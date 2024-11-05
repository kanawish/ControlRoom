import asyncio
import colorsys
import cv2
import numpy as np
from livekit import rtc
from livekit.rtc import VideoFrame
from livekit.rtc import VideoFrameEvent
import time

from control_room.toolkit_object_detect import draw_perf_bkg


async def draw_color_cycle(output_source: rtc.VideoSource, width, height):
    argb_frame = bytearray(width * height * 4)
    arr = np.frombuffer(argb_frame, dtype=np.uint8)

    framerate = 1 / 30
    hue = 0.0

    while True:
        start_time = asyncio.get_event_loop().time()

        rgb = colorsys.hsv_to_rgb(hue, 1.0, 1.0)
        rgb = [(x * 255) for x in rgb]  # type: ignore

        argb_color = np.array(rgb + [255], dtype=np.uint8)
        arr.flat[::4] = argb_color[0]
        arr.flat[1::4] = argb_color[1]
        arr.flat[2::4] = argb_color[2]
        arr.flat[3::4] = argb_color[3]

        frame = rtc.VideoFrame(width, height, rtc.VideoBufferType.RGBA, argb_frame)
        output_source.capture_frame(frame)
        hue = (hue + framerate / 3) % 1.0

        code_duration = asyncio.get_event_loop().time() - start_time
        await asyncio.sleep(1 / 30 - code_duration)


def draw_img_rect_info(win_name: str, bgr_image):
    rect = cv2.getWindowImageRect(win_name)

    # Define the text and its properties
    text = f"Image Rectangle: x={rect[0]}, y={rect[1]}, width={rect[2]}, height={rect[3]}"
    org = (50, 50)  # Bottom-left corner of the text (x, y)
    font_face = cv2.FONT_HERSHEY_SIMPLEX
    font_scale = 1
    color = (255, 0, 0)  # Blue color in BGR
    thickness = 2
    line_type = cv2.LINE_AA
    # Draw the text on the image
    cv2.putText(bgr_image, text, org, font_face, font_scale, color, thickness, line_type)

def draw_foo(
        frame_event: VideoFrameEvent,
        output_source: rtc.VideoSource,
):
    buffer: VideoFrame = frame_event.frame
    np_frame = np.frombuffer(buffer.data, dtype=np.uint8)
    np_frame = np_frame.reshape((buffer.height, buffer.width, 3))

    start_time = time.perf_counter()

    # TODO: something...

    end_time = time.perf_counter()
    from control_room.toolkit_object_detect import draw_perf
    draw_perf_bkg(np_frame, 0, end_time - start_time)
    frame = rtc.VideoFrame(
        # 640, 480,
        buffer.width, buffer.height,
        rtc.VideoBufferType.RGB24,
        np_frame.data
    )
    output_source.capture_frame(frame)
