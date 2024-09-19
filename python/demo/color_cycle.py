import argparse
import asyncio
from signal import SIGINT, SIGTERM

from control_room.toolkit import *
from control_room.toolkit_draw import draw_color_cycle

logging.basicConfig(level=logging.INFO)

# Just an example
parser = argparse.ArgumentParser()
parser.add_argument("--param", help="Describe what param stands for")
args = parser.parse_args()
logging.info(f'Param: {args.param}')

WIDTH, HEIGHT = 1280, 720


# asyncio.get_event_loop().run_until_complete(main(load_config()))

# asyncio.run(create_room(loaded_config))

async def join_room(config: Config, token):
    room = rtc.Room()
    log_room_activity(room)

    async def receive_frames(stream: rtc.VideoStream):
        async for frame in stream:
            # received a video frame from the track, process it here
            pass

    # track_subscribed is emitted whenever the local participant is subscribed to a new track
    @room.on("track_subscribed")
    def on_track_subscribed(track: rtc.Track, publication: rtc.RemoteTrackPublication,
                            participant: rtc.RemoteParticipant):
        logging.info("receive / track subscribed: %s", publication.sid)
        if track.kind == rtc.TrackKind.KIND_VIDEO:
            video_stream = rtc.VideoStream(track)
            asyncio.ensure_future(receive_frames(video_stream))

    # By default, autosubscribe is enabled. The participant will be subscribed to
    # all published tracks in the room
    await room.connect(config.LIVEKIT_URL, token)
    logging.info("connected to room %s", room.name)

    global waiting
    while waiting:
        await asyncio.sleep(1)


async def main(target_room: rtc.Room):
    logging.info("called main")
    room_name = os.getenv("TARGET_ROOM_NAME")
    token = (
        # will automatically use the LIVEKIT_API_KEY and LIVEKIT_API_SECRET env vars
        api.AccessToken()
        .with_identity("python-bot")
        .with_name("Python Bot")
        .with_grants(
            api.VideoGrants(
                room_join=True,
                room=room_name,
            )
        )
        .to_jwt()
    )

    url = os.getenv("LIVEKIT_URL")
    log_room_activity(target_room)

    logging.info("connecting to %s", url)
    try:
        await target_room.connect(url, token)
        logging.info("connected to room %s", target_room.name)
    except rtc.ConnectError as e:
        logging.error("failed to connect to the room: %s", e)
        return

    # publish a track
    source = rtc.VideoSource(WIDTH, HEIGHT)
    track = rtc.LocalVideoTrack.create_video_track("hue", source)
    options = rtc.TrackPublishOptions()
    options.source = rtc.TrackSource.SOURCE_CAMERA
    publication = await target_room.local_participant.publish_track(track, options)
    logging.info("published track %s", publication.sid)

    asyncio.ensure_future(draw_color_cycle(source, WIDTH, HEIGHT))


if __name__ == "__main__":
    # async def main():
    # logging.basicConfig(
    #     level=logging.INFO,
    #     handlers=[logging.FileHandler("publish_hue.log"), logging.StreamHandler()],
    # )

    # Load config from file, assign to environ in order to expose to lk token functions.
    loaded_config = load_config()

    loop = asyncio.get_event_loop()
    room = rtc.Room(loop=loop)


    async def cleanup():
        await room.disconnect()
        loop.stop()


    # asyncio.run(join_room(loaded_config, new_token))
    asyncio.ensure_future(main(room))

    for signal in [SIGINT, SIGTERM]:
        loop.add_signal_handler(signal, lambda: asyncio.ensure_future(cleanup()))

    try:
        loop.run_forever()
    finally:
        loop.close()
