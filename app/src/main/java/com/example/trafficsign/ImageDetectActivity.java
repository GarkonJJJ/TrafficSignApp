package com.example.trafficsign;

import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.trafficsign.data.HistoryRepository;
import com.example.trafficsign.databinding.ActivityImageDetectBinding;
import com.example.trafficsign.model.DetectionBox;
import com.example.trafficsign.ncnn.NativeYolo;
import com.example.trafficsign.util.SettingsStore;

public class ImageDetectActivity extends AppCompatActivity {

    private ActivityImageDetectBinding vb;
    private NativeYolo yolo = new NativeYolo();
    private boolean nativeReady = false;

    private Uri currentUri;
    private DetectionBox[] lastBoxes;

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                currentUri = uri;
                Glide.with(this).load(uri).into(vb.imageView);
                runDetect(uri);
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivityImageDetectBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        vb.btnPick.setOnClickListener(v -> pickImage.launch("image/*"));
        vb.btnSave.setOnClickListener(v -> saveHistory());
        vb.btnBack.setOnClickListener(v -> finish());

        initNativeIfNeeded();
    }

    private void initNativeIfNeeded() {
        if (nativeReady) return;
        SettingsStore s = new SettingsStore(this);
        nativeReady = yolo.init(getAssets(),
                "model.param", "model.bin", "labels.txt",
                s.getInputSize(), s.getConfThr(), s.getNmsThr(),
                s.getThreads(), s.isUseGpu());
    }

    private void runDetect(Uri uri) {
        initNativeIfNeeded();
        if (!nativeReady) return;

        // 简化：Glide/ContentResolver 解码成 Bitmap
        var bmp = HistoryRepository.decodeUriToBitmap(this, uri, 1280);
        if (bmp == null) return;

        SettingsStore s = new SettingsStore(this);
        long t0 = System.nanoTime();
        lastBoxes = yolo.detectBitmap(bmp, s.getConfThr(), s.getNmsThr());
        long t1 = System.nanoTime();

        vb.overlayView.setDetections(lastBoxes, bmp.getWidth(), bmp.getHeight(), null);
        vb.overlayView.setTiming(0, (t1 - t0)/1e6);
    }

    private void saveHistory() {
        if (currentUri == null || lastBoxes == null) return;
        HistoryRepository repo = new HistoryRepository(this);
        repo.saveHistoryFromUri(currentUri, lastBoxes);
        vb.txtStatus.setText("已保存到历史记录");
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        yolo.release();
    }
}

