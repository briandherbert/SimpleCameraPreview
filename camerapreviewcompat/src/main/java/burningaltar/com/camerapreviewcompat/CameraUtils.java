package burningaltar.com.camerapreviewcompat;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.util.List;


public class CameraUtils {
    public static final String TAG = "CameraUtils";

    /**
     * Set camera parameters such as preview size, picture size, focus mode, etc.
     */
    public static Camera.Parameters getCameraParams(Camera.Parameters params, int width, int height, boolean isSideways) {
        if (params == null) return null;

        // Preview size
        log("getting best preview size for size " + width + ", " + height);
        Camera.Size mBestPreviewSize = getBiggestSize(params.getSupportedPreviewSizes(), width, height, isSideways);
        log("Best preview size is " + mBestPreviewSize.width + " , " + mBestPreviewSize.height);
        params.setPreviewSize(mBestPreviewSize.width, mBestPreviewSize.height);

        // Picture size
        Camera.Size mBestPicSize = getBiggestSize(params.getSupportedPictureSizes());
        log("Best pic size is " + mBestPicSize.width + " , " + mBestPicSize.height);
        params.setPictureSize(mBestPicSize.width, mBestPicSize.height);

        // Focus mode
        String focusMode = getBestFocusMode(params);

        if (focusMode != null) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }

        return params;
    }

    public static int getBiggestSizeIdx(@NonNull Point[] sizes, int width, int height, boolean isSideways) {
        if (sizes == null || width <= 0 || height <= 0) {
            return 0;
        }

        /*
        Scumbag exceptional cases
        */

        String model = Build.MODEL;
        if (model != null && model.equals("Nexus 4")) {
            log("Nexus 4; using max size for preview and photo");
            width = height = Integer.MAX_VALUE;
        }


        if (isSideways) {
            int temp = width;
            width = height;
            height = temp;
        }

        log("Finding biggest size within " + width + ", " + height);

        int biggestScore = 0;
        int biggestIdx = 0;

        for (int i = 0; i < sizes.length; i++) {
            log("Found size " + sizes[i].x + ", " + sizes[i].y);
            int score = sizes[i].x * sizes[i].y;
            if (sizes[i].x <= width && sizes[i].y <= height &&
                   score > biggestScore) {
                biggestScore = score;
                biggestIdx = i;
            }
        }

        log("Best size is " + sizes[biggestIdx].x + ", " + sizes[biggestIdx].y);
        return biggestIdx;
    }

    /**
     * Given a range of sizes, find the one that is largest while fitting in the given width and height
     */
    public static Camera.Size getBiggestSize(List<Camera.Size> sizes, int width, int height, boolean isSideways) {
        if (sizes == null || width <= 0 || height <= 0) {
            return null;
        }

        Point[] points = new Point[sizes.size()];

        for (int i = 0; i < points.length; i++) {
            points[i] = new Point(sizes.get(i).width, sizes.get(i).height);
        }

        return sizes.get(getBiggestSizeIdx(points, width, height, isSideways));
    }

    public static Camera.Size getBiggestSize(List<Camera.Size> sizes) {
        return getBiggestSize(sizes, Integer.MAX_VALUE, Integer.MAX_VALUE, false);
    }

    @TargetApi(21)
    /**
     * With camera2, find the biggest size that fits within the bounds
     */
    public static Size getBiggestSize(Size[] sizes, int width, int height, boolean isSideways) {
        if (sizes == null || width <= 0 || height <= 0) {
            return null;
        }

        Point[] points = new Point[sizes.length];

        for (int i = 0; i < points.length; i++) {
            points[i] = new Point(sizes[i].getWidth(), sizes[i].getHeight());
        }

        return sizes[getBiggestSizeIdx(points, width, height, isSideways)];
    }

    @TargetApi(21)
    public static Size getBiggestSize(Size[] sizes) {
        return getBiggestSize(sizes, Integer.MAX_VALUE, Integer.MAX_VALUE, false);
    }

    /**
     * Given a screen orientation and a camera (index), find the degrees required to rotate the camera to align with the current orientation.
     */
    public static int getRotationDegrees(Activity activity, Camera.CameraInfo info) {
        // The camera's orientation, in ordinal direction degrees
        int cameraOrientation = info.orientation;

        int screenOrientation = activity.getWindowManager().getDefaultDisplay().getRotation();

        // Translate rotation constants into degrees
        if (screenOrientation == Surface.ROTATION_0) {
            screenOrientation = 0;
        } else if (screenOrientation == Surface.ROTATION_90) {
            screenOrientation = 90;
        } else if (screenOrientation == Surface.ROTATION_180) {
            screenOrientation = 180;
        } else {
            screenOrientation = 270;
        }

        // Tare the rotation and get the result in positive degrees. Parens and order of ops are for clarity and to prevent modulo of a negative.
        int degreesToRotate = ((cameraOrientation - screenOrientation) + 360) % 360;

        return degreesToRotate;
    }

    @TargetApi(21)
    public static int getRotationDegrees(Activity activity, CameraCharacteristics characteristics, boolean isFront) {
        // The camera's orientation, in ordinal direction degrees
        int cameraOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        int screenOrientation = activity.getWindowManager().getDefaultDisplay().getRotation();

        // Translate rotation constants into degrees
        if (screenOrientation == Surface.ROTATION_0) {
            screenOrientation = 0;
        } else if (screenOrientation == Surface.ROTATION_90) {
            screenOrientation = 90;
        } else if (screenOrientation == Surface.ROTATION_180) {
            screenOrientation = 180;
        } else {
            screenOrientation = 270;
        }

        // Tare the rotation and get the result in positive degrees. Parens and order of ops are for clarity and to prevent modulo of a negative.
        int degreesToRotate = ((cameraOrientation - screenOrientation) + 360) % 360;

        if (isFront) {
            if (degreesToRotate == 180) {
                degreesToRotate = 0;
            } else if (degreesToRotate == 0) {
                degreesToRotate = 180;
            }
        }

        log("camera orientation " + cameraOrientation + " screenOrientation " + screenOrientation);

        return degreesToRotate;
    }

    /**
     * For some reason, only 270 and 90 need to be swapped, and only those
     * @param activity
     * @return
     */
    public static int getCamera2PreviewRotation(Activity activity) {
        int screenOrientation = activity.getWindowManager().getDefaultDisplay().getRotation();

        // Translate rotation constants into degrees
        if (screenOrientation == Surface.ROTATION_0) {
            screenOrientation = 0;
        } else if (screenOrientation == Surface.ROTATION_90) {
            screenOrientation = 270;
        } else if (screenOrientation == Surface.ROTATION_180) {
            screenOrientation = 180;
        } else {
            screenOrientation = 90;
        }

        return screenOrientation;
    }


    public static String getBestFocusMode(Camera.Parameters params) {
        if (params == null) return null;

        String focusMode = null;

        List<String> focusModes = params.getSupportedFocusModes();

        if (Build.VERSION.SDK_INT >= 14 && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else if (Build.VERSION.SDK_INT >= 9 && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }

        return focusMode;
    }

    /**
     * There's no sure-fire way to always tell if the camera is sideways without taking a picture, but we'll take an educated guess based on whether
     * the camera's preview sizes are mostly the same as our current orientation.
     *
     * @param cameraPreviewSizes
     * @return
     */
    public static boolean getIsCameraSideways(List<Camera.Size> cameraPreviewSizes, Activity activity) {
        // Get screen dimensions
        Display display = activity.getWindowManager().getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();

        boolean isPortrait = (height >= width);
        int numSidewaysOrientations = 0;

        for (Camera.Size size : cameraPreviewSizes) {
            if (isPortrait == (size.height <= size.width)) {
                numSidewaysOrientations++;
            }
        }

        return (numSidewaysOrientations > cameraPreviewSizes.size() / 2);
    }

    static byte[] fromPreviewData(byte[] bytes, Camera.Size previewSize) {
        int width = previewSize.width;
        int height = previewSize.height;

        log("Converting preview data from size " + width + ", " + height);

        // Convert bytes first to YUV image, then to RGB
        YuvImage yuvImage = new YuvImage(bytes, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream jpegOutput = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 90, jpegOutput);
        return jpegOutput.toByteArray();
    }

    public static Bitmap decodeSampledBitmap(byte[] bytes, int reqWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }


    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static void log(String msg) {
        Log.v(TAG, msg);
    }
}
