package com.example.trafficsign;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.trafficsign.databinding.ActivityAboutBinding;

public class AboutActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityAboutBinding vb = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());
        vb.btnBack.setOnClickListener(v -> finish());
    }
}
