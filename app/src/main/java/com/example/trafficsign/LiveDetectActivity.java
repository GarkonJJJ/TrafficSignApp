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

public class LiveDetectActivity extends AppCompatActivity {

    private ActivityLiveDetectBinding vb;
    private PreviewView previewView;
    private ExecutorService analysisExecutor;

    private NativeYolo yolo = new NativeYolo();
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
    }

    private void initNativeIfNeeded() {
        if (nativeReady) return;

        SettingsStore s = new SettingsStore(this);
        nativeReady = yolo.init(
                getAssets(),
                "model.param", "model.bin", "labels.txt",
                s.getInputSize(),
                s.getConfThr(), s.getNmsThr(),
                s.getThreads(),
                s.isUseGpu()
        );
        Log.i("LiveDetect", "nativeReady=" + nativeReady);
    }

    private void startCamera() {
        initNativeIfNeeded();

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

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

    @Override protected void onDestroy() {
        super.onDestroy();
        if (analysisExecutor != null) analysisExecutor.shutdownNow();
        yolo.release();
    }
}
