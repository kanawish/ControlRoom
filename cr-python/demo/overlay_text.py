import logging

from livekit import rtc
from livekit.rtc import VideoFrame
from livekit.rtc import VideoFrameEvent

from control_room.toolkit import load_config, loop_on, first_track_queued_frame_looper
from control_room.toolkit_draw import draw_foo

logging.basicConfig(level=logging.INFO)

# ensure LIVEKIT_URL, LIVEKIT_API_KEY, and LIVEKIT_API_SECRET are set
loaded_config = load_config()

WIDTH, HEIGHT = 1024, 768

tasks = set()

# TODO: import the changes made for the waterloo devfest that are under ks-models.
async def handle_frame_event(frame_event: VideoFrameEvent, output_source: rtc.VideoSource):
    draw_foo(frame_event, output_source)

if __name__ == "__main__":
    async def main(room: rtc.Room):
        return await first_track_queued_frame_looper(
            room=room,
            lk_id="foo",
            lk_name="foobar",
            handle_frame=handle_frame_event)

    loop_on(main)
