from typing import Callable

import cv2
import numpy as np


MARGIN = 10  # pixel
ROW_SIZE = 16  # pixels
FONT_SIZE = 1
FONT_THICKNESS = 1
TEXT_COLOR = (255, 0, 0)  # red
F = 2


def draw_perf(image: np.ndarray, fps: float, latency: float) -> np.ndarray:
    height, width = image.shape[:2]

    # height, width = image.shape[:2]
    fps_loc = (int(MARGIN + (width / 2)), MARGIN + ROW_SIZE * F)

    cv2.putText(image, f"FPS: {fps}", fps_loc, cv2.FONT_HERSHEY_PLAIN, FONT_SIZE * F, (0, 255, 0), FONT_THICKNESS * F)

    latency_loc = (int(MARGIN + (width / 2)), MARGIN + ROW_SIZE * F * 3)
    cv2.putText(image, f"LATENCY: {latency:.4f}", latency_loc, cv2.FONT_HERSHEY_PLAIN, FONT_SIZE * F, (0, 255, 0),
                FONT_THICKNESS * F)

    wh_loc = (int(MARGIN + (width / 2)), MARGIN + ROW_SIZE * F * 2)
    cv2.putText(image, f"[{width},{height}]", wh_loc, cv2.FONT_HERSHEY_PLAIN, FONT_SIZE * F, (0, 255, 0),
                FONT_THICKNESS * F)

    return image


def draw_perf_bkg(image: np.ndarray, fps: float, latency: float) -> np.ndarray:
    height, width = image.shape[:2]

    # Define text locations
    top = height - pos_y(4)
    fps_loc = (pos_x(width), top + pos_y(1))
    latency_loc = (pos_x(width), top + pos_y(3))
    wh_loc = (pos_x(width), top + pos_y(2))

    # Define text strings
    fps_text = f"FPS: {fps:.2f}"
    latency_text = f"LATENCY: {latency:.4f}"
    wh_text = f"[{width},{height}]"

    # Function to draw text with background
    def draw_text_with_bg(bkg_image, text, loc):
        (text_w, text_h), _ = cv2.getTextSize(text, cv2.FONT_HERSHEY_PLAIN, FONT_SIZE * F, FONT_THICKNESS * F)
        bg_rect_start = (loc[0] - 5, loc[1] - text_h - 5)
        bg_rect_end = (loc[0] + text_w + 5, loc[1] + 5)
        overlay = bkg_image.copy()
        cv2.rectangle(overlay, bg_rect_start, bg_rect_end, (0, 0, 0), -1)
        cv2.addWeighted(overlay, 0.5, bkg_image, 0.5, 0, bkg_image)
        cv2.putText(bkg_image, text, loc, cv2.FONT_HERSHEY_PLAIN, FONT_SIZE * F, (0, 255, 0), FONT_THICKNESS * F)

    # Draw texts with background
    draw_text_with_bg(image, fps_text, fps_loc)
    draw_text_with_bg(image, latency_text, latency_loc)
    draw_text_with_bg(image, wh_text, wh_loc)

    return image


def pos_x(width):
    return int(MARGIN)


def pos_y(line_num=4):
    return MARGIN + ROW_SIZE * F * line_num


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
