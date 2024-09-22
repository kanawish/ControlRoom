import argparse
import asyncio
from signal import SIGINT, SIGTERM

from control_room.toolkit import *
from control_room.toolkit_draw import draw_color_cycle

logging.basicConfig(level=logging.INFO)

WIDTH, HEIGHT = 640, 480

async def main(target_room: rtc.Room):
    logging.info("called main")
    room_name = os.getenv("TARGET_ROOM_NAME") # NOTE: must be "local" !
    token = (
        # Automatically uses the LIVEKIT_API_KEY and LIVEKIT_API_SECRET env vars
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
    # Load config from file, assign to environ in order to expose to lk token functions.
    loaded_config = load_config()

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