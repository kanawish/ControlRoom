import logging
import time

import mediapipe as mp
import numpy as np
from livekit import rtc
from livekit.rtc import VideoFrame
from livekit.rtc import VideoFrameEvent
from mediapipe.tasks.python.vision.object_detector import ObjectDetector

from control_room.toolkit import load_config, first_track_queued_frame_looper, loop_on
from control_room.toolkit_object_detect import visualize, perf, default_queued_options

logging.basicConfig(level=logging.INFO)

# ensure LIVEKIT_URL, LIVEKIT_API_KEY, and LIVEKIT_API_SECRET are set
loaded_config = load_config()

local_options = default_queued_options('../control_room/efficientdet_lite0.tflite')
detector = ObjectDetector.create_from_options(local_options)


async def handle_frame_event(frame_event: VideoFrameEvent, output_source: rtc.VideoSource):
    # TODO: ...
    # get frame, extract a numpy frame from it.
    buffer: VideoFrame = frame_event.frame
    numpy_frame = np.frombuffer(buffer.data, dtype=np.uint8)
    numpy_frame = numpy_frame.reshape((buffer.height, buffer.width, 3))
    # from the frame, a media pipe image.
    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=numpy_frame)
    # TODO: Compare perf between our hand-rolled queuing and MP's async.

    start_time = time.perf_counter()
    results = detector.detect(mp_image)
    end_time = time.perf_counter()

    visualize(numpy_frame, results)
    perf(numpy_frame, 0, end_time - start_time)
    frame = rtc.VideoFrame(mp_image.width, mp_image.height, rtc.VideoBufferType.RGB24, numpy_frame.data)
    output_source.capture_frame(frame)


async def main(room: rtc.Room):
    return await first_track_queued_frame_looper(room, "foo", "foobar", handle_frame_event)


if __name__ == "__main__":
    loop_on(main)
