/*
 * Copyright 2017 Google Inc. All Rights Reserved.
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

import static com.google.ar.core.examples.java.computervision.utility.CameraPermissionHelper.CAMERA_PERMISSION_CODE;

import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.design.widget.BaseTransientBottomBar;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.examples.java.computervision.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.computervision.utility.CameraImageBuffer;
import com.google.ar.core.examples.java.computervision.utility.CameraPermissionHelper;
import com.google.ar.core.examples.java.computervision.utility.DisplayRotationHelper;
import com.google.ar.core.examples.java.computervision.utility.EdgeDetector;
import com.google.ar.core.examples.java.computervision.utility.TextureReader;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** This is a simple example that demonstrates texture reading with ARCore. */
public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
    private static final String TAG = MainActivity.class.getSimpleName();

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView mSurfaceView;

    private Session mSession;
    private Snackbar mMessageSnackbar;
    private DisplayRotationHelper mDisplayRotationHelper;

    private final BackgroundRenderer mBackgroundRenderer = new BackgroundRenderer();
    private final CameraImageBuffer mEdgeImage = new CameraImageBuffer();
    private final TextureReader mTextureReader = new TextureReader();

    // ArCore full resolution texture has a size of 1920 x 1080.
    private static final int TEXTURE_WIDTH = 1920;
    private static final int TEXTURE_HEIGHT = 1080;

    // We choose a lower sampling resolution.
//    private static final int IMAGE_WIDTH = 1024;
//    private static final int IMAGE_HEIGHT = 512;
    private static final int IMAGE_WIDTH = 640;
    private static final int IMAGE_HEIGHT = 480;

    private int mFrameBufferIndex = -1;

    private boolean is_recording = false;
    private boolean run_slam = false;

    private static byte[] s_ImageBuffer = new byte[0];
    private static int s_ImageBufferSize = 0;

    private DataSampler dataSampler;
    private String resourceDir;
    private FileOutputStream deviceInfo;
    private FileOutputStream deviceName;
    private File deviceInfo_file;
    private File deviceName_file;

    //sensor
    private SensorManager sensorManager = null;
    private Sensor acceleratorSeneor ;
    private Sensor gyroscopeSensor;
    private Sensor gravitySensor;
    private Sensor rotationVectorSensor;

    private boolean has_accelerator = false;
    private boolean has_gyroscope = false;
    private boolean has_gravity = false;
    private boolean has_orientation = false;


    private long lastTimeStamp = -1;


    private boolean checkSensor(int type) {
        return (sensorManager.getDefaultSensor(type) != null);
    }

    private Button recordButton;

    private void UpdateButtonText() {
        if (is_recording) {
            recordButton.setText("STOP");
        } else {
            recordButton.setText("START");
        }
    }

    private void startRecord() {
        is_recording = true;
        dataSampler = new DataSampler();
        resourceDir = dataSampler.genCurSamplerDir();

        try {
            deviceName_file = new File(resourceDir + "device_name.txt");
            deviceName = new FileOutputStream(deviceName_file);
            deviceInfo_file = new File(resourceDir + "device_info.txt") ;
            deviceInfo = new FileOutputStream(deviceInfo_file);

            String deviceModelInfo = dataSampler.getDeviceModel();
            String deviceNameInfo = deviceModelInfo + "\nANDROID\n" +
                    (has_accelerator ? 1 : 0) + "\n" +
                    (has_gyroscope ? 1 : 0) + "\n" +
                    (has_gravity ? 1 : 0) + "\n" +
                    (has_orientation ? 1 : 0);
            deviceName.write(deviceNameInfo.getBytes());
            deviceName.flush();
            deviceName.close();

            String deviceMaInfo = dataSampler.getDeviceManufacturer();
            String deviceReInfo = dataSampler.getDeviceRelease();
            String deviceInfoStr = "Model: " + deviceModelInfo + "\r\n" +
                    "Manufacturer: " + deviceMaInfo + "\r\n" +
                    "Release: "+ deviceReInfo + "\r\n";

            if (checkSensor(Sensor.TYPE_ACCELEROMETER)) {
                deviceInfoStr += "Accelerometer: Yes\r\n";
            } else {
                deviceInfoStr += "Accelerometer: No\r\n";
            }
            if (checkSensor(Sensor.TYPE_GYROSCOPE)) {
                deviceInfoStr += "Gyroscope: Yes\r\n";
            } else {
                deviceInfoStr += "Gyroscope: No\r\n";
            }
            if (checkSensor(Sensor.TYPE_MAGNETIC_FIELD)) {
                deviceInfoStr += "Magnetometer: Yes\r\n";
            } else {
                deviceInfoStr += "Magnetometer: No\r\n";
            }
            if (checkSensor(Sensor.TYPE_GRAVITY)) {
                deviceInfoStr += "Gravity: Yes\r\n";
            } else {
                deviceInfoStr += "Gravity: No\r\n";
            }
            if (checkSensor(Sensor.TYPE_LIGHT)) {
                deviceInfoStr += "Light: Yes\r\n";
            } else {
                deviceInfoStr += "Light: No\r\n";
            }
            if (checkSensor(Sensor.TYPE_PRESSURE)) {
                deviceInfoStr += "Pressure: Yes\r\n";
            } else {
                deviceInfoStr += "Pressure: No\r\n";
            }
            if (checkSensor(Sensor.TYPE_PROXIMITY)) {
                deviceInfoStr += "Proximity: Yes\r\n";
            } else {
                deviceInfoStr += "Proximity: No\r\n";
            }
            if (checkSensor(Sensor.TYPE_LINEAR_ACCELERATION)) {
                deviceInfoStr += "LinearAcceleration: Yes\r\n";
            } else {
                deviceInfoStr += "LinearAcceleration: No\r\n";
            }
            if (checkSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)) {
                deviceInfoStr += "Game Rotation Vector: Yes\r\n";
            } else {
                deviceInfoStr += "Game Rotation Vector: No\r\n";
            }
            if (checkSensor(Sensor.TYPE_ROTATION_VECTOR)) {
                deviceInfoStr += "Rotation Vector: Yes\r\n";
            } else {
                deviceInfoStr += "Rotation Vector: No\r\n";
            }

            deviceInfo.write(deviceInfoStr.getBytes());
            deviceInfo.flush();
            deviceInfo.close();
        } catch (FileNotFoundException e) {
            Log.e("File not found: ", e.getMessage());
        } catch (IOException e) {
            Log.e("IOException: ", e.getMessage());
        }
        UpdateButtonText();
    }

    private void stopRecord() {
        is_recording = false;
        dataSampler = null;
        try {
            acceFileStream.close();
            gyroFileStream.close();
            gravFileStream.close();
            rotationVectorFileStream.close();
        } catch (IOException e) {
            Log.e("file: ", "close error");
        }
        acceListernerState = false;
        gyroListernerState = false;
        gravListernerState = false;
        rotationVectorListernerState = false;
        UpdateButtonText();
    }

    //////////////////////////  sensor  /////////////////////////////////
    private void initSensor() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if(sensorManager == null){
            Log.e("Sensor Manager : "," can not check the Sensor Manager");

            gyroscopeSensor = null;
            acceleratorSeneor = null;
            gravitySensor = null;
            rotationVectorSensor = null;
            return;
        }
        gyroscopeSensor  = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        acceleratorSeneor  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if(acceleratorSeneor != null) {
            has_accelerator = true;
        }
        if(gyroscopeSensor != null) {
            has_gyroscope = true;
        }
        if(gravitySensor != null) {
            has_gravity = true;
        }

        if (rotationVectorSensor != null) {
            has_orientation = true;
        } else {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationVectorSensor != null) {
                has_orientation = true;
            }
        }

        if (!run_slam) {
            startRecord();
        }

        if(acceleratorSeneor != null) {
            sensorManager.registerListener(acceleratorListener,acceleratorSeneor,SensorManager.SENSOR_DELAY_FASTEST);
        }
        if(gyroscopeSensor != null) {
            sensorManager.registerListener(gyroscopListener, gyroscopeSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
        if(gravitySensor != null) {
            sensorManager.registerListener(gravityListenser, gravitySensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(rotationVectorListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }


    private boolean gyroListernerState = false;
    private File gyroFile = null;
    private FileOutputStream gyroFileStream = null;
    private SensorEventListener gyroscopListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            long timeStamp = sensorEvent.timestamp;

            if (run_slam) {
                //do nothing

            } else {
                if (!is_recording) return;
                if (!gyroListernerState) {
                    gyroFile = new File(resourceDir + "gyroscope_listener.txt");
                    try {
                        gyroFileStream = new FileOutputStream(gyroFile);
                    } catch (FileNotFoundException e){
                        e.printStackTrace();
                        Log.e("File: ", "file not found!");
                    }
                    gyroListernerState = true;
                }
                String str = Long.toString(timeStamp) + "," +
                        Float.toString(sensorEvent.values[0]) + "," +
                        Float.toString(sensorEvent.values[1]) + "," +
                        Float.toString(sensorEvent.values[2]) + "\r\n";

                try {
                    gyroFileStream.write(str.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e("IO: ", e.getMessage());
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private boolean gravListernerState = false;
    private File gravFile = null;
    private FileOutputStream gravFileStream = null;
    private SensorEventListener gravityListenser =  new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            long timeStamp = sensorEvent.timestamp;

            if (run_slam) {
                //do nothing

            } else {
                if (!is_recording) return;
                if (!gravListernerState) {
                    gravFile = new File(resourceDir + "gravity_listener.txt");
                    try {
                        gravFileStream = new FileOutputStream(gravFile);
                    } catch (FileNotFoundException e){
                        e.printStackTrace();
                        Log.e("File: ", "file not found!");
                    }
                    gravListernerState = true;
                }
                String str = Long.toString(timeStamp) + "," +
                        Float.toString(sensorEvent.values[0]) + "," +
                        Float.toString(sensorEvent.values[1]) + "," +
                        Float.toString(sensorEvent.values[2]) + "\r\n";

                try {
                    gravFileStream.write(str.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e("IO: ", e.getMessage());
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    };

    private boolean acceListernerState = false;
    private File acceFile = null;
    private FileOutputStream acceFileStream = null;
    private SensorEventListener acceleratorListener =  new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            long timeStamp = sensorEvent.timestamp;

            if (run_slam) {
                //do nothing
            } else {
                if (!is_recording) return;
                if (!acceListernerState) {
                    acceFile = new File(resourceDir + "accelerometer_listener.txt");
                    try {
                        acceFileStream = new FileOutputStream(acceFile);
                    } catch (FileNotFoundException e){
                        e.printStackTrace();
                        Log.e("File: ", "file not found!");
                    }
                    acceListernerState = true;
                }
                String str = Long.toString(timeStamp) + "," +
                        Float.toString(sensorEvent.values[0]) + "," +
                        Float.toString(sensorEvent.values[1]) + "," +
                        Float.toString(sensorEvent.values[2]) + "\r\n";

                try {
                    acceFileStream.write(str.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e("IO: ", e.getMessage());
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private boolean rotationVectorListernerState = false;
    private File rotationVectorFile = null;
    private FileOutputStream rotationVectorFileStream = null;
    private SensorEventListener rotationVectorListener =  new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            long timeStamp = sensorEvent.timestamp;

            if (run_slam) {
                //do nothing
            } else {
                if (!is_recording) return;
                if (!rotationVectorListernerState) {
                    rotationVectorFile = new File(resourceDir + "game_rotation_vector_listener.txt");
                    try {
                        rotationVectorFileStream = new FileOutputStream(rotationVectorFile);
                    } catch (FileNotFoundException e){
                        e.printStackTrace();
                        Log.e("File: ", "file not found!");
                    }
                    rotationVectorListernerState = true;
                }
                float[] wRb = new float[9];
                SensorManager.getRotationMatrixFromVector(wRb, sensorEvent.values);
                String str = Long.toString(timeStamp) + "," +
                        Float.toString(wRb[0]) + "," +
                        Float.toString(wRb[1]) + "," +
                        Float.toString(wRb[2]) + "," +
                        Float.toString(wRb[3]) + "," +
                        Float.toString(wRb[4]) + "," +
                        Float.toString(wRb[5]) + "," +
                        Float.toString(wRb[6]) + "," +
                        Float.toString(wRb[7]) + "," +
                        Float.toString(wRb[8]) + "\r\n";

                try {
                    rotationVectorFileStream.write(str.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e("IO: ", e.getMessage());
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSurfaceView = (GLSurfaceView) findViewById(R.id.surfaceview);
        mDisplayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        // Setup a touch listener to control the texture splitter position.
        mSurfaceView.setOnTouchListener(
            new View.OnTouchListener() {
                private static final float SWIPE_SCALING_FACTOR = 1.15f;
                private static final float MIN_DELTA = .01f;
                private float mStartPosition = 0;
                private float mStartCoordinate = 0;

                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                        {
                            mStartCoordinate = e.getY();
                            mStartPosition = mBackgroundRenderer.getSplitterPosition();
                            break;
                        }
                        case MotionEvent.ACTION_MOVE:
                        {
                            float delta = (e.getY() - mStartCoordinate) / mSurfaceView.getHeight();

                            if (Math.abs(delta) > MIN_DELTA) {
                                float newPosition = mStartPosition + delta * SWIPE_SCALING_FACTOR;
                                newPosition = Math.min(1.f, Math.max(0.f, newPosition));
                                mBackgroundRenderer.setSplitterPosition(newPosition);
                            }
                            break;
                        }
                        default:
                            break;
                    }

                    return true;
                }
            });

        recordButton = (Button)findViewById(R.id.start);
        if (run_slam) {

        } else {
            recordButton.setVisibility(View.VISIBLE);
            UpdateButtonText();
            recordButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (is_recording) {
                        stopRecord();
                    } else {
                        startRecord();
                    }
                }
            });
        }

        initSensor();


        // Set up renderer.
        mSurfaceView.setPreserveEGLContextOnPause(true);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        mSurfaceView.setRenderer(this);
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        Exception exception = null;
        String message = null;
        try {
            mSession = new Session(/* context= */ this);
        } catch (UnavailableArcoreNotInstalledException e) {
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
            showSnackbarMessage(message, true);
            Log.e(TAG, "Exception creating session", exception);
            return;
        }

        // Create default config and check if supported.
        Config defaultConfig = new Config(mSession);
        if (!mSession.isSupported(defaultConfig)) {
            showSnackbarMessage("This device does not support AR", true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (CameraPermissionHelper.hasCameraPermission(this)) {
            if (mSession != null) {
                // Note that order matters - see the note in onPause(), the reverse applies here.
                mSession.resume();
            }
            mSurfaceView.onResume();
            mDisplayRotationHelper.onResume();
        } else {
            CameraPermissionHelper.requestCameraPermission(this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Note that the order matters - GLSurfaceView is paused first so that it does not try
        // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
        // still call mSession.update() and get a SessionPausedException.
        mDisplayRotationHelper.onPause();
        mSurfaceView.onPause();
        if (mSession != null) {
            mSession.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode != CAMERA_PERMISSION_CODE) {
          return;
        }
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_DENIED) {
            // Permission denied.
            if (CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied without checking "Do not ask again."
                showSnackbarMessage("Camera permission is needed to run this application", true);
            } else if (!CameraPermissionHelper.hasCameraPermission(this)) {
                // Permission denied with checking "Do not ask again".
                // Show toast and take user to app settings where they can grant the camera
                // permission.
                CameraPermissionHelper.launchPermissionSettings(this);
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Standard Android full-screen functionality.
            getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Create the texture and pass it to ARCore session to be filled during update().
        mBackgroundRenderer.createOnGlThread(/*context=*/ this);

        if (mSession != null) {
            mSession.setCameraTextureName(mBackgroundRenderer.getTextureId());
        }

        // The image format can be either IMAGE_FORMAT_RGBA or IMAGE_FORMAT_I8.
        // Set keepAspectRatio to false so that the output image covers the whole viewport.
//        mTextureReader.create(CameraImageBuffer.IMAGE_FORMAT_I8, IMAGE_WIDTH, IMAGE_HEIGHT, false);
        mTextureReader.create(CameraImageBuffer.IMAGE_FORMAT_RGBA, IMAGE_WIDTH, IMAGE_HEIGHT, false);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mDisplayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (mSession == null) {
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        mDisplayRotationHelper.updateSessionIfNeeded(mSession);

        try {
            Frame frame = mSession.update();

            // If there is a frame being requested previously, acquire the pixels and process it.
            if (mFrameBufferIndex >= 0) {
                CameraImageBuffer imageBuffer = mTextureReader.acquireFrame(mFrameBufferIndex);

                if (imageBuffer.format == imageBuffer.IMAGE_FORMAT_I8) {
                    // Detect the edges from the captured grayscale image.
                    if (EdgeDetector.detect(mEdgeImage, imageBuffer)) {
                        // Set the edge image to renderer as overlay.
                        mBackgroundRenderer.setOverlayImage(mEdgeImage);
                    }
                }


                if (run_slam) {

                } else {
                    if (is_recording) {
                        if (lastTimeStamp > 0) {
                            try {
                                String imageName = resourceDir + "img_data_" + lastTimeStamp + ".bin";
                                File imageFile = new File(imageName);

                                if (imageFile.exists()) {
                                    imageFile.delete();
                                }
                                FileOutputStream out = new FileOutputStream(imageFile);

                                int bufferSize = -1;
                                if (imageBuffer.format == imageBuffer.IMAGE_FORMAT_RGBA) {
                                    bufferSize = imageBuffer.width * imageBuffer.height * 4;
                                } else {
                                    bufferSize = imageBuffer.width * imageBuffer.height;
                                }
                                if (s_ImageBuffer.length < bufferSize || bufferSize < s_ImageBufferSize) {
                                    s_ImageBuffer = new byte[bufferSize];
                                    s_ImageBufferSize = bufferSize;
                                }

                                imageBuffer.buffer.position(0);
                                imageBuffer.buffer.get(s_ImageBuffer);
                                out.write(s_ImageBuffer);

                                Log.e("onDrawFrame :", "" + imageBuffer.width + "," + imageBuffer.height + "," + lastTimeStamp +
                                        "," + s_ImageBuffer.length  + "," + imageBuffer.format);

                                out.flush();
                                out.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                // You should always release frame buffer after using. Otherwise the next call to
                // submitFrame() may fail.
                mTextureReader.releaseFrame(mFrameBufferIndex);
            }

            // Submit request for the texture from the current frame.
            mFrameBufferIndex = mTextureReader.submitFrame(
                mBackgroundRenderer.getTextureId(), TEXTURE_WIDTH, TEXTURE_HEIGHT);


            // Draw background video.
            mBackgroundRenderer.draw(frame);
            lastTimeStamp = frame.getTimestamp();

            Log.e("onDrawFrame :", "" + mTextureReader.getmBufferFormat() + "," + lastTimeStamp);


        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    private void showSnackbarMessage(String message, boolean finishOnDismiss) {
        mMessageSnackbar =
            Snackbar.make(
                MainActivity.this.findViewById(android.R.id.content),
                message,
                Snackbar.LENGTH_INDEFINITE);
        mMessageSnackbar.getView().setBackgroundColor(0xbf323232);
        if (finishOnDismiss) {
            mMessageSnackbar.setAction(
                "Dismiss",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mMessageSnackbar.dismiss();
                    }
                });
            mMessageSnackbar.addCallback(
                new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    @Override
                    public void onDismissed(Snackbar transientBottomBar, int event) {
                        super.onDismissed(transientBottomBar, event);
                        finish();
                    }
                });
        }
        mMessageSnackbar.show();
    }
}
