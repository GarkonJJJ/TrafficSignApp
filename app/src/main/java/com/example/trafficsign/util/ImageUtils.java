package com.example.trafficsign.util;

import android.graphics.ImageFormat;
import android.media.Image;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

public class ImageUtils {


    @OptIn(markerClass = ExperimentalGetImage.class)
    public static byte[] imageProxyToRgba(ImageProxy proxy) {
        Image image = proxy.getImage();
        if (image == null) return new byte[0];

        if (proxy.getFormat() != ImageFormat.YUV_420_888) return new byte[0];

        int w = proxy.getWidth();
        int h = proxy.getHeight();

        // 1) 取出 Y/U/V 平面
        ByteBuffer yBuf = proxy.getPlanes()[0].getBuffer();
        ByteBuffer uBuf = proxy.getPlanes()[1].getBuffer();
        ByteBuffer vBuf = proxy.getPlanes()[2].getBuffer();

        int yRowStride = proxy.getPlanes()[0].getRowStride();
        int uvRowStride = proxy.getPlanes()[1].getRowStride();
        int uvPixelStride = proxy.getPlanes()[1].getPixelStride();

        byte[] out = new byte[w * h * 4];

        int outIndex = 0;
        for (int row = 0; row < h; row++) {
            int yRowStart = row * yRowStride;
            int uvRowStart = (row / 2) * uvRowStride;

            for (int col = 0; col < w; col++) {
                int yIndex = yRowStart + col;
                int uvIndex = uvRowStart + (col / 2) * uvPixelStride;

                int Y = (yBuf.get(yIndex) & 0xFF);
                int U = (uBuf.get(uvIndex) & 0xFF);
                int V = (vBuf.get(uvIndex) & 0xFF);

                int r = (int) (Y + 1.370705f * (V - 128));
                int g = (int) (Y - 0.337633f * (U - 128) - 0.698001f * (V - 128));
                int b = (int) (Y + 1.732446f * (U - 128));

                r = clamp(r); g = clamp(g); b = clamp(b);

                out[outIndex++] = (byte) r;
                out[outIndex++] = (byte) g;
                out[outIndex++] = (byte) b;
                out[outIndex++] = (byte) 255;
            }
        }
        return out;
    }

    private static int clamp(int v) {
        return (v < 0) ? 0 : Math.min(v, 255);
    }
}
