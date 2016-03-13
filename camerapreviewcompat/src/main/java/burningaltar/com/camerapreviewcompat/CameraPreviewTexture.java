package burningaltar.com.camerapreviewcompat;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.AttributeSet;

import java.io.IOException;

/**
 * A camera preview view that bakes in all the camera setup and resizing, and allows for easily taking photos
 * or getting preview frames. When resized, at least one of the original dimensions (width or height) will be kept, and the other will be padded with margins
 * <p/>
 * You MUST put this view in either a LinearLayout or RelativeLayout or the preview will skew to the size of the container
 */
@TargetApi(14)
@SuppressWarnings("deprecation")
public class CameraPreviewTexture extends BaseCameraPreviewTexture {
    private static final String TAG = CameraPreviewTexture.class.getSimpleName();

    private Camera mCamera;
    private Camera.CameraInfo mInfo = new Camera.CameraInfo();

    int mCurrentCamIdx = 0;
    int mFrontFacingIdx = -1;
    int mRearFacingIdx = -1;

    boolean mIsCameraReady = false;

    public CameraPreviewTexture(Context context) {
        super(context);
        init();
    }

    public CameraPreviewTexture(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CameraPreviewTexture(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    void init() {
        log("init");
        setSurfaceTextureListener(this);

        int numCameras = Math.min(2, Camera.getNumberOfCameras());

        // Loop through cams til we get a front and back
        for (int i = 0; i < numCameras && (mFrontFacingIdx == -1 || mRearFacingIdx == -1); i++) {
            Camera.getCameraInfo(i, mInfo);
            if (mInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mFrontFacingIdx = i;
            } else {
                mRearFacingIdx = i;
            }

            if (mFrontFacingIdx >= 0 && mRearFacingIdx >= 0) break;
        }

        log("Front camera idx is " + mFrontFacingIdx + " rear: " + mRearFacingIdx + " num cams " + numCameras);
    }

    @Override
    public PreviewInfo setCameraImpl(boolean frontFacing) {
        log("Set camera " + (frontFacing ? "front" : "rear"));
        return setCamera(frontFacing ? mFrontFacingIdx : mRearFacingIdx);
    }

    private PreviewInfo setCamera(int cameraIdx) {
        log("Set camera to idx: " + cameraIdx);

        cleanup();

        mCurrentCamIdx = cameraIdx;

        SurfaceTexture st = getSurfaceTexture();

        // setCamera() can be called before the holder is set, in which case the camera will use the given index when the surface is created
        if (st != null && initCamera()) {
            try {
                st.setDefaultBufferSize(mPreviewInfo.rotatedWidth, mPreviewInfo.rotatedHeight);

                mCamera.setPreviewTexture(st);
                mCamera.setOneShotPreviewCallback(mPreviewCallback);
                mCamera.startPreview();
            } catch (IOException e) {
                loge("Error setting camera preview: ", e);
            }
        }

        return mPreviewInfo;
    }

    boolean initCamera() {
        log("initCamera idx: " + mCurrentCamIdx);
        if (mCurrentCamIdx < 0) return false;

        cleanup();

        // Get a camera
        mCamera = Camera.open(mCurrentCamIdx);

        if (mCamera == null) {
            loge("Unable to obtain camera!", null);
            return false;
        }

        Camera.getCameraInfo(mCurrentCamIdx, mInfo);

        mDegreesToRotatePreview = CameraUtils.getRotationDegrees((Activity) getContext(), mInfo);
        boolean isSideways = mDegreesToRotatePreview == 90 || mDegreesToRotatePreview == 270;

        int orientationDegrees = mDegreesToRotatePreview;

        // Front camera rotation is mirrored
        if (mFrontFacingIdx == mCurrentCamIdx) {
            orientationDegrees = (mDegreesToRotatePreview + 180) % 360;

            if (!isSideways) mDegreesToRotatePreview = orientationDegrees;
        }

        mCamera.setDisplayOrientation(orientationDegrees);

        log("Degrees to rotate " + mDegreesToRotatePreview);

        // Get and set params
        Camera.Parameters params = CameraUtils.getCameraParams(mCamera.getParameters(), getWidth(), getHeight(), isSideways);

        // We want to fit the entire preview in the view's initial bounds, so we'll account for rotation and scale down
        if (params != null) {
            Camera.Size previewSize = params.getPreviewSize();
            mPreviewInfo = new PreviewInfo(previewSize.width, previewSize.height, isSideways);

            mCamera.setParameters(params);

            Camera.Size prevSize = mCamera.getParameters().getPreviewSize();
            log("preview dimens " + previewSize.width + ", " + previewSize.height + " according to cam " + prevSize.width + ", " + prevSize.height);

        }

        return true;
    }

    /**
     * Subscribe to the preview's image data feed to get a callback with the data as a bitmap
     *
     * @param previewBitmapListener
     */
    @Override
    public void getNextPreviewFrame(CameraPreviewViewCompat.PreviewBitmapListener previewBitmapListener) {
        mPreviewBitmapListener = previewBitmapListener;
        if (mCamera == null) return;

        mCamera.setOneShotPreviewCallback(mPreviewCallback);
    }

    @Override
    public void getPhoto(CameraPreviewViewCompat.PhotoBitmapListener photoListener) {
        mPhotoBitmapListener = photoListener;
        if (mCamera == null) return;

        mCamera.takePicture(null, null, null, mPictureCallback);
    }

    public boolean isCameraReady() {
        return mIsCameraReady;
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        cleanup();
        return super.onSurfaceTextureDestroyed(surface);
    }

    void cleanup() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * This will constantly get the preview image data. If mPreviewBitmapCallback is set, it'll get passed the next preview as a bitmap
     */
    final Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (mCamera == null) return;
            log("first preview frame, buffer size " + data.length);

            // If the camera isn't ready, we're just here as a notif that the camera is ready
            if (!mIsCameraReady) {
                mIsCameraReady = true;
                onCameraReady();

                return;
            }

            if (mPreviewBitmapListener != null) {
                mPreviewBitmapListener.onPreview(CameraUtils.fromPreviewData(data, camera.getParameters().getPreviewSize()), mDegreesToRotatePreview);
                mPreviewBitmapListener = null;
            }
        }
    };

    final Camera.PictureCallback mPictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            if (mPhotoBitmapListener != null && data != null) {
                mPhotoBitmapListener.onPhoto(data, mDegreesToRotatePreview);
                mPhotoBitmapListener = null;
            }

            if (mCamera != null) mCamera.startPreview();
        }
    };

    public boolean supportsScaling() {
        return true;
    }

    @Override
    public String getTag() {
        return TAG;
    }
}
