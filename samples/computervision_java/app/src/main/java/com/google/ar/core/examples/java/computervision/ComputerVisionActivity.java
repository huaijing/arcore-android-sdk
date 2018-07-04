/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.computervision;

import android.graphics.ImageFormat;
import android.media.Image;
import android.media.MediaRecorder;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.WritePermissionHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/** This is a simple example that demonstrates cpu image access with ARCore. */
public class ComputerVisionActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  private static final String TAG = ComputerVisionActivity.class.getSimpleName();
  private static final String CAMERA_INTRINSICS_TEXT_FORMAT =
      "Unrotated Camera %s Intrinsics:\n\tFocal Length: (%.2f, %.2f)\n\tPrincipal Point: "
          + "(%.2f, %.2f)\n\tImage Dimensions: (%d, %d)\n\tUnrotated Field of View: (%.2fº, %.2fº)";
  private static final float RADIANS_TO_DEGREES = (float) (180 / Math.PI);

  // This app demonstrates two approaches to obtaining image data accessible on CPU:
  // 1. Access the CPU image directly from ARCore. This approach delivers a frame without latency
  //    (if available), but currently is lower resolution than the GPU image.
  // 2. Download the texture from GPU. This approach incurs a 1-frame latency, but allows a high
  //    resolution image.
  private enum ImageAcquisitionPath {
    CPU_DIRECT_ACCESS,
    GPU_DOWNLOAD
  };

  // Select the image acquisition path here.
  private final ImageAcquisitionPath imageAcquisitionPath = ImageAcquisitionPath.CPU_DIRECT_ACCESS;

  // Session management and rendering.
  private GLSurfaceView surfaceView;
  private Session session;
  private boolean installRequested;
  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private CpuImageDisplayRotationHelper cpuImageDisplayRotationHelper;
  private CpuImageTouchListener cpuImageTouchListener;
  private final CpuImageRenderer cpuImageRenderer = new CpuImageRenderer();
  private final EdgeDetector edgeDetector = new EdgeDetector();

  // Camera intrinsics text view.
  private TextView cameraIntrinsicsTextView;

  // The fields below are used for the GPU_DOWNLOAD image acquisition path.
  private final TextureReader textureReader = new TextureReader();
  private int gpuDownloadFrameBufferIndex = -1;

  // ARCore full resolution GL texture typically has a size of 1920 x 1080.
  private static final int TEXTURE_WIDTH = 1920;
  private static final int TEXTURE_HEIGHT = 1080;

  // We choose a lower sampling resolution.
//  private static final int IMAGE_WIDTH = 1280;
//  private static final int IMAGE_HEIGHT = 720;
  private static final int IMAGE_WIDTH = 640;
  private static final int IMAGE_HEIGHT = 480;

  private boolean is_recording = false;
  private boolean run_slam = false;

  private DataSampler dataSampler;
  private String resourceDir;
  private FileOutputStream deviceInfo;
  private FileOutputStream deviceName;
  private File deviceInfo_file;
  private File deviceName_file;


  private long lastTimeStamp = -1;



  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    cameraIntrinsicsTextView = findViewById(R.id.camera_intrinsics_view);
    surfaceView = findViewById(R.id.surfaceview);
    cpuImageDisplayRotationHelper = new CpuImageDisplayRotationHelper(/*context=*/ this);
    cpuImageTouchListener = new CpuImageTouchListener(cpuImageRenderer);

    // Setup a touch listener to control the texture splitter position.
    surfaceView.setOnTouchListener(cpuImageTouchListener);

    // Set up renderer.
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

    installRequested = false;
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        if (!WritePermissionHelper.hasWritePermission(this)) {
          WritePermissionHelper.requestWritePermission(this);
          return;
        }

        session = new Session(/* context= */ this);


      } catch (UnavailableArcoreNotInstalledException
          | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
        exception = e;
      } catch (Exception e) {
        message = "This device does not support AR";
        exception = e;
      }

      if (message != null) {
        messageSnackbarHelper.showError(this, message);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }
    }

    // Note that order matters - see the note in onPause(), the reverse applies here.
    try {
      session.resume();
    } catch (CameraNotAvailableException e) {
      // In some cases (such as another camera app launching) the camera may be given to
      // a different app instead. Handle this properly by showing a message and recreate the
      // session at the next iteration.
      messageSnackbarHelper.showError(this, "Camera not available. Please restart the app.");
      session = null;
      return;
    }
    surfaceView.onResume();
    cpuImageDisplayRotationHelper.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      // Note that the order matters - GLSurfaceView is paused first so that it does not try
      // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
      // still call session.update() and get a SessionPausedException.
      cpuImageDisplayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
          .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

    // Create the texture and pass it to ARCore session to be filled during update().
    try {
      cpuImageRenderer.createOnGlThread(/* context= */ this);

      // The image format can be either IMAGE_FORMAT_RGBA or IMAGE_FORMAT_I8.
      // Set keepAspectRatio to false so that the output image covers the whole viewport.
      textureReader.create(
          /* context= */ this,
          TextureReaderImage.IMAGE_FORMAT_I8,
          IMAGE_WIDTH,
          IMAGE_HEIGHT,
          false);

    } catch (IOException e) {
      Log.e(TAG, "Failed to read an asset file", e);
    }

    is_recording = true;
    dataSampler = new DataSampler();
    resourceDir = dataSampler.genCurSamplerDir();

  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    cpuImageDisplayRotationHelper.onSurfaceChanged(width, height);
    GLES20.glViewport(0, 0, width, height);
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    // Clear screen to notify driver it should not load any pixels from previous frame.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    if (session == null) {
      return;
    }
    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    cpuImageDisplayRotationHelper.updateSessionIfNeeded(session);

    try {
      session.setCameraTextureName(cpuImageRenderer.getTextureId());
      final Frame frame = session.update();

      switch (imageAcquisitionPath) {
        case CPU_DIRECT_ACCESS:
          renderProcessedImageCpuDirectAccess(frame);
          break;
        case GPU_DOWNLOAD:
          renderProcessedImageGpuDownload(frame);
          break;
      }

      // Update the camera intrinsics' text.
      runOnUiThread(
          new Runnable() {
            @Override
            public void run() {
              cameraIntrinsicsTextView.setText(getCameraIntrinsicsText(frame));
            }
          });
    } catch (Exception t) {
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    }
  }

  /* Demonstrates how to access a CPU image directly from ARCore. */
  private void renderProcessedImageCpuDirectAccess(Frame frame) {
    try (Image image = frame.acquireCameraImage()) {
      if (image.getFormat() != ImageFormat.YUV_420_888) {
        throw new IllegalArgumentException(
            "Expected image in YUV_420_888 format, got format " + image.getFormat());
      }

      ByteBuffer processedImageBytesGrayscale =
          edgeDetector.detect(
              image.getWidth(),
              image.getHeight(),
              image.getPlanes()[0].getRowStride(),
              image.getPlanes()[0].getBuffer());


//      ByteBuffer imageBuffer = image.getPlanes()[0].getBuffer();

      byte[] s_ImageBuffer;
      ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
//      ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
//      ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

      int ySize = yBuffer.remaining();
//      int uSize = uBuffer.remaining();
//      int vSize = vBuffer.remaining();

//      s_ImageBuffer = new byte[ySize + uSize + vSize];
      s_ImageBuffer = new byte[ySize];

      //U and V are swapped
      yBuffer.get(s_ImageBuffer, 0, ySize);
//      vBuffer.get(s_ImageBuffer, ySize, vSize);
//      uBuffer.get(s_ImageBuffer, ySize + vSize, uSize);


      if (run_slam) {

      } else {
        if (is_recording) {
          if (lastTimeStamp > 0) {
            try {

              String imageName = resourceDir + "img_data_" + lastTimeStamp + ".bin";
              File imageFile = new File(imageName);
//
//              Log.e("renderProcessedImageCpuDirectAccess :", "" + lastTimeStamp + "," + imageName + "," +
//                      image.getFormat() + ", " + image.getWidth() + "," + image.getHeight() + "," +
//                      image.getPlanes()[0].getRowStride() + "," + image.getPlanes().length + ", " + s_ImageBuffer.length + "," +
//                      ySize + ", " + uSize + "," + vSize + "," + image.getPlanes()[0].getRowStride() + "," + image.getPlanes()[0].getPixelStride() +
//                      "," + image.getPlanes()[1].getRowStride() + "," + image.getPlanes()[1].getPixelStride() + "," + image.getPlanes()[2].getRowStride() + "," +
//                      image.getPlanes()[2].getPixelStride());

              if (imageFile.exists()) {
                imageFile.delete();
              }
              FileOutputStream out = new FileOutputStream(imageFile);

//              byte[] s_ImageBuffer = new byte[imageBuffer.remaining()];
//              imageBuffer.get(s_ImageBuffer, 0, s_ImageBuffer.length);
              out.write(s_ImageBuffer);

//              Log.e("onDrawFrame :", "" + imageBuffer.width + "," + imageBuffer.height + "," +
//                      lastTimeStamp + "," + s_ImageBuffer.length  + "," + imageBuffer.format);

              out.flush();
              out.close();
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        }
      }


      lastTimeStamp = frame.getTimestamp();

      cpuImageRenderer.drawWithCpuImage(
          frame,
          image.getWidth(),
          image.getHeight(),
          processedImageBytesGrayscale,
          cpuImageDisplayRotationHelper.getViewportAspectRatio(),
          cpuImageDisplayRotationHelper.getCameraToDisplayRotation());

    } catch (NotYetAvailableException e) {
      // This exception will routinely happen during startup, and is expected. cpuImageRenderer
      // will handle null image properly, and will just render the background.
      cpuImageRenderer.drawWithoutCpuImage();
    }
  }

  /* Demonstrates how to access a CPU image using a download from GPU */
  private void renderProcessedImageGpuDownload(Frame frame) {
    // If there is a frame being requested previously, acquire the pixels and process it.
    if (gpuDownloadFrameBufferIndex >= 0) {
      TextureReaderImage image = textureReader.acquireFrame(gpuDownloadFrameBufferIndex);

      if (image.format != TextureReaderImage.IMAGE_FORMAT_I8) {
        throw new IllegalArgumentException(
            "Expected image in I8 format, got format " + image.format);
      }

      ByteBuffer processedImageBytesGrayscale =
          edgeDetector.detect(image.width, image.height, /* stride= */ image.width, image.buffer);

      // You should always release frame buffer after using. Otherwise the next call to
      // submitFrame() may fail.
      textureReader.releaseFrame(gpuDownloadFrameBufferIndex);

      cpuImageRenderer.drawWithCpuImage(
          frame,
          IMAGE_WIDTH,
          IMAGE_HEIGHT,
          processedImageBytesGrayscale,
          cpuImageDisplayRotationHelper.getViewportAspectRatio(),
          cpuImageDisplayRotationHelper.getCameraToDisplayRotation());

    } else {
      cpuImageRenderer.drawWithoutCpuImage();
    }

    // Submit request for the texture from the current frame.
    gpuDownloadFrameBufferIndex =
        textureReader.submitFrame(cpuImageRenderer.getTextureId(), TEXTURE_WIDTH, TEXTURE_HEIGHT);
  }

  private String getCameraIntrinsicsText(Frame frame) {
    Camera camera = frame.getCamera();

    boolean shouldShowCpuIntrinsics = (cpuImageRenderer.getSplitterPosition() < 0.5f);
    CameraIntrinsics intrinsics =
        shouldShowCpuIntrinsics ? camera.getImageIntrinsics() : camera.getTextureIntrinsics();
    String intrinsicsLabel = shouldShowCpuIntrinsics ? "Image" : "Texture";

    float[] focalLength = intrinsics.getFocalLength();
    float[] principalPoint = intrinsics.getPrincipalPoint();
    int[] imageSize = intrinsics.getImageDimensions();

    float fovX = (float) (2 * Math.atan2((double) imageSize[0], (double) (2 * focalLength[0])));
    float fovY = (float) (2 * Math.atan2((double) imageSize[1], (double) (2 * focalLength[1])));
    fovX *= RADIANS_TO_DEGREES;
    fovY *= RADIANS_TO_DEGREES;

    return String.format(
        CAMERA_INTRINSICS_TEXT_FORMAT,
        intrinsicsLabel,
        focalLength[0],
        focalLength[1],
        principalPoint[0],
        principalPoint[1],
        imageSize[0],
        imageSize[1],
        fovX,
        fovY);
  }
}
