import cv2
import numpy as np


def draw_img_rect_info(win_name: str, bgr_image: np.ndarray) -> None:
    """Draw window rectangle information on the provided image.

    This function gets the window rectangle information for the given window name
    and draws text showing the x, y coordinates and dimensions on the image.

    Args:
        win_name (str): Name of the window to get rectangle info from
        bgr_image (np.ndarray): BGR format image to draw text on (modified in-place)

    Returns:
        None: The image is modified in-place
    """
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
