import asyncio
import logging
import os
from asyncio import Queue
from signal import SIGINT, SIGTERM
from typing import Callable, Awaitable, Optional

from livekit import rtc, api
from livekit.rtc import VideoFrameEvent

from control_room.toolkit import load_config, log_room_activity

logging.basicConfig(level=logging.INFO)

# ensure LIVEKIT_URL, LIVEKIT_API_KEY, and LIVEKIT_API_SECRET are set
loaded_config = load_config()

# Used for our first rough 'tasks management' approach taken from LK samples.
# TODO: Find a cleaner approach, not fond of top level properties like this.
tasks = set()


async def producer(
        queue: asyncio.Queue[Optional[VideoFrameEvent]],
        input_stream: rtc.VideoStream
):
    """
    Asynchronously produce frames from the input_stream
    and put them into the queue.
    """
    logging.info("frame_event producer start")
    frame_event: VideoFrameEvent
    async for frame_event in input_stream:
        while queue.full():
            print("Queue is full, dropping frame!")
            await asyncio.sleep(0)  # yield control to the event loop
        await queue.put(frame_event)
    logging.info("frame_event producer end")
    await queue.put(None)


async def consumer(
        queue: asyncio.Queue[Optional[VideoFrameEvent]],
        handle_frame: Callable[[VideoFrameEvent], Awaitable[None]]
):
    """
    Asynchronously consume frames from the queue
    """
    logging.info("frame_event consumer start")
    try:
        while True:
            frame = await queue.get()
            if frame is None:
                break
            await handle_frame(frame)
            queue.task_done()
        logging.info("frame_event consumer end")
    except asyncio.CancelledError:
        logging.info("frame_event consumer Task cancelled")


async def main(
        room_arg: rtc.Room,
        width: int = int(os.getenv("OUTPUT_WIDTH")) or 1024,
        height: int = int(os.getenv("OUTPUT_HEIGHT")) or 768
):
    logging.info(f"called main({room_arg.name},{width}, {height})")

    input_video_stream = None

    log_room_activity(room)

    # prepare a track
    output_source = rtc.VideoSource(width, height)
    output_track = rtc.LocalVideoTrack.create_video_track("masked", output_source)
    output_track_options = rtc.TrackPublishOptions()
    output_track_options.source = rtc.TrackSource.SOURCE_CAMERA

    async def handle_frame_event(frame_event: VideoFrameEvent):
        # NOTE: Passthrough example.
        output_source.capture_frame(frame_event.frame)
        # TODO: Your handling here!

    # We wait on first user to subscribe their track into the call.
    @room.on("track_subscribed")
    def on_track_subscribed(subscribed_track: rtc.Track, *_):
        logging.info(f"🛤 on_track_subscribed('{subscribed_track.sid}')")
        if subscribed_track.kind == rtc.TrackKind.KIND_VIDEO:
            nonlocal input_video_stream
            if input_video_stream is not None:
                # only process the first stream received
                return

            logging.info(f"🥇🛤️ {subscribed_track.sid} is the first received video track.")
            input_video_stream = rtc.VideoStream(subscribed_track, format=rtc.VideoBufferType.RGB24)
            queue: Queue[Optional[VideoFrameEvent]] = asyncio.Queue(maxsize=1)

            # Approach 1, the 'cancel reaction'
            producer_task = asyncio.create_task(producer(queue, input_video_stream))
            tasks.add(producer_task)
            consumer_task = asyncio.create_task(consumer(queue, handle_frame_event))
            tasks.add(consumer_task)

            def done_handler(t):
                tasks.remove(t)
                consumer_task.cancel("producer is done, consumer cancelled.")
            producer_task.add_done_callback(done_handler)
            consumer_task.add_done_callback(tasks.remove)

            # TODO Approach 2, the emit-none on end of input stream?

        @room.on("track_unsubscribed")
        def on_track_unsubscribed(unsubscribed_track: rtc.Track, *_):
            logging.info(f"🛤 on_track_unsubscribed({unsubscribed_track.name})")

    token = (
        api.AccessToken()
        .with_identity("python-bot-queue-tmpl")
        .with_name("Python Bot")
        .with_grants(
            api.VideoGrants(
                room_join=True,
                room=os.getenv("TARGET_ROOM_NAME"),
            )
        )
    )
    await room.connect(os.getenv("LIVEKIT_URL"), token.to_jwt())
    logging.info("connected to room: " + room.name)

    publication = await room.local_participant.publish_track(output_track, output_track_options)
    logging.info("published track %s", publication.sid)


if __name__ == "__main__":

    loop = asyncio.get_event_loop()
    room = rtc.Room(loop=loop)


    async def cleanup():
        await room.disconnect()
        loop.stop()


    asyncio.ensure_future(main(room))
    for signal in [SIGINT, SIGTERM]:
        loop.add_signal_handler(signal, lambda: asyncio.ensure_future(cleanup()))

    try:
        loop.run_forever()
    finally:
        loop.close()

