package burningaltar.com.camerapreviewcompat;

import android.content.Context;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.os.Build;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.TextureView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;

/**
 * Created by bherbert on 1/23/16.
 */
abstract class BaseCameraPreviewTexture extends TextureView implements TextureView.SurfaceTextureListener {
    private static final String TAG = BaseCameraPreviewTexture.class.getSimpleName();

    static class PreviewInfo {
        int w;
        int h;
        boolean isSideways;

        public int rotatedWidth, rotatedHeight;

        public PreviewInfo(int width, int height, boolean isSideways) {
            w = width;
            h = height;
            this.isSideways = isSideways;

            rotatedWidth = isSideways ? h : w;
            rotatedHeight = isSideways ? w : h;
        }

        @Override
        public String toString() {
            return "PreviewInfo - width: " + w + " height: " + h + " sideways? " + isSideways;
        }
    }

    SimpleCameraPreview.PreviewBitmapListener mPreviewBitmapListener = null;
    SimpleCameraPreview.PhotoBitmapListener mPhotoBitmapListener = null;

    int mDegreesToRotatePreview = 0;

    private SimpleCameraPreview.CameraPreviewStatusListener mListener;

    PreviewInfo mPreviewInfo = null;

    Boolean mIsFrontFacing = null;

    boolean mIsCameraReady = false;

    private boolean mIsSurfaceAvailable = false;

    Point screenSize = new Point();

    public BaseCameraPreviewTexture(Context context) {
        super(context);
    }

    public BaseCameraPreviewTexture(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BaseCameraPreviewTexture(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            display.getRealSize(screenSize);
        } else {
            display.getSize(screenSize);
        }

        log("Texture available, size is " + width + ", " + height + " screen size is " + screenSize.toString() +
        " device model " + Build.MODEL);

        mIsSurfaceAvailable = true;

        setCamera(mIsFrontFacing);
    }

    public final void setCamera(Boolean isFrontFacing) {
        log("Set camera, front? " + isFrontFacing);
        mIsFrontFacing = isFrontFacing;

        if (mIsSurfaceAvailable && null != mIsFrontFacing) {
            PreviewInfo previewInfo = setCameraImpl(mIsFrontFacing);
            if (previewInfo == null) {
                log("Failed to set camera!");
                return;
            }

            resizeToPreview(previewInfo);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // noop
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // noop
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mIsSurfaceAvailable = false;
        return true;
    }

    /**
     * Resize to the preview size, accounting for rotation. Pad out w margins
     *
     * @param previewInfo
     */
    private final void resizeToPreview(PreviewInfo previewInfo) {
        if (previewInfo == null) {
            log("Preview info is null!");
            return;
        }

        log("Resizing view to preview. " + previewInfo.toString());

        boolean isSideways = previewInfo.isSideways;
        int previewWidth = previewInfo.w;
        int previewHeight = previewInfo.h;

        int origWidth = getWidth();
        int origHeight = getHeight();

        // Rotate the camera if necessary, only on initial creation
        if (isSideways) {
            log("Preview is sideways; swapping width and height");

            origWidth = origWidth ^ origHeight;
            origHeight = origWidth ^ origHeight;
            origWidth = origWidth ^ origHeight;
        }

        if (previewWidth == origWidth && previewHeight == origHeight) return;

        double surfaceRatio = origWidth / (double) origHeight;
        double previewRatio = previewWidth / (double) previewHeight;

        if (supportsScaling()) {
            double scaleBy;

            if (surfaceRatio < previewRatio) {
                scaleBy = (origWidth / (double) previewWidth);
            } else {
                scaleBy = (origHeight / (double) previewHeight);
            }

            log("surface ratio " + surfaceRatio + " preview ratio " + previewRatio);

            previewHeight *= scaleBy;
            previewWidth *= scaleBy;
        }

        log("new width " + previewWidth + " height " + previewHeight);

        int heightDiff = origHeight - previewHeight;
        int widthDiff = origWidth - previewWidth;

        int marginHoriz = (isSideways ? heightDiff : widthDiff) / 2;
        int marginVert = (isSideways ? widthDiff : heightDiff) / 2;

        ViewGroup.LayoutParams lp = getLayoutParams();
        lp.width = isSideways ? previewHeight : previewWidth;
        lp.height = isSideways ? previewWidth : previewHeight;

        if (lp instanceof RelativeLayout.LayoutParams) {
            ((RelativeLayout.LayoutParams) lp).setMargins(marginHoriz, marginVert, marginHoriz, marginVert);
        } else {
            log("Preview isn't in a Relative or LinearLayout; we can't scale the image down!");
        }

        log("margin horizontal " + marginHoriz + " vert " + marginVert);

        requestLayout();

        if (mListener != null) {
            mListener.onResizedPreview(origWidth, origHeight, lp.width, lp.height);
        }
    }

    public final void setListener(SimpleCameraPreview.CameraPreviewStatusListener listener) {
        mListener = listener;
    }

    /**
     * Returns isFrontFacing
     */
    public boolean switchCameras() {
        setCamera(null == mIsFrontFacing ? true : !mIsFrontFacing);
        return mIsFrontFacing;
    }

    public abstract PreviewInfo setCameraImpl(boolean frontFacing);

    abstract boolean supportsScaling();

    /**
     * @return whether the new camera is front facing
     */

    public abstract void getPhoto(SimpleCameraPreview.PhotoBitmapListener photoListener);

    public abstract void getNextPreviewFrame(SimpleCameraPreview.PreviewBitmapListener previewBitmapListener);

    public String getTag() {
        return TAG;
    }

    public void log(String msg) {
        Log.v(getTag(), msg);
    }

    public void loge(String msg, @Nullable Exception e) {
        Log.e(getTag(), msg, e);
    }

    protected void onCameraReady() {
        if (mListener != null) mListener.onCameraReady();
    }
}
