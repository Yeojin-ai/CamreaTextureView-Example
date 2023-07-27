package com.example.cameratextureview;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private static final String TAG ="IRISCameraApp";
    private int REQUEST_CODE_PERMISSION = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA"};

    private TextureView textureView;

    private String CamID="0";
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest captureRequest;
    private CaptureRequest.Builder capReqBuilder;
    private Handler mHandler;
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
        Toast.makeText(this,"on Create",Toast.LENGTH_SHORT).show();

        setContentView(R.layout.activity_main);

        textureView = (TextureView) findViewById(R.id.textureView);

        if (allPermissionsGranted()){
            startCamera();
        }else{
            ActivityCompat.requestPermissions(this,REQUIRED_PERMISSIONS,REQUEST_CODE_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode==REQUEST_CODE_PERMISSION){
            if (allPermissionsGranted()){
                startCamera();
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


    private void startCamera(){
        //'textureListener with 'textureView'.
        Log.d(TAG,"iris startCamera");
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
            textureView.setAlpha(0.7f);
            textureView.setRotation(90.0f);
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

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
            //When the CameraManager open a camera, it should check the permission.
            cameraManager.openCamera(CamID,devicestateCallback,null);
        }else{
            ActivityCompat.requestPermissions(this,REQUIRED_PERMISSIONS,REQUEST_CODE_PERMISSION);
        }
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
        Surface surface = new Surface(texture);

        capReqBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        capReqBuilder.addTarget(surface);

        cameraDevice.createCaptureSession(Arrays.asList(surface),mSessionPreviewStateCallback,null);
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

};
