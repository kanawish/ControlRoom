import asyncio
import json
import logging
import os
import time
from asyncio import Queue
from aiohttp import ClientSession
from signal import SIGINT, SIGTERM
from typing import Callable, Awaitable, Optional

import cv2
import numpy as np
from livekit import api, rtc
from livekit.rtc import VideoFrameEvent, VideoFrame, AudioFrameEvent, AudioFrame, AudioStream
from livekit.agents.stt import SpeechEventType, SpeechEvent, SpeechStream
from livekit.plugins import deepgram

def build_recoder(filename, fps=20.0, resolution=(640, 480)) -> cv2.VideoWriter:
    fourcc = cv2.VideoWriter.fourcc(*"MP4V")
    return cv2.VideoWriter(f"{filename}.mp4v", fourcc, fps, resolution)


class Config:
    def __init__(
            self,
            target_room_name,
            livekit_url,
            livekit_api_key,
            livekit_api_secret,
            eleven_api_key,
            deepgram_api_key,
            openai_api_key,
            output_width,
            output_height,
    ):
        self.TARGET_ROOM_NAME = target_room_name
        self.LIVEKIT_URL = livekit_url
        self.LIVEKIT_API_KEY = livekit_api_key
        self.LIVEKIT_API_SECRET = livekit_api_secret
        self.ELEVEN_API_KEY = eleven_api_key
        self.DEEPGRAM_API_KEY = deepgram_api_key
        self.OPENAI_API_KEY = openai_api_key
        self.OUTPUT_WIDTH = output_width
        self.OUTPUT_HEIGHT = output_height


def load_config(config_json="config.json"):
    with open(config_json, "r") as file:
        data = json.load(file)
        config = Config(**data)
        os.environ["LIVEKIT_API_KEY"] = config.LIVEKIT_API_KEY
        os.environ["LIVEKIT_API_SECRET"] = config.LIVEKIT_API_SECRET
        os.environ["LIVEKIT_URL"] = config.LIVEKIT_URL
        os.environ["TARGET_ROOM_NAME"] = config.TARGET_ROOM_NAME
        os.environ["OUTPUT_WIDTH"] = config.OUTPUT_WIDTH
        os.environ["OUTPUT_HEIGHT"] = config.OUTPUT_HEIGHT
        os.environ["OPENAI_API_KEY"] = config.OPENAI_API_KEY
        os.environ["DEEPGRAM_API_KEY"] = config.DEEPGRAM_API_KEY
        os.environ["ELEVEN_API_KEY"] = config.ELEVEN_API_KEY

        return config


async def create_room(config: Config):
    lk_api = api.LiveKitAPI(
        config.LIVEKIT_URL, config.LIVEKIT_API_KEY, config.LIVEKIT_API_SECRET
    )
    room_info = await lk_api.room.create_room(
        api.CreateRoomRequest(name=config.TARGET_ROOM_NAME),
    )
    print(room_info)
    results = await lk_api.room.list_rooms(api.ListRoomsRequest())
    print(results)
    await lk_api.aclose()


def log_room_activity(room: rtc.Room):
    @room.on("participant_connected")
    def on_participant_connected(participant: rtc.RemoteParticipant):
        logging.info(
            "participant connected: %s %s", participant.sid, participant.identity
        )

    @room.on("participant_disconnected")
    def on_participant_disconnected(participant: rtc.RemoteParticipant):
        logging.info(
            "participant disconnected: %s %s", participant.sid, participant.identity
        )

    # track_subscribed is emitted whenever the local participant is subscribed to a new track
    @room.on("track_subscribed")
    def on_track_subscribed(
            track: rtc.Track,
            publication: rtc.RemoteTrackPublication,
            participant: rtc.RemoteParticipant,
    ):
        logging.info("track subscribed: %s pub: %s", track.sid, publication.sid)

    @room.on("track_unsubscribed")
    def on_track_unsubscribed(
            track: rtc.Track,
            publication: rtc.RemoteTrackPublication,
            participant: rtc.RemoteParticipant,
    ):
        logging.info("track unsubscribed: %s pub: %s", track.sid, publication.sid)


async def producer(
        queue: asyncio.Queue[Optional[VideoFrameEvent]], input_stream: rtc.VideoStream
):
    """
    Asynchronously produce frames from the input_stream
    and put them into the queue.
    """
    logging.info("frame_event producer start")
    total = 0
    dropped = 0
    frame_event: VideoFrameEvent
    # TODO: Review this code... works ok, but something 🐟 re: task_done() / join() usage.
    async for frame_event in input_stream:
        total += 1
        if not queue.full():
            await queue.put(frame_event)
        else:
            dropped += 1
            if dropped % 100 == 0:
                print(f"{dropped / total} frames drop %")

    logging.info("frame_event producer end")
    await queue.put(None)


async def consumer(
        queue: asyncio.Queue[Optional[VideoFrameEvent]],
        output_source: rtc.VideoSource,
        handle_frame: Callable[[VideoFrameEvent, rtc.VideoSource], Awaitable[None]],
):
    """
    Asynchronously consume frames from the queue
    """
    logging.info("frame_event consumer start")
    count = 0
    try:
        while True:
            frame = await queue.get()
            if frame is None:
                break
            start_time = time.perf_counter()
            await handle_frame(frame, output_source)

            end_time = time.perf_counter()
            count += 1
            if count % 60 == 0:
                print(f"frame handled in: {end_time - start_time} seconds")

            queue.task_done()
        logging.info("frame_event consumer end")
    except asyncio.CancelledError:
        logging.info("frame_event consumer Task cancelled")


async def audio_to_text_looper(
        room: rtc.Room,
        lk_id: str,
        lk_name: str,
        handle_audio: Callable[[AudioFrameEvent], Awaitable[None]]
):

    log_room_activity(room)
    stt = deepgram.STT(http_session=ClientSession())
    stt_stream = stt.stream()

    async def listen(stt_stream: SpeechStream):
        async for event in stt_stream:
            if event.type == SpeechEventType.FINAL_TRANSCRIPT:
                text = event.alternatives[0].text
                await handle_audio(text)
                logging.info(f"🔊 {text}")
                # Do something with text
            elif event.type == SpeechEventType.INTERIM_TRANSCRIPT:
                pass
            elif event.type == SpeechEventType.START_OF_SPEECH:
                pass
            elif event.type == SpeechEventType.END_OF_SPEECH:
                pass


    async def audio_to_text(track: rtc.Track):
        audio_stream = rtc.AudioStream(track)

        asyncio.ensure_future(listen(stt_stream))

        async for ev in audio_stream:
            stt_stream.push_frame(ev.frame)

        stt_stream.end_input()


    @room.on("track_subscribed")
    def on_track_subscribed(subscribed_track: rtc.Track, *_):
        logging.info(f"🛤 on_track_subscribed('{subscribed_track.sid}')")
        if subscribed_track.kind == rtc.TrackKind.KIND_AUDIO:

            #asyncio.create_task(audio_to_text(subscribed_track))

            asyncio.ensure_future(audio_to_text(subscribed_track))


    @room.on("track_unsubscribed")
    def on_track_unsubscribed(unsubscribed_track: rtc.Track, *_):
        logging.info(f"🛤 on_track_unsubscribed({unsubscribed_track.name})")

    token = (
        api.AccessToken()
        .with_identity(lk_id)
        .with_name(lk_name)
        .with_grants(
            api.VideoGrants(
                room_join=True,
                room=os.getenv("TARGET_ROOM_NAME"),
            )
        )
    )
    await room.connect(os.getenv("LIVEKIT_URL"), token.to_jwt())
    logging.info(f"connected to room: {room.name}")


async def first_track_queued_frame_looper(
        room: rtc.Room,
        lk_id: str,
        lk_name: str,
        handle_frame: Callable[[VideoFrameEvent, rtc.VideoSource], Awaitable[None]],
):
    """
    A main loop that listens for first subscribed KIND_VIDEO track,
    then sets up a producer-consumer frame queue. Frames are dropped when
    queue is waiting

    @param room: the livekit room object, has a looper associated to it.
    @param lk_id: livekit user id
    @param lk_name: livekit username
    @param handle_frame: the callback that will be responsible for inference and
    @return:
    """
    width: int = int(os.getenv("OUTPUT_WIDTH")) or 1024
    height: int = int(os.getenv("OUTPUT_HEIGHT")) or 768
    logging.info(f"called foo_main({room.name}) [{width}x{height}]")
    input_video_stream = None

    log_room_activity(room)

    # prepare a track
    output_source = rtc.VideoSource(width, height)
    output_track = rtc.LocalVideoTrack.create_video_track("masked", output_source)
    output_track_options = rtc.TrackPublishOptions()
    output_track_options.source = rtc.TrackSource.SOURCE_CAMERA

    # Used for our first rough 'tasks management' approach taken from LK samples.
    tasks = set()

    @room.on("track_subscribed")
    def on_track_subscribed(subscribed_track: rtc.Track, *_):
        logging.info(f"🛤 on_track_subscribed('{subscribed_track.sid}')")
        if subscribed_track.kind == rtc.TrackKind.KIND_VIDEO:
            nonlocal input_video_stream
            if input_video_stream is not None:
                # only process the first stream received
                return

            logging.info(
                f"🥇🛤️ {subscribed_track.sid} is the first received video track."
            )
            input_video_stream = rtc.VideoStream(
                subscribed_track, format=rtc.VideoBufferType.RGB24
            )
            queue: Queue[Optional[VideoFrameEvent]] = asyncio.Queue(maxsize=1)

            # Approach 1, the 'cancel reaction'
            producer_task = asyncio.create_task(producer(queue, input_video_stream))
            tasks.add(producer_task)
            consumer_task = asyncio.create_task(
                consumer(queue, output_source, handle_frame)
            )
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
        .with_identity(lk_id)
        .with_name(lk_name)
        .with_grants(
            api.VideoGrants(
                room_join=True,
                room=os.getenv("TARGET_ROOM_NAME"),
            )
        )
    )
    await room.connect(os.getenv("LIVEKIT_URL"), token.to_jwt())
    logging.info(f"connected to room: {room.name}")

    publication = await room.local_participant.publish_track(
        output_track, output_track_options
    )
    logging.info(f"published track {publication.sid}")


def loop_on(main: Callable[[rtc.Room], Awaitable[None]]):
    """
    Sets up the main loop, preps a rtc.Room with said loop,
    and runs it.

    Args:
        main (Callable[[rtc.Room], Awaitable[None]]): The main function to be called with the room.

    """
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
