package com.example.cameratextureview;

import static android.provider.MediaStore.Images.Media.insertImage;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG ="IRISCameraApp";
    private int REQUEST_CODE_PERMISSION = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA"};
    
    //Camera2 and TextureView
        private String CamID="0";
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder capReqBuilder;
    private CameraCaptureSession cameraCaptureSession;
    private TextureView textureView;

    //Save Image
    private ImageReader mImageReader;
    private File file;

    //Handler and Thread
    private Handler mHandler;
    HandlerThread handlerThread;

    private int mWidth = 0;
    private int mHeight = 0;

    Button button_capture;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,180);
        ORIENTATIONS.append(Surface.ROTATION_270,270);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG,"iris onCreate");
        Toast.makeText(this,"onCreate",Toast.LENGTH_SHORT).show();

        setContentView(R.layout.activity_main);
        textureView = (TextureView) findViewById(R.id.textureView);
        button_capture = (Button) findViewById(R.id.button_capture);
    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG,"iris onResume");

        startBackgroundThread();

        button_capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    takePicture();
                } catch (CameraAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        if (allPermissionsGranted()){
            initTextureView();
        }else{
            ActivityCompat.requestPermissions(this,REQUIRED_PERMISSIONS,REQUEST_CODE_PERMISSION);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Toast.makeText(this,"onPause",Toast.LENGTH_SHORT).show();
        stopBackgroundThread();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode==REQUEST_CODE_PERMISSION){
            if (allPermissionsGranted()){
                initTextureView();
            }else {
                Toast.makeText(this,"Permissions are not granted by the user",Toast.LENGTH_SHORT).show();
                this.finish();
            }
        }
    }
    private boolean allPermissionsGranted(){
        for (String permission : REQUIRED_PERMISSIONS){
            if (ContextCompat.checkSelfPermission(this,permission)!= PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }return true;
    }


    private void initTextureView(){
        //'textureListener with 'textureView'.
        Log.d(TAG,"iris initTextureView");

        textureView.setSurfaceTextureListener(textureListener);
    }

    private TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            Log.d(TAG,"iris onSurfaceTextureAvailable");
            try {
                //When the textureView is ready, open a camera using Camera2 API.
                openCamera();
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
            //only textureView can set Alpha and Rotation (not surfaceview)
            textureView.setAlpha(1.0f);
            textureView.setRotation(75.0f);
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            Log.d(TAG,"iris onSurfaceTextureSizeChanged");

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    private void openCamera() throws CameraAccessException{
        Log.d(TAG,"iris openCamera");

        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(CamID);
        StreamConfigurationMap scmap=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size mPreviewSize = scmap.getOutputSizes(ImageFormat.JPEG)[0];
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
            //When the CameraManager open a camera, it should check the permission.
            cameraManager.openCamera(CamID,devicestateCallback,null);
        }else{
            ActivityCompat.requestPermissions(this,REQUIRED_PERMISSIONS,REQUEST_CODE_PERMISSION);
        }
        //setAspectRatioView(mPreviewSize.getHeight(),mPreviewSize.getWidth());

        //set to save image
        mImageReader = ImageReader.newInstance(
                    mPreviewSize.getWidth(),
                    mPreviewSize.getHeight(),
                    ImageFormat.JPEG,
                    1);

    }

    private CameraDevice.StateCallback devicestateCallback = new CameraDevice.StateCallback() {
        //This callback return the Object of CameraDevice.
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG,"iris devicestateCallback onOpened");
            cameraDevice = camera;
            try {
                takePreview();
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {

            Log.d(TAG,"iris devicestateCallback onDisconnected");
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice=null;

        }
    };


    private void takePreview() throws CameraAccessException{
        Log.d(TAG,"iris takePreview");
        SurfaceTexture texture = textureView.getSurfaceTexture();
        Surface previewSurface = new Surface(texture);
        Surface imageSurface = mImageReader.getSurface();

        capReqBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        capReqBuilder.addTarget(previewSurface);
        cameraDevice.createCaptureSession(Arrays.asList(previewSurface,imageSurface),mSessionPreviewStateCallback,null);
    }


    private void takePicture() throws CameraAccessException{
        Log.d(TAG,"iris takePicture");

        try {
            // This is the CaptureRequest.Builder that we use to take a picture.
            CaptureRequest.Builder cCaptReqBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            cCaptReqBuilder.addTarget(mImageReader.getSurface());
            Long timeStampLong= System.currentTimeMillis()/1000;
            String ts=timeStampLong.toString();
            file = new File("sdcard/DCIM/irisPic"+"/"+"texture"+ts+".jpg");
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,mHandler);

            // Use the same AE and AF modes as the preview.
            Log.d(TAG,"iris takePicture2");
            cCaptReqBuilder.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO);
            Log.d(TAG,"iris takePicture3");
            CaptureRequest cCaptureRequest = cCaptReqBuilder.build();
            Log.d(TAG,"iris takePicture4");
            cameraCaptureSession.capture(cCaptureRequest,mSessionCaptureCallback,mHandler);
            Log.d(TAG,"iris takePicture5");

        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public CameraCaptureSession.StateCallback mSessionPreviewStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.d(TAG,"iris mSessionPreviewStateCallback onConfigured");
            if (cameraDevice == null){
                return;
            }
            cameraCaptureSession=session;
            try {
                capReqBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                cameraCaptureSession.setRepeatingRequest(capReqBuilder.build(),null,mHandler);
            }catch (CameraAccessException e){
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Toast.makeText(getApplicationContext(),"Configuration Changed",Toast.LENGTH_SHORT).show();
        }
    };
    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback= new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
            Log.d(TAG,"iris onCaptureProgressed");
            cameraCaptureSession =session;
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Log.d(TAG,"iris onCaptureCompleted");
            cameraCaptureSession=session;
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.d(TAG,"iris onCaptureFailed");
        }
    };
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG,"iris onImageAvailable");

            Image image = null;
            image=reader.acquireLatestImage();  //[CHECK] reader.acquireLastImage()
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.capacity()];
            buffer.get(bytes);
            try {
                save(bytes);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }finally {
                if(image!=null){
                    image.close();
                    reader.close();
                }
            }
        }
    };
    private void save(byte[] bytes) throws IOException{
        Log.d(TAG,"iris save");
        OutputStream outputStream=null;
        try{
            Log.d(TAG,"iris save-try");
            outputStream=new FileOutputStream(file);
            Log.d(TAG,"iris save-try2");
            outputStream.write(bytes);
            Log.d(TAG,"iris save-try3");
            Toast.makeText(this,"saved image",Toast.LENGTH_SHORT).show();
        }finally {
            if (null != outputStream){
                outputStream.close();
            }
        }
    }
    private void startBackgroundThread(){
        handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
    }
    private void stopBackgroundThread(){
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread=null;
            mHandler=null;
        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }

/*
    private void setAspectRatioView(int width, int height){
        if(width>height){
            int newWidth = mWidth;
            int newHeight = ((mWidth*width)/height);
            Log.d("@@@", "TextureView Width : " + newWidth + " TextureView Height : " + newHeight);
            mSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(newWidth,newHeight));
        }
        else{
            int newWidth = mWidth;
            int newHeight = ((mWidth*height)/width);
            Log.d("@@@", "TextureView Width : " + newWidth + " TextureView Height : " + newHeight);
            mSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(newWidth,newHeight));
        }
    }
    */

};
