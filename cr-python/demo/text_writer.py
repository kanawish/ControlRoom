import argparse
import asyncio
from signal import SIGINT, SIGTERM
import logging
import os

# Import numpy and OpenCV
import numpy as np
import cv2

from control_room.toolkit import *
# Removed draw_color_cycle import
# from control_room.toolkit_draw import draw_color_cycle

logging.basicConfig(level=logging.INFO)

# Just an example
parser = argparse.ArgumentParser()
parser.add_argument("--param", help="Describe what param stands for")
args = parser.parse_args()
logging.info(f'Param: {args.param}')

WIDTH, HEIGHT = 1280, 720

# Define the function to draw text message
async def draw_text_message(source: rtc.VideoSource, width, height, message):
    font = cv2.FONT_HERSHEY_SIMPLEX
    font_scale = 3  # Adjust as needed
    color = (255, 255, 255)  # White color
    thickness = 5  # Adjust as needed
    while True:
        # Create a black image
        frame = np.zeros((height, width, 3), dtype=np.uint8)
        # Get size of the text
        text_size, _ = cv2.getTextSize(message, font, font_scale, thickness)
        text_width, text_height = text_size
        # Set text position
        text_x = int((width - text_width) / 2)
        text_y = int((height + text_height) / 2)
        # Put text on image
        cv2.putText(frame, message, (text_x, text_y), font, font_scale, color, thickness, cv2.LINE_AA)
        # Create VideoFrame
        video_frame = rtc.VideoFrame.from_ndarray(frame)
        # Send the frame
        source.capture_frame(video_frame)

        await asyncio.sleep(1/30)  # 30 fps

async def main(target_room: rtc.Room):
    logging.info("called main")
    token = (
        # will automatically use the LIVEKIT_API_KEY and LIVEKIT_API_SECRET env vars
        api.AccessToken()
        .with_identity("python-bot")
        .with_name("Python Bot")
        .with_grants(
            api.VideoGrants(
                room_join=True,
                room=os.getenv("TARGET_ROOM_NAME"),
            )
        )
        .to_jwt() # ?
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
    track = rtc.LocalVideoTrack.create_video_track("text_message", source)
    options = rtc.TrackPublishOptions()
    options.source = rtc.TrackSource.SOURCE_CAMERA
    publication = await target_room.local_participant.publish_track(track, options)
    logging.info("published track %s", publication.sid)

    # Start the text message drawing coroutine
    asyncio.ensure_future(draw_text_message(source, WIDTH, HEIGHT, "Your Text Message"))

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