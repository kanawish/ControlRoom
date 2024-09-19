import cv2
import cv2 as cv

# video capture
vc = cv.VideoCapture(1)
# video writer
video_filename = 'webcam.mp4'
frame_rate = vc.get(cv2.CAP_PROP_FPS)
frame_size = (int(vc.get(cv2.CAP_PROP_FRAME_WIDTH)), int(vc.get(cv2.CAP_PROP_FRAME_HEIGHT)))
fourcc = cv.VideoWriter.fourcc(*'mp4v')
vw = cv.VideoWriter(
    video_filename,
    fourcc,
    frame_rate,
    frame_size
)

print(f"    {video_filename},; {fourcc},; {frame_rate},; {frame_size} ")
while True:
    _, frame = vc.read()
    vw.write(frame)
    cv.imshow('', frame)
    if cv.waitKey(1) == ord('q'):
        break

vc.release()
vw.release()
cv.destroyAllWindows()