import os

import cv2
import mediapipe as mp
import numpy as np
from livekit import rtc
from livekit.rtc import VideoFrame
from mediapipe.framework.formats import landmark_pb2
from mediapipe.python import solutions

model_file = "face_landmarker.task"
model_path = os.path.dirname(os.path.realpath(__file__)) + "/" + model_file
BaseOptions = mp.tasks.BaseOptions
FaceLandmarker = mp.tasks.vision.FaceLandmarker
FaceLandmarkerOptions = mp.tasks.vision.FaceLandmarkerOptions
VisionRunningMode = mp.tasks.vision.RunningMode
options = FaceLandmarkerOptions(
    base_options=BaseOptions(model_asset_path=model_path),
    running_mode=VisionRunningMode.VIDEO,
)


def draw_landmarks_on_image(rgb_image, detection_result):
    # from https://github.com/googlesamples/mediapipe/blob/main/examples/face_landmarker/python/%5BMediaPipe_Python_Tasks%5D_Face_Landmarker.ipynb
    face_landmarks_list = detection_result.face_landmarks

    # Loop through the detected faces to visualize.
    for face_landmarks in face_landmarks_list:
        # Draw the face landmarks.
        face_landmarks_proto = landmark_pb2.NormalizedLandmarkList()
        face_landmarks_proto.landmark.extend(
            [
                landmark_pb2.NormalizedLandmark(
                    x=landmark.x, y=landmark.y, z=landmark.z
                )
                for landmark in face_landmarks
            ]
        )

        solutions.drawing_utils.draw_landmarks(
            image=rgb_image,
            landmark_list=face_landmarks_proto,
            connections=mp.solutions.face_mesh.FACEMESH_TESSELATION,
            landmark_drawing_spec=None,
            connection_drawing_spec=mp.solutions.drawing_styles.get_default_face_mesh_tesselation_style(),
        )
        solutions.drawing_utils.draw_landmarks(
            image=rgb_image,
            landmark_list=face_landmarks_proto,
            connections=mp.solutions.face_mesh.FACEMESH_CONTOURS,
            landmark_drawing_spec=None,
            connection_drawing_spec=mp.solutions.drawing_styles.get_default_face_mesh_contours_style(),
        )
        solutions.drawing_utils.draw_landmarks(
            image=rgb_image,
            landmark_list=face_landmarks_proto,
            connections=mp.solutions.face_mesh.FACEMESH_IRISES,
            landmark_drawing_spec=None,
            connection_drawing_spec=mp.solutions.drawing_styles.get_default_face_mesh_iris_connections_style(),
        )


async def draw_face_mask_to_video_loop(
        input_stream: rtc.VideoStream,
        output_source: rtc.VideoSource,
        show_window=True
):
    landmarker = FaceLandmarker.create_from_options(options)

    # cv2 commands are only for _local_ window/preview
    if show_window:
        cv2.namedWindow("livekit_video", cv2.WINDOW_NORMAL)
        cv2.startWindowThread()

    async for frame_event in input_stream:
        buffer: VideoFrame = frame_event.frame

        arr = np.frombuffer(buffer.data, dtype=np.uint8)
        arr = arr.reshape((buffer.height, buffer.width, 3))

        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=arr)
        # TODO: Fix timestamp-dupe issue, see obj detect for example.
        detection_result = landmarker.detect_for_video(
            mp_image, frame_event.timestamp_us
        )

        draw_landmarks_on_image(arr, detection_result)
        # out_arr[0:buffer.width, 0:buffer.height, :] = arr
        frame = rtc.VideoFrame(buffer.width, buffer.height, rtc.VideoBufferType.RGB24, buffer.data)
        # frame = rtc.VideoFrame(WIDTH, HEIGHT, rtc.VideoBufferType.RGB24, argb_frame)
        output_source.capture_frame(frame)

        if show_window:
            arr = cv2.cvtColor(arr, cv2.COLOR_RGB2BGR)
            cv2.imshow("livekit_video", arr)
            if cv2.waitKey(1) & 0xFF == ord("q"):
                break

    landmarker.close()
    if show_window:
        cv2.destroyAllWindows()
