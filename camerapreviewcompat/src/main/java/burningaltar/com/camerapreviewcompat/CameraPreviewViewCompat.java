package burningaltar.com.camerapreviewcompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
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

    private CameraApiLevel mCameraApiLevel = null;
    private boolean mIsFrontFacing = false;

    private BaseCameraPreviewTexture mPreviewTexture;
    private boolean mUserSetApiLevel = false;

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
        // Load attributes
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.CameraPreviewViewCompat, defStyle, 0);

        String apiVersion = a.getString(R.styleable.CameraPreviewViewCompat_cameraApiLevel);
        Log.v(TAG, "Attrs api version " + apiVersion);
        if (apiVersion != null) {
            mCameraApiLevel = CameraApiLevel.valueOf(apiVersion);
            mUserSetApiLevel = true;
        } else {
            mCameraApiLevel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? CameraApiLevel.two : CameraApiLevel.one;
        }

        Log.v(TAG, "Using camera API " + mCameraApiLevel);

        setGravity(Gravity.CENTER);

        mPreviewTexture = CameraApiLevel.one.equals(mCameraApiLevel) ? new CameraPreviewTexture(getContext()) :
                new Camera2PreviewTexture(getContext());

        mPreviewTexture.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(mPreviewTexture);
    }

    public void setCamera(boolean frontFacing) {
        if (mPreviewTexture == null) return;
        mIsFrontFacing = frontFacing;
        mPreviewTexture.setCamera(mIsFrontFacing);
    }

    public void getPhoto(PhotoBitmapListener photoListener) {
        if (mPreviewTexture != null) {
            mPreviewTexture.getPhoto(photoListener);
        } else {
            Log.w(TAG, "Tried to get photo while camera isn't ready!");
        }
    }

    public void getNextPreviewFrame(PreviewBitmapListener previewBitmapListener) {
        if (mPreviewTexture != null) {
            mPreviewTexture.getNextPreviewFrame(previewBitmapListener);
        } else {
            Log.w(TAG, "Tried to get preview while camera isn't ready!");
        }
    }

    public final void setListener(CameraPreviewStatusListener listener) {
        if (mPreviewTexture == null) return;
        mPreviewTexture.setListener(listener);
    }

    public void switchCameras() {
        if (mPreviewTexture == null) return;
        mPreviewTexture.switchCameras();
    }

    public CameraApiLevel getCameraAPILevel() {
        return mCameraApiLevel;
    }

}
