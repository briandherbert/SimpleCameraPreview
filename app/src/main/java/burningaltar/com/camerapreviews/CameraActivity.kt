package burningaltar.com.camerapreviews

import android.Manifest
import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Date

import burningaltar.com.camerapreviewcompat.*
import burningaltar.com.camerapreviewcompat.BuildConfig

class CameraActivity : Activity(), SimpleCameraPreview.PreviewBitmapListener, SimpleCameraPreview.PhotoBitmapListener {

    private val mIsCameraSideways = false

    private val mDegreesToRotatePhoto = 0

    private val mFramePreview: FrameLayout? = null

    internal var isCamera2 = false

    internal lateinit var mImgPreviewSnapshot: ImageView

    internal lateinit var mBtnCamVersion: Button

    internal lateinit var mCameraView: SimpleCameraPreview

    internal var mIsFrontFacing = false
    internal lateinit var mApiLevel: SimpleCameraPreview.CameraApiLevel

    // Load the full preview/photo sizes into memory?
    private val mDecodeResampled = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.v(TAG, "oncreate, internal? " + BuildConfig.FLAVOR)

        if (!hasCamera(this)) {
            Toast.makeText(this, "No camera found!", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (savedInstanceState != null) {
            mIsFrontFacing = savedInstanceState.getBoolean(KEY_FRONT_FACING)
            mApiLevel = savedInstanceState.getSerializable(KEY_API_LEVEL) as SimpleCameraPreview.CameraApiLevel

            Log.v(TAG, "Restored state, front facing? $mIsFrontFacing api $mApiLevel")
        }

        setContentView(R.layout.camera_preview_main)

        mCameraView = findViewById<View>(R.id.camera_preview) as SimpleCameraPreview
        mImgPreviewSnapshot = findViewById<View>(R.id.img_preview_snapshot) as ImageView

        mApiLevel = mCameraView.cameraAPILevel

        findViewById<View>(R.id.btn_switch).setOnClickListener {
            mIsFrontFacing = mCameraView.switchCameras()
            Log.v(TAG, "Switched camera to " + if (mIsFrontFacing) "front" else "back")
        }

        findViewById<View>(R.id.btn_snapshot).setOnClickListener { mCameraView.getNextPreviewFrame(this@CameraActivity) }

        findViewById<View>(R.id.btn_photo).setOnClickListener { mCameraView.getPhoto(this@CameraActivity) }

        mBtnCamVersion = findViewById<View>(R.id.lbl_cam_ver) as Button
        mBtnCamVersion.setOnClickListener {
            val apiLevel = if (SimpleCameraPreview.CameraApiLevel.one == mApiLevel)
                SimpleCameraPreview.CameraApiLevel.two
            else
                SimpleCameraPreview.CameraApiLevel.one

            mCameraView.cameraApiLevel = apiLevel
            mApiLevel = mCameraView.cameraAPILevel
            mBtnCamVersion.text = "v. " + mApiLevel.toString()
        }

        mBtnCamVersion.text = "v. " + mApiLevel.toString()
    }

    override fun onResume() {
        super.onResume()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getCameraPermission()
        } else {
            onHasPermission()
        }
    }

    override fun onPause() {
        super.onPause()

    }

    internal fun onHasPermission() {
        mCameraView.showPreview(mIsFrontFacing)
    }

    /**
     * Check if this device has a camera
     */
    private fun hasCamera(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    }

    override fun onPreview(data: ByteArray, degreesToRotate: Int) {
        Log.v(TAG, "on preview, data size " + data.size + " rotate preview " + degreesToRotate)

        val bitmap = if (mDecodeResampled)
            CameraUtils.decodeSampledBitmap(data, mImgPreviewSnapshot.width, mImgPreviewSnapshot.height)
        else
            BitmapFactory.decodeByteArray(data, 0, data.size)

        Log.v(TAG, "resampled bmp size " + bitmap.byteCount)

        // TODO: This fails w OOM on Galaxy S6!
        mImgPreviewSnapshot.rotation = degreesToRotate.toFloat()
        mImgPreviewSnapshot.setImageBitmap(bitmap)
    }

    override fun onPhoto(data: ByteArray, degreesToRotate: Int) {
        Log.v(TAG, "on photo, data size " + data.size + " rotate img " + degreesToRotate)

        val bitmap = if (mDecodeResampled)
            CameraUtils.decodeSampledBitmap(data, mImgPreviewSnapshot.width, mImgPreviewSnapshot.height)
        else
            BitmapFactory.decodeByteArray(data, 0, data.size)

        Log.v(TAG, "resampled bmp size " + bitmap.byteCount)
        mImgPreviewSnapshot.rotation = degreesToRotate.toFloat()
        mImgPreviewSnapshot.setImageBitmap(bitmap)
    }

    @TargetApi(23)
    fun getCameraPermission() {
        val hasPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        Log.v(TAG, "Get permission, has it? $hasPermission")

        // Here, thisActivity is the current activity
        if (!hasPermission) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                            Manifest.permission.CAMERA)) {

                Toast.makeText(this, "Don't have permission", Toast.LENGTH_LONG).show()

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.CAMERA),
                        REQ_CODE_CAMERA)

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            onHasPermission()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQ_CODE_CAMERA -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    onHasPermission()

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return
            }
        }// other 'case' lines to check for other
        // permissions this app might request
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_FRONT_FACING, mIsFrontFacing)
        outState.putSerializable(KEY_API_LEVEL, mApiLevel)
    }

    internal inner class SavePhotoTask : AsyncTask<ByteArray, String, Uri>() {
        override fun doInBackground(vararg jpeg: ByteArray): Uri {
            val photoFile = File(Environment.getExternalStorageDirectory(), "photo.jpg")

            if (photoFile.exists()) {
                photoFile.delete()
            }

            try {
                val fos = FileOutputStream(photoFile.path)

                fos.write(jpeg[0])
                fos.close()
            } catch (e: java.io.IOException) {
                Log.v("blarg", "Exception in photoCallback", e)
            }

            val uri = Uri.fromFile(photoFile)

            return Uri.fromFile(photoFile)
        }

        override fun onPostExecute(uri: Uri) {
            Log.v("blarg", "done saving")
            onBmpSaved(uri)
        }
    }

    fun onBmpSaved(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            mImgPreviewSnapshot.setImageBitmap(bitmap)

        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    companion object {
        val TAG = "CameraActivity"

        val KEY_FRONT_FACING = "KEY_FRONT_FACING"
        val KEY_API_LEVEL = "KEY_API_LEVEL"

        internal val REQ_CODE_CAMERA = 1
    }
}
