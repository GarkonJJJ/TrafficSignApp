package com.example.trafficsign;

import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.example.trafficsign.databinding.ActivityLiveDetectBinding;
import com.example.trafficsign.model.DetectionBox;
import com.example.trafficsign.ncnn.NativeYolo;
import com.example.trafficsign.util.PermissionUtils;
import com.example.trafficsign.util.SettingsStore;
import com.example.trafficsign.util.YuvToRgbaConverter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import android.content.pm.PackageManager;
import android.widget.Toast;
public class LiveDetectActivity extends AppCompatActivity {

    private ActivityResultLauncher<String> cameraPermLauncher;

    private ActivityLiveDetectBinding vb;
    private PreviewView previewView;
    private ExecutorService analysisExecutor;

//    private NativeYolo yolo = new NativeYolo();

    private NativeYolo yolo;
    private boolean nativeInited = false;

    private YuvToRgbaConverter yuvToRgba;
    private boolean nativeReady = false;

    // FPS
    private long lastFpsTs = 0;
    private int frameCount = 0;


    private final ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (PermissionUtils.hasCameraPermission(this)) startCamera();
                else finish();
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        vb = ActivityLiveDetectBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        previewView = vb.previewView;
        analysisExecutor = Executors.newSingleThreadExecutor();
        yuvToRgba = new YuvToRgbaConverter();

        vb.btnBack.setOnClickListener(v -> finish());
        vb.btnToggle.setOnClickListener(v -> vb.overlayView.toggleDraw());

        if (!PermissionUtils.hasCameraPermission(this)) {
            permLauncher.launch(new String[]{Manifest.permission.CAMERA});
        } else {
            startCamera();
        }

        yolo = new NativeYolo();

        cameraPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startCamera();   // 你原来的 CameraX 启动函数
                    } else {
                        Toast.makeText(this, "需要相机权限才能实时检测", Toast.LENGTH_LONG).show();
                    }
                }
        );

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA);
        }


    }

//    private void initNativeIfNeeded() {
//        if (nativeReady) return;
//
//        SettingsStore s = new SettingsStore(this);
//        nativeReady = yolo.init(
//                getAssets(),
//                "model.param", "model.bin", "labels.txt",
//                s.getInputSize(),
//                s.getConfThr(), s.getNmsThr(),
//                s.getThreads(),
//                s.isUseGpu()
//        );
//        Log.i("LiveDetect", "nativeReady=" + nativeReady);
//    }

    //解决点击开始后闪退的问题
    private void initNativeIfNeeded() {
        if (nativeInited) return;

        if (yolo == null) {
            yolo = new com.example.trafficsign.ncnn.NativeYolo();
        }


        String param = "model.param";
        String bin   = "model.bin";
        String labels= "labels.txt";
        float conf = 0.25f;
        float nms = 0.45f;
        int input = 640;
        int threads = 4;
        boolean useGpu = false; // 你如果有设置项就读设置

        boolean ok = yolo.init(
                getAssets(),
                param,
                bin,
                labels,
                input,
                conf,
                nms,
                threads,
                useGpu
        );

        if (!ok) {
//            throw new RuntimeException("NativeYolo.init() failed. Check assets/model files and ABI libs.");
            // ❗不要 throw 直接崩，先提示并退出页面
            android.widget.Toast.makeText(this,
                    "NativeYolo.init() 失败：请确认 assets 下的 model.param / model.bin 是 NCNN 模型且能被加载",
                    android.widget.Toast.LENGTH_LONG).show();
            finish();
        }

        nativeInited = true;
    }


    private void startCamera() {

        Log.d("LiveDetect", "startCamera() called");

        initNativeIfNeeded();

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Log.d("LiveDetect", "cameraProvider got");

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(analysisExecutor, this::analyzeFrame);

                CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, selector, preview, analysis);

            } catch (Exception e) {
                Log.e("LiveDetect", "startCamera failed", e);
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeFrame(ImageProxy image) {
        long t0 = System.nanoTime();

        if (!nativeReady) {
            image.close();
            return;
        }

        // 1) YUV_420_888 -> RGBA byte[]
        byte[] rgba = yuvToRgba.yuv420ToRgba(image);
        int width = image.getWidth();
        int height = image.getHeight();

        SettingsStore s = new SettingsStore(this);
        long tInfer0 = System.nanoTime();
        DetectionBox[] boxes = yolo.detectImageRGBA(rgba, width, height, s.getConfThr(), s.getNmsThr());
        long tInfer1 = System.nanoTime();

        // 2) 更新 Overlay（注意：PreviewView 可能有旋转/镜像，这里先按“画面同向”简单处理）
        runOnUiThread(() -> {
            vb.overlayView.setDetections(boxes, width, height, previewView);
            vb.overlayView.setTiming(
                    calcFps(),
                    (tInfer1 - tInfer0) / 1e6
            );
        });

        image.close();

        long t1 = System.nanoTime();
        // 你也可以在这里统计端到端耗时 (t1-t0)
    }

    private float calcFps() {
        long now = System.currentTimeMillis();
        if (lastFpsTs == 0) lastFpsTs = now;
        frameCount++;
        long dt = now - lastFpsTs;
        if (dt >= 1000) {
            float fps = frameCount * 1000f / dt;
            frameCount = 0;
            lastFpsTs = now;
            return fps;
        }
        return vb.overlayView.getLastFps();
    }

//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        if (analysisExecutor != null) analysisExecutor.shutdownNow();
//        yolo.release();
//    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (yolo != null) yolo.release();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
