# CameraPreviewViewCompat

...shows a camera preview with minimal effort at the expense of customization, and includes very basic photo-taking functionality.

```XML
    <burningaltar.com.camerapreviewcompat.CameraPreviewViewCompat
        android:id="@+id/camera_preview"
        android:layout_width="200dp"
        android:layout_height="300dp"
        custom:cameraApiLevel="one"
        custom:frontFacing="true" />
```

That alone will show a camera preview if possible; the custom attributes are optional.

**FOR API LEVEL >= 23, YOU MUST REMEMBER TO GRANT CAMERA PERMISSIONS** because of Android's new and annoying on-demand permissions model. This library will fail silently if permissions aren't present when initialized, so you'll have to call `CameraPreviewViewCompat.showPreview()` after they're granted. See [CameraActivity.java](CameraActivity.java) for a ghetto example of this.

## Features
#### Front/Rear Camera
Use XML attribute `frontFacing`, or set it in `.showPreview(boolean isFrontFacing)`. There's also `.switchCameras()` to blindly toggle.

#### Preview Size 
Each device has a list of supported preview dimensions. CameraPreviewViewCompat chooses the largest size that will fit within its own bounds, then scales up to fit exactly within those bounds, adding padding to whatever axis isn't filled. Same logic as [ImageView.ScaleType.CENTER_INSIDE](http://developer.android.com/intl/es/reference/android/widget/ImageView.ScaleType.html). 

I've noticed that some devices incorrectly report supported preview sizes. With camera2, if the selected size fails, CameraPreviewViewCompat will try to use successively smaller sizes.

#### Taking Photos
There are two types of photos you can take:

1. `.getPhoto()` calls back with the largest sized photo supported by the camera. This can be enormous, so if you're going to show it on screen, take advantage of the included `CameraUtils.decodeSampledBitmap(byte[] bytes, int reqWidth, int reqHeight)` so you don't load more pixels than you can show.

2. `.getNextPreviewFrame()` will call back with a photo that's the size of the selected preview dimens, so it'll be at most as large as your preview. This call will be relatively fast, but the image will be of "preview" quality. The byte array has already been converted from YUV into RGB.

#### Camera API
As shown in the XML example above, you can force the camera api to `one` or `two`, though it'll only be effective to downgrade. This can also be done via `.setCameraApiLevel()`

## Shortcomings / TODO / Wishlist
+ Doesn't save which camera (front/rear) is used or API level; persisting those through activity restarts is the implementer's job.
+ Same deal with requesting camera permissions on >= Marshmallow
+ Only uses the first two hardware cameras (typically FRONT and REAR)
+ Can't manually choose photo size
+ Can't set camera params like focus mode
+ No fallbacks for device reporting unsupported preview sizes with legacy camera api
+ Really awkward library name



