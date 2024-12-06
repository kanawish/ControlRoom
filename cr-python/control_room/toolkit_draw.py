from collections import deque

from control_room.toolkit_object_detect import draw_perf_bkg

from livekit import rtc
from livekit.rtc import VideoFrame
from livekit.rtc import VideoFrameEvent

from numpy import ndarray, dtype, unsignedinteger

from typing import Any
from typing import Callable

import asyncio
import colorsys
import cv2
import numpy as np
import time


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


# Initialize a deque to store timestamps of the last few frames
def draw_red_dot(image: np.ndarray) -> np.ndarray:
    # Define the position and color of the dot
    position = (10, 10)  # Top-left corner
    color = (255, 0, 0)  # Red color in BGR
    radius = 5
    thickness = -1  # Solid circle

    # Draw the red dot on the image
    cv2.circle(image, position, radius, color, thickness)
    return image


def empty_block(np_frame: ndarray) -> ndarray:
    pass


frame_times = deque(maxlen=120)


def draw_block_perf(
        frame_event: VideoFrameEvent,
        output_source: rtc.VideoSource,
        block: Callable[[ndarray], ndarray] = empty_block
):
    buffer: VideoFrame = frame_event.frame
    np_frame: ndarray = np.frombuffer(buffer.data, dtype=np.uint8)
    np_frame = np_frame.reshape((buffer.height, buffer.width, 3))

    start_time = time.perf_counter()

    # Call block
    new_frame = block(np_frame)

    end_time = time.perf_counter()

    # Calculate FPS
    frame_times.append(end_time)
    if len(frame_times) > 1:
        fps = len(frame_times) / (frame_times[-1] - frame_times[0])
    else:
        fps = 0.0

    draw_perf_bkg(new_frame, fps, end_time - start_time)
    frame = rtc.VideoFrame(
        buffer.width, buffer.height,
        rtc.VideoBufferType.RGB24,
        new_frame.data
    )
    output_source.capture_frame(frame)

