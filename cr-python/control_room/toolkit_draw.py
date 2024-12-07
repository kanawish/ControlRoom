from collections import deque

from control_room.toolkit_object_detect import draw_perf_bkg

from livekit import rtc
from livekit.rtc import VideoFrame
from livekit.rtc import VideoFrameEvent

from numpy import ndarray

from typing import Callable

import colorsys
import cv2
import numpy as np
import time


def draw_red_dot(image: np.ndarray) -> np.ndarray:
    """Draw a solid red dot in the top-left corner of the image.
    
    Args:
        image (np.ndarray): Input image to draw on (BGR format)
        
    Returns:
        np.ndarray: Image with red dot drawn on it
        
    The dot is drawn at position (10,10) with radius 5 pixels as a solid red circle.
    The input image is modified in-place but also returned.
    """
    position = (10, 10)  # Top-left corner
    color = (255, 0, 0)  # Red color in BGR
    radius = 5
    thickness = -1  # Solid circle

    cv2.circle(image, position, radius, color, thickness)
    return image


def draw_color_cycle(np_frame: ndarray) -> ndarray:
    import math
    import time
    
    # Calculate hue using sine wave oscillating between 0 and 1
    # Time-based animation creates smooth color cycling
    # Divide time by 5 to slow down the cycle by 5x
    hue = (math.sin(time.time() / 5) + 1) / 2

    # Convert HSV to RGB
    rgb = colorsys.hsv_to_rgb(hue, 1.0, 1.0)
    rgb = [int(x * 255) for x in rgb]  # Convert to 0-255 range

    # Fill entire frame with the RGB color
    # np_frame[:, :] = rgb

    # Instead, overlay RGB color with alpha blending
    alpha = 0.5  # 50% transparency
    overlay = np.full_like(np_frame, rgb)
    cv2.addWeighted(overlay, alpha, np_frame, 1 - alpha, 0, np_frame)

    return np_frame


def empty_block(np_frame: ndarray) -> ndarray:
    pass


class PerfDecorator:
    def __init__(self, block: Callable[[ndarray], ndarray]):
        """Initialize decorator with a rendering block and its own frame times queue.
        
        Args:
            block: Function that takes and returns a numpy array representing an image.
        """
        self.block = block
        self.frame_times = deque(maxlen=120)
        
    def __call__(self, np_frame: ndarray) -> ndarray:
        start_time = time.perf_counter()
        # Call block
        new_frame = self.block(np_frame)
        end_time = time.perf_counter()
        # Calculate FPS
        self.frame_times.append(end_time)
        if len(self.frame_times) > 1:
            fps = len(self.frame_times) / (self.frame_times[-1] - self.frame_times[0])
        else:
            fps = 0.0
        return draw_perf_bkg(new_frame, fps, end_time - start_time)


def draw_on_frame(
        frame_event: VideoFrameEvent,
        output_source: rtc.VideoSource,
        block: Callable[[ndarray], ndarray] = empty_block
):
    """Takes a VideoFrameEvent containing a frame, applies the provided rendering block function,
    and outputs the result.

    Args:
        frame_event: VideoFrameEvent containing the input frame
        output_source: VideoSource to output the processed frame to
        block: Function that takes and returns a numpy array representing an image.
              Defaults to empty_block which makes no changes.
    """
    buffer: VideoFrame = frame_event.frame
    np_frame: ndarray = np.frombuffer(buffer.data, dtype=np.uint8)
    np_frame = np_frame.reshape((buffer.height, buffer.width, 3))

    # Call block
    new_frame = block(np_frame)

    frame = rtc.VideoFrame(
        buffer.width, buffer.height,
        rtc.VideoBufferType.RGB24,
        new_frame.data
    )
    output_source.capture_frame(frame)

