import asyncio
import logging

from livekit import rtc
from livekit.rtc import VideoFrameEvent

from control_room.toolkit import load_config, loop_on, first_track_queued_frame_looper
from control_room.toolkit_draw import draw_red_dot, PerfDecorator, draw_on_frame

logging.basicConfig(level=logging.INFO)

# ensure LIVEKIT_URL, LIVEKIT_API_KEY, and LIVEKIT_API_SECRET are set
loaded_config = load_config()

WIDTH, HEIGHT = 1024, 768

tasks = set()

# TODO: Not functional.
if __name__ == "__main__":
    async def main(room: rtc.Room):

        perf_decorator = PerfDecorator(draw_red_dot)
        async def handle_frame_event(frame_event: VideoFrameEvent, output_source: rtc.VideoSource):
            draw_on_frame(frame_event, output_source, perf_decorator)
            # draw_on_frame(frame_event, output_source, draw_red_dot)

        return await first_track_queued_frame_looper(
            room=room,
            lk_id="overlay_text",
            lk_name="Overlay Text",
            handle_frame=handle_frame_event)

    loop_on(main)
