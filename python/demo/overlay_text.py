import asyncio
import logging
import os
from signal import SIGINT, SIGTERM

from livekit import api, rtc

from control_room.toolkit_face import draw_face_mask_to_video_loop
from control_room.toolkit import load_config, log_room_activity

logging.basicConfig(level=logging.INFO)

# ensure LIVEKIT_URL, LIVEKIT_API_KEY, and LIVEKIT_API_SECRET are set
loaded_config = load_config()

WIDTH, HEIGHT = 1024, 768

tasks = set()


async def main(room: rtc.Room) -> None:
    video_stream = None
    log_room_activity(room)

    # publish a track
    output_source = rtc.VideoSource(WIDTH, HEIGHT)
    track = rtc.LocalVideoTrack.create_video_track("masked", output_source)
    track_options = rtc.TrackPublishOptions()
    track_options.source = rtc.TrackSource.SOURCE_CAMERA

    @room.on("track_subscribed")
    def on_track_subscribed(track: rtc.Track, *_):
        logging.info("'main' subscribed to track: " + track.name)
        if track.kind == rtc.TrackKind.KIND_VIDEO:
            nonlocal video_stream
            if video_stream is not None:
                # only process the first stream received
                return

            print(f"and, track: {track.name} is video.")
            video_stream = rtc.VideoStream(track, format=rtc.VideoBufferType.RGB24)

            task = asyncio.create_task(draw_face_mask_to_video_loop(video_stream, output_source, False))
            tasks.add(task)
            task.add_done_callback(tasks.remove)

    token = (
        api.AccessToken()
        .with_identity("python-bot")
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

    publication = await room.local_participant.publish_track(track, track_options)
    logging.info("published track %s", publication.sid)


if __name__ == "__main__":
    # logging.basicConfig(
    #     level=logging.INFO,
    #     handlers=[logging.FileHandler("face_landmark.log"), logging.StreamHandler()],
    # )
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
