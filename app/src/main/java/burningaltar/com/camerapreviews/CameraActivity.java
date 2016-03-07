package burningaltar.com.camerapreviews;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import burningaltar.com.camerapreviewcompat.CameraPreviewViewCompat;
import burningaltar.com.camerapreviewcompat.CameraUtils;

public class CameraActivity extends Activity implements CameraPreviewViewCompat.PreviewBitmapListener, CameraPreviewViewCompat.PhotoBitmapListener {
    public static final String TAG = "KameraActivity";

    public static final String KEY_FRONT_FACING = "KEY_FRONT_FACING";
    public static final String KEY_API_LEVEL = "KEY_API_LEVEL";

    static final int REQ_CODE_CAMERA = 1;

    private boolean mIsCameraSideways = false;

    private int mDegreesToRotatePhoto = 0;

    private FrameLayout mFramePreview;

    boolean isCamera2 = false;

    ImageView mImgPreviewSnapshot;

    Button mBtnCamVersion;

    CameraPreviewViewCompat mCameraView;

    boolean mIsFrontFacing = true;
    CameraPreviewViewCompat.CameraApiLevel mApiLevel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!hasCamera(this)) {
            Toast.makeText(this, "No camera found!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (savedInstanceState != null) {
            mIsFrontFacing = savedInstanceState.getBoolean(KEY_FRONT_FACING);
            mApiLevel = (CameraPreviewViewCompat.CameraApiLevel) savedInstanceState.getSerializable(KEY_API_LEVEL);

            Log.v(TAG, "Restored state, front facing? " + mIsFrontFacing + " api " + mApiLevel);
        }

        setContentView(R.layout.camera_preview_main);

        mCameraView = (CameraPreviewViewCompat) findViewById(R.id.camera_preview);
        mImgPreviewSnapshot = (ImageView) findViewById(R.id.img_preview_snapshot);

        mApiLevel = mCameraView.getCameraAPILevel();

        findViewById(R.id.btn_switch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsFrontFacing = mCameraView.switchCameras();
                Log.v(TAG, "Switched camera to " + (mIsFrontFacing ? "front" : "back"));
            }
        });

        findViewById(R.id.btn_snapshot).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraView.getNextPreviewFrame(CameraActivity.this);
            }
        });

        findViewById(R.id.btn_photo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraView.getPhoto(CameraActivity.this);
            }
        });

        mBtnCamVersion = (Button) findViewById(R.id.lbl_cam_ver);
        mBtnCamVersion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CameraPreviewViewCompat.CameraApiLevel apiLevel =
                        CameraPreviewViewCompat.CameraApiLevel.one.equals(mApiLevel) ?
                                CameraPreviewViewCompat.CameraApiLevel.two :
                                CameraPreviewViewCompat.CameraApiLevel.one;

                mCameraView.setCameraApiLevel(apiLevel);

                mApiLevel = mCameraView.getCameraAPILevel();

                mBtnCamVersion.setText("v. " + mApiLevel.toString());
            }
        });

        mBtnCamVersion.setText("v. " + mApiLevel.toString());
    }

    protected void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getCameraPermission();
        } else {
            onHasPermission();
        }
    }

    protected void onPause() {
        super.onPause();

    }

    void onHasPermission() {
        mCameraView.showPreview(mIsFrontFacing);
    }

    /**
     * Check if this device has a camera
     */
    private boolean hasCamera(Context context) {
        return (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA));
    }

    @Override
    public void onPreview(byte[] data, int degreesToRotate) {
        Log.v(TAG, "on preview, data size " + data.length + " rotate preview " + degreesToRotate);

        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

        Log.v("blarg", "preview size " + bitmap.getWidth() + ", " + bitmap.getHeight());

        //Bitmap bmp = CameraUtils.decodeSampledBitmap(data, mImgPreviewSnapshot.getWidth(), mImgPreviewSnapshot.getHeight());

        // Log.v(TAG, "resampled bmp size " + bmp.getByteCount());

        // TODO: This fails w OOM on Galaxy S6!
        mImgPreviewSnapshot.setRotation(degreesToRotate);
        mImgPreviewSnapshot.setImageBitmap(bitmap);
    }

    @Override
    public void onPhoto(byte[] data, int degreesToRotate) {
        Log.v(TAG, "on photo, data size " + data.length + " rotate img " + degreesToRotate);

        Bitmap bmp = CameraUtils.decodeSampledBitmap(data, mImgPreviewSnapshot.getWidth(), mImgPreviewSnapshot.getHeight());

        Log.v(TAG, "resampled bmp size " + bmp.getByteCount());
        mImgPreviewSnapshot.setRotation(degreesToRotate);
        mImgPreviewSnapshot.setImageBitmap(bmp);
    }

    @TargetApi(23)
    public void getCameraPermission() {
        boolean hasPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;

        Log.v(TAG, "Get permission, has it? " + hasPermission);

        // Here, thisActivity is the current activity
        if (!hasPermission) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {

                Toast.makeText(this, "Not showing reason", Toast.LENGTH_LONG).show();

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        REQ_CODE_CAMERA);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            onHasPermission();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQ_CODE_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    onHasPermission();

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_FRONT_FACING, mIsFrontFacing);
        outState.putSerializable(KEY_API_LEVEL, mApiLevel);
    }
}
