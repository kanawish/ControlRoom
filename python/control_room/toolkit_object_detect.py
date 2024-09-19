from typing import Callable

import cv2
import mediapipe as mp
import numpy as np
from mediapipe import Image
from mediapipe.tasks import python
from mediapipe.tasks.python.components.containers import detections
from mediapipe.tasks.python.vision.object_detector import ObjectDetectorResult

BaseOptions = mp.tasks.BaseOptions
DetectionResult = mp.tasks.components.containers.DetectionResult
ObjectDetector = mp.tasks.vision.ObjectDetector
ObjectDetectorOptions = mp.tasks.vision.ObjectDetectorOptions
VisionRunningMode = mp.tasks.vision.RunningMode


def print_result(result: DetectionResult, output_image: mp.Image, timestamp_ms: int):
    print('detection result: {}'.format(result))


def default_queued_options(model_path: str) -> ObjectDetectorOptions:
    return ObjectDetectorOptions(
        base_options=BaseOptions(model_asset_path=model_path),
        running_mode=VisionRunningMode.IMAGE,
        max_results=5)


def default_live_stream_options(
        model_path: str,
        result_callback: Callable[
            [ObjectDetectorResult, Image, int], None] = print_result) -> ObjectDetectorOptions:
    return ObjectDetectorOptions(
        base_options=BaseOptions(model_asset_path=model_path),
        running_mode=VisionRunningMode.LIVE_STREAM,
        max_results=5,
        result_callback=result_callback)


MARGIN = 10  # pixel
ROW_SIZE = 11  # pixels
FONT_SIZE = 1
FONT_THICKNESS = 1
TEXT_COLOR = (255, 0, 0)  # red
F = 2


def perf(image: np.ndarray, fps: float, latency: float) -> np.ndarray:
    height, width = image.shape[:2]

    # height, width = image.shape[:2]
    fps_loc = (int(MARGIN + (width / 2)), MARGIN + ROW_SIZE * F)

    cv2.putText(image, f"FPS: {fps}", fps_loc, cv2.FONT_HERSHEY_PLAIN, FONT_SIZE * F, (0, 255, 0), FONT_THICKNESS * F)

    latency_loc = (int(MARGIN + (width / 2)), MARGIN + ROW_SIZE * F * 3)
    cv2.putText(image, f"LATENCY: {latency}", latency_loc, cv2.FONT_HERSHEY_PLAIN, FONT_SIZE * F, (0, 255, 0),
                FONT_THICKNESS * F)

    wh_loc = (int(MARGIN + (width / 2)), MARGIN + ROW_SIZE * F * 2)
    cv2.putText(image, f"[{width},{height}]", wh_loc, cv2.FONT_HERSHEY_PLAIN, FONT_SIZE * F, (0, 255, 0),
                FONT_THICKNESS * F)

    return image


def visualize(
        image,
        detection_result
) -> np.ndarray:
    """Draws bounding boxes on the input image and return it.
    Args:
      image: The input RGB image.
      detection_result: The list of all "Detection" entities to be visualized.
    Returns:
      Image with bounding boxes.
    """
    for detection in detection_result.detections:
        # Draw bounding_box
        bbox = detection.bounding_box
        start_point = bbox.origin_x, bbox.origin_y
        end_point = bbox.origin_x + bbox.width, bbox.origin_y + bbox.height
        cv2.rectangle(image, start_point, end_point, TEXT_COLOR, 3)
        # Draw label and score
        category = detection.categories[0]
        category_name = category.category_name
        probability = round(category.score, 2)
        result_text = category_name + ' (' + str(probability) + ')'
        text_location = (MARGIN + bbox.origin_x,
                         MARGIN + ROW_SIZE + bbox.origin_y)
        cv2.putText(image, result_text, text_location, cv2.FONT_HERSHEY_PLAIN,
                    FONT_SIZE, TEXT_COLOR, FONT_THICKNESS)

    return image
