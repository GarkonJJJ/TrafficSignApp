package com.example.trafficsign;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.example.trafficsign.databinding.ActivitySplashBinding;

public class SplashActivity extends AppCompatActivity {
    private ActivitySplashBinding vb;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vb = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        vb.getRoot().postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }, 600);
    }
}
