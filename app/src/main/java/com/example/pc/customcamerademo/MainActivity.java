package com.example.pc.customcamerademo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

//first image wrong orientation flash light everytime turn off on

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final int ACTIVITY_SELECT_IMAGE = 1234;
    private static final int MY_PERMISSIONS = 123;
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String WAKE_LOCK_TAG = "TORCH_WAKE_LOCK";

    private static MainActivity torch;
    private PowerManager.WakeLock wakeLock;
    private boolean lightOn;
    private boolean previewOn;

    private Camera.PictureCallback jpegCallback;
    private Camera.Parameters parameters = null;

    private List<Camera.Size> mSupportedPreviewSizes;
    private Camera.Size mPreviewSize;

    private ImageView ivClick;
    private ImageView ivSwitchCamera;
    private FrameLayout flImagePreview;
    private Camera mCamera;
    private SurfaceView surfaceView;
    private SurfaceHolder mHolder;
    private ImageView ivPreview;
    private ImageView ivFlash;
    private View button;

    private boolean isFlashon = false;
    private boolean inPreview = false;

    private int currentCameraId = 0;
    private boolean isPreviewRunning = false;


    public MainActivity() {
        super();
        torch = this;
    }

    public static Bitmap rotate(Bitmap bitmap, int degree) {

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        Matrix mtx = new Matrix();
        mtx.setRotate(degree);
        return Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);
    }

    public static MainActivity getTorch() {
        return torch;
    }

    /*
   * Called by the view (see main.xml)
   */
    public void toggleLight(View view) {
        toggleLight();
    }

    private void toggleLight() {
        if (lightOn) {
            turnLightOff();
        } else {
            turnLightOn();
        }
    }

    private void turnLightOn() {

        mCamera = getCameraInstance();

        if (mCamera == null) {
            Toast.makeText(this, "Camera not found", Toast.LENGTH_LONG);
            // Use the screen as a flashlight (next best thing)
            //  button.setBackgroundColor(COLOR_WHITE);
            return;
        }
        lightOn = true;
        parameters = mCamera.getParameters();
        if (parameters == null) {
            // Use the screen as a flashlight (next best thing)
            //button.setBackgroundColor(TRIM_MEMORY_BACKGROUND);
            return;
        }
        List<String> flashModes = parameters.getSupportedFlashModes();
        // Check if camera flash exists
        if (flashModes == null) {
            // Use the screen as a flashlight (next best thing)
            // button.setBackgroundColor(COLOR_WHITE);
            return;
        }
        String flashMode = parameters.getFlashMode();
        Log.i(TAG, "Flash mode: " + flashMode);
        Log.i(TAG, "Flash modes: " + flashModes);
        if (!Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode)) {
            // Turn on the flash
            if (flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                mCamera.setParameters(parameters);
                //button.setBackgroundColor(COLOR_LIGHT);
                startWakeLock();
            } else {
                Toast.makeText(this, "Flash mode (torch) not supported", Toast.LENGTH_LONG).show();
                // Use the screen as a flashlight (next best thing)
                //button.setBackgroundColor(COLOR_WHITE);
                Log.e(TAG, "FLASH_MODE_TORCH not supported");
            }
        }
    }

    private void turnLightOff() {
        if (lightOn) {
            // set the background to dark
            //button.setBackgroundColor(COLOR_DARK);
            lightOn = false;
            if (mCamera == null) {
                return;
            }
            parameters = mCamera.getParameters();
            if (parameters == null) {
                return;
            }
            List<String> flashModes = parameters.getSupportedFlashModes();
            String flashMode = parameters.getFlashMode();
            // Check if camera flash exists
            if (flashModes == null) {
                return;
            }
            Log.i(TAG, "Flash mode: " + flashMode);
            Log.i(TAG, "Flash modes: " + flashModes);
            if (!Camera.Parameters.FLASH_MODE_OFF.equals(flashMode)) {
                // Turn off the flash
                if (flashModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    mCamera.setParameters(parameters);
                    stopWakeLock();
                } else {
                    Log.e(TAG, "FLASH_MODE_OFF not supported");
                }
            }
        }
    }

    private void stopPreview() {
        if (previewOn && mCamera != null) {
            mCamera.stopPreview();
            previewOn = false;
        }
    }

    private void startWakeLock() {
        if (wakeLock == null) {
            Log.d(TAG, "wakeLock is null, getting a new WakeLock");
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            Log.d(TAG, "PowerManager acquired");
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
            Log.d(TAG, "WakeLock set");
        }
        wakeLock.acquire();
        Log.d(TAG, "WakeLock acquired");
    }

    private void stopWakeLock() {
        if (wakeLock != null) {
            wakeLock.release();
            Log.d(TAG, "WakeLock released");
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (checkRuntimePermission()) {
            //get device name
            String str = android.os.Build.MODEL;
            Log.d("TAG", " " + str);
            initView();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        turnLightOn();
        Log.i(TAG, "onResume");
        if (ivFlash != null) {
            ivFlash.setImageResource(R.drawable.ic_flash_off_black_24dp);
        }
        mCamera = getCameraInstance();
        mCamera.startPreview();
        mCamera.setDisplayOrientation(90);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mCamera != null) {
            stopPreview();
            mCamera.release();
            mCamera = null;
        }
        torch = null;
        Log.i(TAG, "onStop");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCamera != null) {
            turnLightOff();
            stopPreview();
            mCamera.release();
        }
        Log.i(TAG, "onDestroy");
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        // When the search button is long pressed, quit
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            finish();
            return true;
        }
        return false;
    }


    @Override
    public void onPause() {
        super.onPause();
        turnLightOff();
        Log.i(TAG, "onPause");
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
        getCameraInstance();
    }

    public boolean checkRuntimePermission() {
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) +
                ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) +
                ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) +
                ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSIONS);
            return false;
        }
    }

    private void disablePhoneSleep() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void initView() {

        button = findViewById(R.id.button);
        ivClick = (ImageView) findViewById(R.id.ivcameraclick);
        ivSwitchCamera = (ImageView) findViewById(R.id.ivswitchcamera);
        //flImagePreview = (FrameLayout) findViewById(R.id.camera_preview);
        ivPreview = (ImageView) findViewById(R.id.ivimage);
        ivFlash = (ImageView) findViewById(R.id.ivflash);

        surfaceView = (SurfaceView) findViewById(R.id.sv);

        surfaceView.setFocusable(true);
        surfaceView.setBackgroundColor(TRIM_MEMORY_BACKGROUND);

        // Create an instance of Camera
        mCamera = getCameraInstance();

        mHolder = surfaceView.getHolder();
        mHolder.setKeepScreenOn(true);
        mHolder.setFixedSize(400, 300);

        //insatll a surfaceHolder.callback so we get notified when the underlying surface if created and distroyed
        mHolder.addCallback(this);

        // deprecated setting, but required on Android versions prior to 3.0

        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mHolder.setKeepScreenOn(true);

        disablePhoneSleep();

        ivFlash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setFlash();
            }
        });

        ivSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                switchCamera();
            }
        });

        ivPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //going into recent items
               /* Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                Uri uri = Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath() + "/Image_Demo*//**//*");
                startActivityForResult(Intent.createChooser(intent, "/Image_Demo*//**//*"), ACTIVITY_SELECT_IMAGE); */

                //opening gallary
                Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
                startActivityForResult(intent, ACTIVITY_SELECT_IMAGE);


            }
        });

        ivClick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraImage();

            }
        });

        jpegCallback = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {

                File file = getDirc();
                if (!file.exists() && !file.mkdirs()) {
                    Toast.makeText(MainActivity.this, "Can't create directory to save image", Toast.LENGTH_SHORT).show();
                    return;
                }
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyymmddhhmmss");
                String date = simpleDateFormat.format(new Date());
                String photoFile = "Cam_Demo" + date + ".jpg";
                String fileName = file.getAbsolutePath() + "/" + photoFile;
                File picFile = new File(fileName);

                try {
                    FileOutputStream fos = new FileOutputStream(picFile);

                    Bitmap realImage = BitmapFactory.decodeByteArray(data, 0, data.length);

                    ExifInterface exif = new ExifInterface(picFile.toString());

                    Log.d("EXIF value", exif.getAttribute(ExifInterface.TAG_ORIENTATION));
                    if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("6") && currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        realImage = rotate(realImage, 90);
                    } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("8") && currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        realImage = rotate(realImage, 270);
                    } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("3") && currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        realImage = rotate(realImage, 180);
                    } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("0") && currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        realImage = rotate(realImage, 90);
                    } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("0") && currentCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        realImage = rotate(realImage, 270);
                    } else {
                        realImage = rotate(realImage, 90);
                    }
                    boolean bo = realImage.compress(Bitmap.CompressFormat.JPEG, 100, fos);

                    fos.close();

                    ivPreview.setImageBitmap(realImage);

                    Log.d("Info", bo + "");

                } catch (FileNotFoundException e) {
                    Log.d("Info", "File not found: " + e.getMessage());
                } catch (IOException e) {
                    Log.d("TAG", "Error accessing file: " + e.getMessage());
                }

                Toast.makeText(MainActivity.this, "Picture saved", Toast.LENGTH_SHORT).show();
                refreshCamera();
                refreshgallary(picFile);
            }
        };
    }

    private void switchCamera() {

        if (inPreview) {
            mCamera.stopPreview();
        }
        //NB: if you don't release the current camera before switching, you app will crash
        mCamera.release();

        //swap the id of the camera to be used
        if (currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
            currentCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;

        } else {
            currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        }

        mCamera = Camera.open(currentCameraId);
        //Code snippet for this method from somewhere on android developers, i forget where
        //mCamera.setCameraDisplayOrientation(MainActivity.this, currentCameraId, mCamera);
        try {
            //this step is critical or preview on new camera will no know where to render to
            mCamera.setPreviewDisplay(mHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.setDisplayOrientation(90);
        mCamera.startPreview();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case ACTIVITY_SELECT_IMAGE:
                if (resultCode == RESULT_OK) {
                    Uri selectedImage = data.getData();
                    String[] filePathColumn = {MediaStore.Images.Media.DATA};

                    Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                    if (cursor != null) {
                        cursor.moveToFirst();

                        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                        String filePath = cursor.getString(columnIndex);
                        cursor.close();


                        Bitmap yourSelectedImage = BitmapFactory.decodeFile(filePath);
                        /* Now you have choosen image in Bitmap format in object "yourSelectedImage". You can use it in way you want! */
                        ivPreview.setImageBitmap(yourSelectedImage);
                    }

                }
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS:
                if (grantResults.length > 0) {
                    boolean readExternalFile = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean cameraPermission = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                    boolean locationPermission = grantResults[2] == PackageManager.PERMISSION_GRANTED;
                    boolean recordAudio = grantResults[3] == PackageManager.PERMISSION_GRANTED;

                    if (readExternalFile && cameraPermission && locationPermission && recordAudio) {
                        initView();
                    } else {
                        finish();
                    }

                }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.setDisplayOrientation(90);
            mCamera.startPreview();
        } catch (Exception e) {
            Log.d("TAG", "Error setting camera preview: " + e.getMessage());

        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, final int width, final int height) {

        refreshCamera();

        //auto Focus
        mCamera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                if (success) {
                    camera.cancelAutoFocus();//  Only add to this sentence ， Automatic focus
                    doAutoFocus();

                    mCamera = getCameraInstance();

                    if (isPreviewRunning) {
                        mCamera.stopPreview();
                    }

                    parameters = mCamera.getParameters();


                    List<Camera.Size> allSizes = parameters.getSupportedPreviewSizes();
                    Camera.Size size = allSizes.get(0); // get top size
                    for (int i = 0; i < allSizes.size(); i++) {
                        if (allSizes.get(i).width > size.width) {
                            size = allSizes.get(i);
                        }
                    }

                    Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

                    if (display.getRotation() == Surface.ROTATION_0) {
                        parameters.setPreviewSize(height, width);
                        mCamera.setDisplayOrientation(90);
                    }

                    if (display.getRotation() == Surface.ROTATION_90) {
                        parameters.setPreviewSize(width, height);
                    }

                    if (display.getRotation() == Surface.ROTATION_180) {
                        parameters.setPreviewSize(height, width);
                    }

                    if (display.getRotation() == Surface.ROTATION_270) {
                        parameters.setPreviewSize(width, height);
                        mCamera.setDisplayOrientation(180);
                    }

                    if (mCamera != null && parameters != null) {
                        mCamera.setParameters(parameters);
                        previewCamera(mCamera);
                    } else {
                        Toast.makeText(MainActivity.this, "error", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        //stop preview and release camera
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        } else {

        }
    }

    public void previewCamera(Camera camera) {
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();
            isPreviewRunning = true;
        } catch (Exception e) {
            Log.d("TAG", "Cannot start preview", e);
        }
    }

    private void setFlash() {
        if (isFlashon) {
            ivFlash.setImageResource(R.drawable.ic_flash_off_black_24dp);
            parameters = mCamera.getParameters();
            //*EDIT*//params.setFocusMode("continuous-picture");
            //It is better to use defined constraints as opposed to String
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            mCamera.setParameters(parameters);
            isFlashon = false;

        } else {
            parameters = mCamera.getParameters();
            //*EDIT*//params.setFocusMode("continuous-picture");
            //It is better to use defined constraints as opposed to String
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
            mCamera.setParameters(parameters);
            ivFlash.setImageResource(R.drawable.ic_flash_on_black_24dp);
            isFlashon = true;

        }
    }

    //refresh gallary
    public void refreshgallary(File file) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(Uri.fromFile(file));
        sendBroadcast(intent);
    }


    public void refreshCamera() {
        if (mHolder.getSurface() == null) {
            //preview surface doesn't exists
            return;
        }
        //stop preview before making changes
        try {
            mCamera.stopPreview();
            mCamera.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
        //set preview size and make any resize, rotate or reformatting changing here
        //start preview with nav settings
        try {
            mCamera = getCameraInstance();
            mCamera.setPreviewDisplay(mHolder);
            mCamera.setDisplayOrientation(90);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public File getDirc() {

        File file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        return new File(file, "ABCD");

    }

    public void cameraImage() {
        mCamera.takePicture(null, null, jpegCallback);
    }

    //Check if this device has a camera

    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            Toast.makeText(context, "Camera not found", Toast.LENGTH_SHORT).show();
            return false;
        }

    }

    //A safe way to get an instance of the Camera object.

    public Camera getCameraInstance() {
        Camera c = null;
        if (checkCameraHardware(MainActivity.this)) {
            try {
                c = Camera.open(); // attempt to get a Camera instance
            } catch (Exception e) {
                // Camera is not available (in use or does not exist)
                e.printStackTrace();
            }
        }

        if (c != null) {
            parameters = c.getParameters();

            // parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            parameters.setPictureFormat(PixelFormat.JPEG);
            parameters.setJpegQuality(100);

            List<Camera.Size> allSizes = parameters.getSupportedPictureSizes();
            Camera.Size size = allSizes.get(0); // get top size
            for (int i = 0; i < allSizes.size(); i++) {
                if (allSizes.get(i).width > size.width)
                    size = allSizes.get(i);
            }
            //set max Picture Size
            parameters.setPictureSize(size.width, size.height);
            //parameters.setRotation(90);
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);

            if (!Build.MODEL.equals("KORIDY H30")) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);// 1 Continuous focus
            } else {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }
            c.setParameters(parameters);
            c.startPreview();
            c.cancelAutoFocus();// 2 If you want to achieve continuous autofocus ， This sentence must be added.
        }

        return c; // returns null if camera is unavailable
    }

    // handle button auto focus
    private void doAutoFocus() {
        parameters = mCamera.getParameters();
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        mCamera.setParameters(parameters);
        mCamera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                if (success) {
                    camera.cancelAutoFocus();//  Only add to this sentence ， Automatic focus 。
                    if (!Build.MODEL.equals("KORIDY H30")) {
                        parameters = camera.getParameters();
                        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);// 1 Continuous focus
                        camera.setParameters(parameters);
                    } else {
                        parameters = camera.getParameters();
                        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                        camera.setParameters(parameters);
                    }
                }
            }
        });
    }

}