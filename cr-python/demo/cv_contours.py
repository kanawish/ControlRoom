import logging

import cv2
import numpy as np
from livekit import rtc
from livekit.rtc import VideoFrame, VideoFrameEvent

from control_room.toolkit import load_config, loop_on, first_track_queued_frame_looper


# Function to detect shape
def detect_shape(contour):
    shape = "unidentified"
    perimeter = cv2.arcLength(contour, True)
    approximation = cv2.approxPolyDP(contour, 0.04 * perimeter, True)

    if len(approximation) == 3:
        shape = "triangle"
    elif len(approximation) == 4:
        (x, y, w, h) = cv2.boundingRect(approximation)
        aspect_ratio = w / float(h)
        shape = "square" if 0.95 <= aspect_ratio <= 1.05 else "rectangle"
    elif len(approximation) == 5:
        shape = "pentagon"
    elif len(approximation) == 6:
        shape = "hexagon"
    else:
        shape = "circle"
    return shape


async def handle_frame_event(frame_event: VideoFrameEvent, output_source: rtc.VideoSource):
    buffer: VideoFrame = frame_event.frame
    arr = np.frombuffer(buffer.data, dtype=np.uint8)
    arr = arr.reshape((buffer.height, buffer.width, 3))

    src_image = cv2.cvtColor(arr, cv2.COLOR_RGB2BGR)

    gray = cv2.cvtColor(src_image, cv2.COLOR_BGR2GRAY)
    # cv2.imshow(windows[0], gray)
    blurred = cv2.GaussianBlur(gray, (7, 7), 0)
    # blurred = cv2.GaussianBlur(gray, (65, 65), 0) # increased kernel
    # cv2.imshow(windows[1], blurred)
    _, thresh = cv2.threshold(blurred, 120, 255, cv2.THRESH_BINARY_INV)
    # cv2.imshow(windows[2], thresh)

    contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    dest_image = src_image

    for contour in contours:
        cv2.drawContours(dest_image, [contour], -1, (0, 255, 0), 2)
        # cv2.putText(dest_image, shape, (x, y - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 0), 2)

    frame = rtc.VideoFrame(
        buffer.width,
        buffer.height,
        rtc.VideoBufferType.RGB24,
        # TODO: Demo the different filters.
        # cv2.cvtColor(gray, cv2.COLOR_BGR2RGB).data
        # cv2.cvtColor(blurred, cv2.COLOR_BGR2RGB).data
        # cv2.cvtColor(thresh, cv2.COLOR_BGR2RGB).data
        cv2.cvtColor(dest_image, cv2.COLOR_BGR2RGB).data
    )
    output_source.capture_frame(frame)


# TODO: Get rid of top level preview stuff.
logging.basicConfig(level=logging.INFO)
loaded_config = load_config()

if __name__ == "__main__":
    async def main(room: rtc.Room):
        return await first_track_queued_frame_looper(
            room=room,
            lk_id="cv_contours",
            lk_name="Open CV Contour",
            handle_frame=handle_frame_event)

    loop_on(main)
