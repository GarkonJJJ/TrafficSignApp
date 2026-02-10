package com.example.trafficsign;


import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.example.trafficsign.databinding.ActivitySettingsBinding;
import com.example.trafficsign.util.SettingsStore;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding vb;
    private SettingsStore s;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        s = new SettingsStore(this);

        vb.edConf.setText(String.valueOf(s.getConfThr()));
        vb.edNms.setText(String.valueOf(s.getNmsThr()));
        vb.edInput.setText(String.valueOf(s.getInputSize()));
        vb.edThreads.setText(String.valueOf(s.getThreads()));
        vb.swGpu.setChecked(s.isUseGpu());
        vb.edSkip.setText(String.valueOf(s.getSkipFrame()));

        vb.btnBack.setOnClickListener(v -> finish());
        vb.btnSave.setOnClickListener(v -> {
            s.setConfThr(parseF(vb.edConf.getText().toString(), 0.25f));
            s.setNmsThr(parseF(vb.edNms.getText().toString(), 0.45f));
            s.setInputSize(parseI(vb.edInput.getText().toString(), 640));
            s.setThreads(parseI(vb.edThreads.getText().toString(), 4));
            s.setUseGpu(vb.swGpu.isChecked());
            s.setSkipFrame(parseI(vb.edSkip.getText().toString(), 0));
            vb.txtStatus.setText("已保存（重启实时检测后生效）");
        });
    }

    private float parseF(String s, float def) {
        try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }
    private int parseI(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
