import logging

from livekit import rtc
from livekit.rtc import VideoFrameEvent

from control_room.toolkit import load_config, loop_on, first_track_queued_frame_looper
from control_room.toolkit_draw import draw_on_frame, draw_red_dot, PerfDecorator, draw_color_cycle

logging.basicConfig(level=logging.INFO)

# ensure LIVEKIT_URL, LIVEKIT_API_KEY, and LIVEKIT_API_SECRET are set
loaded_config = load_config()

WIDTH, HEIGHT = 1024, 768


if __name__ == "__main__":
    async def main(room: rtc.Room):

        async def handle_frame_event(frame_event: VideoFrameEvent, output_source: rtc.VideoSource):
            draw_on_frame(frame_event, output_source, draw_color_cycle)

        return await first_track_queued_frame_looper(
            room=room,
            lk_id="color_cycle",
            lk_name="Color Cycler",
            handle_frame=handle_frame_event)

    loop_on(main)
