import logging

from livekit import rtc
from livekit.rtc import VideoFrameEvent

from loadimg import load_img

import numpy as np
from PIL import Image

import spaces

import torch
from torchvision import transforms

from transformers import AutoModelForImageSegmentation

from control_room.toolkit import load_config, loop_on, first_track_queued_frame_looper
from control_room.toolkit_draw import draw_block_perf, draw_red_dot

logging.basicConfig(level=logging.INFO)

# ensure LIVEKIT_URL, LIVEKIT_API_KEY, and LIVEKIT_API_SECRET are set
loaded_config = load_config()

WIDTH, HEIGHT = 1024, 768

torch.set_float32_matmul_precision(["high", "highest"][0])

# Replace CUDA-specific code with device-agnostic code
device = "cuda" if torch.cuda.is_available() else "cpu"
print("DEVICE: ", device)
birefnet = AutoModelForImageSegmentation.from_pretrained(
    "ZhengPeng7/BiRefNet", trust_remote_code=True
)
birefnet.to(device)

transform_image = transforms.Compose(
    [
        transforms.Resize((1024, 1024)),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
    ]
)


@spaces.GPU
def remove_background(image: Image.Image) -> Image.Image:
    # Store original image dimensions for later resizing
    image_size = image.size

    # Step 1: Prepare the image for the model
    input_images = transform_image(image).unsqueeze(0).to(device)
    # transform_image applies:
    # - Resize to 1024x1024
    # - Convert to tensor
    # - Normalize with ImageNet means and stds
    # unsqueeze adds batch dimension
    # to(device) moves to GPU if available

    # Step 2: Run the model
    with torch.no_grad():  # Disable gradient calculation for inference
        preds = birefnet(input_images)[-1].sigmoid().cpu()
    pred = preds[0].squeeze()  # Remove batch dimension
    
    # Step 3: Convert prediction to PIL Image
    pred_pil = transforms.ToPILImage()(pred)
    
    # Step 4: Resize mask back to original image size
    mask = pred_pil.resize(image_size)

    # Step 5: Apply mask as alpha channel

    # Create a new black image with the same mode and size as the original image
    black_background = Image.new("RGB", image_size, (0, 0, 0))

    # Paste the original image onto the black background, using the mask as the transparency mask
    black_background.paste(image.convert("RGB"), mask=mask)

    return black_background  # Returns the resulting image with the background blacked out
    # image.putalpha(mask)  # This makes background transparent
    #
    # return image #
    # return mask.convert("RGB")  # Returns the mask as a PIL Image


def frame_processor(input_frame: np.ndarray) -> np.ndarray:
    # Debug input shape and type
    input_shape = input_frame.shape
    input_dtype = input_frame.dtype
    print(f"Input frame: shape={input_shape}, dtype={input_dtype}")

    # Convert numpy array to PIL Image
    pil_image: Image.Image = Image.fromarray(input_frame)
    
    # Get mask from background removal
    mask: Image.Image = remove_background(pil_image)

    # Convert back to numpy array for output
    output_frame: np.ndarray = np.array(mask, dtype=np.uint8)

    # Debug input shape and type
    output_shape = output_frame.shape
    output_dtype = output_frame.dtype
    print(f"Output frame: shape={output_shape}, dtype={output_dtype}")
    
    # Debug: Show output array in separate window
    # import cv2cv2
    # cv2.imshow("5. Output NumPy Array", cv2.cvtColor(output_frame, cv2.COLOR_RGB2BGR))
    # cv2.waitKey(1)  # Refresh window, wait 1ms
    # print("5")
    
    return output_frame


async def handle_frame_event(frame_event: VideoFrameEvent, output_source: rtc.VideoSource):
    # TODO: check this works..
    draw_block_perf(frame_event, output_source, frame_processor)


if __name__ == "__main__":
    async def main(room: rtc.Room):
        return await first_track_queued_frame_looper(
            room=room,
            lk_id="bkg_remover",
            lk_name="Background Remover",
            handle_frame=handle_frame_event)


    loop_on(main)
