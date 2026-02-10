package com.example.trafficsign;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.example.trafficsign.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding vb;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        vb.btnLive.setOnClickListener(v -> startActivity(new Intent(this, LiveDetectActivity.class)));
        vb.btnImage.setOnClickListener(v -> startActivity(new Intent(this, ImageDetectActivity.class)));
        vb.btnHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        vb.btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        vb.btnAbout.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
    }
}