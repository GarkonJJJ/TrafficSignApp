package com.example.trafficsign;

import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.trafficsign.data.HistoryRepository;
import com.example.trafficsign.databinding.ActivityHistoryBinding;
import com.example.trafficsign.ui.HistoryAdapter;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class HistoryActivity extends AppCompatActivity {

    private ActivityHistoryBinding vb;
    private HistoryRepository repo;
    private HistoryAdapter adapter;

    private String pendingExportText = "";

    private final ActivityResultLauncher<String> createJson =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), this::writeExport);
    private final ActivityResultLauncher<String> createCsv =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), this::writeExport);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        repo = new HistoryRepository(this);

        adapter = new HistoryAdapter();
        vb.recycler.setLayoutManager(new LinearLayoutManager(this));
        vb.recycler.setAdapter(adapter);

        vb.btnBack.setOnClickListener(v -> finish());
        vb.btnExportJson.setOnClickListener(v -> {
            pendingExportText = repo.exportJsonString();
            createJson.launch("trafficsign_history.json");
        });
        vb.btnExportCsv.setOnClickListener(v -> {
            pendingExportText = repo.exportCsvString();
            createCsv.launch("trafficsign_history.csv");
        });
    }

    @Override protected void onResume() {
        super.onResume();
        adapter.submitList(repo.loadAll());
    }

    private void writeExport(Uri uri) {
        if (uri == null) return;
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) return;
            os.write(pendingExportText.getBytes(StandardCharsets.UTF_8));
            vb.txtStatus.setText("已导出：" + uri);
        } catch (Exception e) {
            vb.txtStatus.setText("导出失败");
        }
    }
}
