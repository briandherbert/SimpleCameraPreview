package burningaltar.com.camerapreviewcompat;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.util.AttributeSet;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bherbert on 1/22/16.
 * <p/>
 * Notes:
 * Taking a picture is the same as getting a preview image, just with the largest size instead of trying to match view size
 *
 * Can't add too many surfaces to the capturesession, otherwise, it fails. So we can't stream the preview and have the
 * option to capture preview AND photos, even if they're not happening simultaneously
 *
 * Also, there's a distinction between the rotation required for the on-screen preview and the raw images generated
 * <p/>
 */
@TargetApi(21)
class Camera2PreviewTexture extends BaseCameraPreviewTexture {
    public static final String TAG = Camera2PreviewTexture.class.getSimpleName();

    CameraManager mCameraManager;
    String mFrontCameraId;
    String mRearCameraId;
    String mCurrentCameraId;

    CameraDevice mCamera;

    Handler mHandler = new Handler();

    CameraCaptureSession mCaptureSession;
    CaptureRequest.Builder mPreviewRequestBuilder;

    // For capturing preview frames
    ImageReader mImageReader;

    Size mPreviewSize;
    Size mPhotoSize;

    Surface mSurface;

    private int mDegreesToRotatePhoto = 0;
    private int mDegreesToRotatePreview = 0;

    static Size[] sSupportedSizes = null;
    Size mLastAttemptedSize = null;

    public Camera2PreviewTexture(Context context) {
        super(context);
        init();
    }

    public Camera2PreviewTexture(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Camera2PreviewTexture(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    void init() {
        log("init");
        setSurfaceTextureListener(this);

        mCameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);

        String[] camIds = null;

        try {
            camIds = mCameraManager.getCameraIdList();

            for (String id : camIds) {
                if (mCameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    mFrontCameraId = id;
                } else {
                    mRearCameraId = id;
                }

                if (mFrontCameraId != null && mRearCameraId != null) break;
            }
        } catch (CameraAccessException e) {
            loge("Camera access exception ", e);
        }

        if (camIds == null) return;
    }

    @Override
    public PreviewInfo setCameraImpl(boolean frontFacing) {
        log("Set camera " + (frontFacing ? "front" : "rear"));
        setCamera(frontFacing ? mFrontCameraId : mRearCameraId);
        return mPreviewInfo;
    }

    private boolean setCamera(String cameraId) {
        log("Set camera to id " + cameraId);

        cleanup();

        mCurrentCameraId = cameraId;

        try {
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                initCamera();
                mCameraManager.openCamera(mCurrentCameraId, mCameraStateCallback, mHandler);
                return true;
            }
        } catch (CameraAccessException e) {
            loge("Exception opening camera ", e);
        }

        return false;
    }

    public void initCamera() throws CameraAccessException {
        CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCurrentCameraId);

        StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        mDegreesToRotatePhoto = CameraUtils.getRotationDegrees((Activity) getContext(), characteristics, mFrontCameraId == mCurrentCameraId);
        mDegreesToRotatePreview = CameraUtils.getCamera2PreviewRotation((Activity) getContext());

        boolean isPhotoSideways = mDegreesToRotatePhoto == 90 || mDegreesToRotatePhoto == 270;
        boolean isPreviewSideways = mDegreesToRotatePreview == 90 || mDegreesToRotatePreview == 270;

        if (isPreviewSideways) {
            setRotation(mDegreesToRotatePreview);
        }

        log("Degrees to rotate photo " + mDegreesToRotatePhoto + " , preview " + mDegreesToRotatePreview);

        log("finding camera2 sizes using configs.getOutputSizes ");

        if (sSupportedSizes == null) {
            sSupportedSizes = configs.getOutputSizes(SurfaceHolder.class);
        }

        mPreviewSize = CameraUtils.getBiggestSize(sSupportedSizes, getWidth(), getHeight(), isPhotoSideways);

        int width = mPreviewSize.getWidth();
        int height = mPreviewSize.getHeight();

        mPhotoSize = CameraUtils.getBiggestSize(configs.getOutputSizes(SurfaceHolder.class));

        mPreviewInfo = new PreviewInfo(width, height, true);
    }

    @Override
    public void getPhoto(SimpleCameraPreview.PhotoBitmapListener photoListener) {
        mPhotoBitmapListener = photoListener;
        captureImage(false);
    }

    @Override
    public void getNextPreviewFrame(SimpleCameraPreview.PreviewBitmapListener previewBitmapListener) {
        log("Get next preview frame");
        mPreviewBitmapListener = previewBitmapListener;
        captureImage(true);
    }

    private void captureImage(boolean forPreview) {
        if (mCaptureSession == null) return;

        try {
            final CaptureRequest.Builder captureBuilder = mCamera.createCaptureRequest(forPreview ? CameraDevice.TEMPLATE_PREVIEW : CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, mDegreesToRotatePhoto);
            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            loge("Unable to get preview ", e);
        }
    }

    public boolean supportsScaling() {
        return true;
    }

    CameraDevice.StateCallback mCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            log("onOpened");

            mCamera = camera;

            log("Preview size " + mPreviewSize.getWidth() + ", " + mPreviewSize.getHeight() + " rotated size " + mPreviewInfo.rotatedWidth + ", " + mPreviewInfo.rotatedHeight + " rotate " + mDegreesToRotatePreview);

            // We configure the size of default buffer to be the size of camera preview we want.
            SurfaceTexture st = getSurfaceTexture();
            st.setDefaultBufferSize(mPreviewInfo.w, mPreviewInfo.h);

            // This is the output Surface we need to start preview.
            mSurface = new Surface(st);

            try {
                log("createCaptureSession");
                mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 1);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mHandler);

                List<Surface> surfaces = new ArrayList<>();
                surfaces.add(mSurface);
                surfaces.add(mImageReader.getSurface());

                mCamera.createCaptureSession(surfaces, mCaptureStateCallback, mHandler);

            } catch (CameraAccessException e) {
                loge("error creating capture session ", e);
            }

        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            loge("onDisconnected", null);
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            loge("onError " + error, null);
        }
    };

    void cleanup() {
        log("cleanup");

        try {
            if (mCamera != null) {
                if (mCaptureSession != null) {
                    mCaptureSession.stopRepeating();
                    mCaptureSession = null;
                }

                mCamera.close();
                mCamera = null;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            // Fail silently
        }
    }

    CameraCaptureSession.StateCallback mCaptureStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            log("onConfigured");

            if (null == mCamera) {
                return;
            }

            // When the session is ready, we start displaying the preview.
            mCaptureSession = session;
            try {
                // Auto focus should be continuous for camera preview.
                mPreviewRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                // Finally, we start displaying the camera preview.
                mPreviewRequestBuilder.addTarget(mSurface);

                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            log("onConfigureFailed with size " + mPreviewSize + ", trying another");
            int sizeIdx = -1;

            for (int i = 0; i < sSupportedSizes.length; i++) {
                if (sSupportedSizes[i].equals(mPreviewSize)) {
                    sizeIdx = i;
                    break;
                }
            }

            if (sizeIdx > -1) {
                Size[] newSizes = new Size[sSupportedSizes.length - 1];

                int j = 0;
                for (int i = 0; i < sSupportedSizes.length; i++) {
                    if (i == sizeIdx) {
                        continue;
                    }

                    newSizes[j++] = sSupportedSizes[i];
                }

                sSupportedSizes = newSizes;
                setCamera(mIsFrontFacing);
            }
        }
    };

    CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            log("capture started");
        }
    };

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            log("Image available");
            if (mPreviewBitmapListener != null || mPhotoBitmapListener != null) {
                Image image = reader.acquireLatestImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();

                buffer.rewind();
                byte[] b = new byte[buffer.remaining()];
                buffer.get(b);

                // Rotation was already set captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, mDegreesToRotatePhoto);
                if (mPreviewBitmapListener != null) {
                    // A preview was requested
                    mPreviewBitmapListener.onPreview(b, 0);
                    mPreviewBitmapListener = null;
                } else {
                    // A photo was requested
                    mPhotoBitmapListener.onPhoto(b, 0);
                    mPhotoBitmapListener = null;
                }

                image.close();
            }

            try {
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mHandler);
            } catch (CameraAccessException e) {
                loge("Unable to restart repeating request", e);
            }
        }
    };

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        cleanup();
        return super.onSurfaceTextureDestroyed(surface);
    }

    @Override
    public String getTag() {
        return TAG;
    }
}
