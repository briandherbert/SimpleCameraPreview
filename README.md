# SimpleCameraPreview for Android

...shows a camera preview of any size with minimal effort, and includes very basic photo-taking functionality. Both camera and camera2 APIs are used, but most camera features are not yet implemented (focus, flash, ISO, etc).

```XML
    <burningaltar.com.camerapreviewcompat.SimpleCameraPreview
        android:id="@+id/camera_preview"
        android:layout_width="200dp"
        android:layout_height="300dp"
        custom:cameraApiLevel="one"
        custom:frontFacing="true" />
```

That alone will show a camera preview if possible; the custom attributes are optional.

**FOR API LEVEL >= 23, YOU MUST REMEMBER TO GRANT CAMERA PERMISSIONS** because of Android's new and annoying on-demand permissions model. This library will fail silently if permissions aren't present when initialized, so you'll have to call `SimpleCameraPreview.showPreview()` after they're granted. See [CameraActivity.java](CameraActivity.java) for a ghetto example of this.

## Features
#### Camera APIs
For Android APIs 21 and above, [camera2](http://developer.android.com/intl/es/reference/android/hardware/camera2/package-summary.html) is the default package used under the hood. Below 21, the [legacy camera](http://developer.android.com/intl/es/reference/android/hardware/Camera.html) package is used. As shown in the XML example above, you can force the camera api to `one` or `two`, though it'll only be effective to downgrade. This can also be done via `.setCameraApiLevel()`

#### Front/Rear Camera
Use XML attribute `frontFacing`, or set it in `.showPreview(boolean isFrontFacing)`. There's also `.switchCameras()` to blindly toggle.

#### Preview Size 
Each device has a list of supported preview dimensions. SimpleCameraPreview chooses the largest size that will fit within its own bounds, then scales up to fit exactly within those bounds, adding padding to whatever axis isn't filled. Same logic as [ImageView.ScaleType.CENTER_INSIDE](http://developer.android.com/intl/es/reference/android/widget/ImageView.ScaleType.html). 

I've noticed that some devices incorrectly report supported preview sizes. With camera2, if the selected size fails, SimpleCameraPreview will try to use successively smaller sizes.

#### Taking Photos
There are two types of photos you can take:

1. `.getPhoto()` calls back with the largest sized photo supported by the camera. This can be enormous, so if you're going to show it on screen, take advantage of the included `CameraUtils.decodeSampledBitmap(byte[] bytes, int reqWidth, int reqHeight)` so you don't load more pixels than you can show.

2. `.getNextPreviewFrame()` will call back with a photo that's the size of the selected preview dimens, so it'll be at most as large as your preview. This call will be relatively fast, but the image will be of "preview" quality. The byte array has already been converted from YUV into RGB.


## Usage
Throw this in your gradle file:
```XML
repositories {
  maven {
    url "https://jitpack.io"
  }
}

dependencies {
  compile 'com.github.briandherbert:SimpleCameraPreview:0.1.4'
}
```
Or if the Jitpack route doesn't suit you, copy/paste willy-nilly. There are individual classes for both a [legacy camera](CameraPreviewTexture.java) and [camera2](Camera2PreviewTexture.java) preview view; those might better suit your needs if you'd prefer to deal with a specific camera API directly.

In the Android Studio xml preview pane, a TextView is used to display the camera API and whether front-facing is used.

## Shortcomings / TODO / Wishlist
+ Doesn't save which camera (front/rear) is used or API level; persisting those through activity restarts is the implementer's job.
+ Same deal with requesting camera permissions on >= Marshmallow
+ Certain devices, like the Nexus 4, require special behavior since the firmware/hardware doesn't follow the API contract
+ Only uses the first two hardware cameras (typically FRONT and REAR)
+ Can't manually choose photo size
+ Can't set camera params like focus mode
+ No fallbacks for device reporting unsupported preview sizes with legacy camera api



