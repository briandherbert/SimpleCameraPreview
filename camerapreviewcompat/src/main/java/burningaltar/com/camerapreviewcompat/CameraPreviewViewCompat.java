package burningaltar.com.camerapreviewcompat;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Debug;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * TODO: document your custom view class.
 */
public class CameraPreviewViewCompat extends RelativeLayout {
    public static final String TAG = CameraPreviewViewCompat.class.getSimpleName();

    public enum CameraApiLevel {
        one,
        two
    }

    public interface CameraPreviewStatusListener {
        // The preview will resize while honoring its original bounds as max limits, so the
        // width or height may end up smaller than expected
        public void onResizedPreview(int origWidth, int origHeight, int newWidth, int newHeight);

        public void onCameraFailed(String message);

        public void onCameraReady();
    }

    public interface PreviewBitmapListener {
        public void onPreview(byte[] previewData, int degreesToRotate);
    }

    public interface PhotoBitmapListener {
        public void onPhoto(byte[] photoData, int degreesToRotate);
    }

    private CameraApiLevel mCameraApiLevel = CameraApiLevel.one;
    private boolean mIsFrontFacing = false;

    private BaseCameraPreviewTexture mPreviewTexture;

    private boolean mSupportsCamera2 = false;

    public CameraPreviewViewCompat(Context context) {
        super(context);
        init(null, 0);
    }

    public CameraPreviewViewCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public CameraPreviewViewCompat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        log("init");

        setGravity(Gravity.CENTER);

        mSupportsCamera2 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

        // Load attributes
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.CameraPreviewViewCompat, defStyle, 0);

        int apiVersion = a.getInt(R.styleable.CameraPreviewViewCompat_cameraApiLevel, -1);
        mIsFrontFacing = a.getBoolean(R.styleable.CameraPreviewViewCompat_frontFacing, mIsFrontFacing);

        log("Attrs api version " + apiVersion);

        if (apiVersion >= 0) {
            mCameraApiLevel = CameraApiLevel.values()[apiVersion];
        } else {
            mCameraApiLevel = mSupportsCamera2 ? CameraApiLevel.two : CameraApiLevel.one;
        }

        log("Using camera API " + mCameraApiLevel + " front facing? " + mIsFrontFacing);

        if (isInEditMode()) {
            TextView tv = new TextView(getContext());
            tv.setText("CameraPreviewViewCompat\nAPI: " + mCameraApiLevel + "\nFront facing: " + mIsFrontFacing);
            addView(tv);
            return;
        }

        showPreview();
    }

    public void showPreview() {
        showPreview(mIsFrontFacing);
    }

    public void showPreview(boolean frontFacing) {
        log("Show preview, front? " + frontFacing);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!ensurePermissions()) {
                log("Camera permissions not yet granted!");
                return;
            }
        }

        mIsFrontFacing = frontFacing;

        mPreviewTexture = CameraApiLevel.one.equals(mCameraApiLevel) ? new CameraPreviewTexture(getContext()) :
                new Camera2PreviewTexture(getContext());

        mPreviewTexture.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        removeAllViews();
        addView(mPreviewTexture);

        mPreviewTexture.setCamera(mIsFrontFacing);
    }

    public boolean getIsFrontFacing() {
        return mIsFrontFacing;
    }

    @TargetApi(23)
    private boolean ensurePermissions() {
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * This will have no effect below API 21
     *
     * @param apiLevel
     */
    public void setCameraApiLevel(CameraApiLevel apiLevel) {
        if (!mSupportsCamera2) return;

        mCameraApiLevel = apiLevel;

        showPreview();
    }

    public CameraApiLevel getCameraApiLevel() {
        return mCameraApiLevel;
    }

    public void getPhoto(PhotoBitmapListener photoListener) {
        if (mPreviewTexture != null) {
            mPreviewTexture.getPhoto(photoListener);
        } else {
            log("Tried to get photo while camera isn't ready!");
        }
    }

    public void getNextPreviewFrame(PreviewBitmapListener previewBitmapListener) {
        if (mPreviewTexture != null) {
            mPreviewTexture.getNextPreviewFrame(previewBitmapListener);
        } else {
            log("Tried to get preview while camera isn't ready!");
        }
    }

    public final void setListener(CameraPreviewStatusListener listener) {
        if (mPreviewTexture == null) return;
        mPreviewTexture.setListener(listener);
    }

    /**
     * Returns isFrontFacing
     */
    public boolean switchCameras() {
        if (mPreviewTexture == null) return false;
        return mPreviewTexture.switchCameras();
    }

    public CameraApiLevel getCameraAPILevel() {
        return mCameraApiLevel;
    }

    public void log(String msg) {
        Log.v(TAG, msg);
    }
}
