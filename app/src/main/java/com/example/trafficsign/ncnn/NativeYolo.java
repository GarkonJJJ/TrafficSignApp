package com.example.trafficsign.ncnn;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

import com.example.trafficsign.model.DetectionBox;
public class NativeYolo {
    static { System.loadLibrary("native-lib"); }

    public native boolean init(AssetManager assetManager,
                               String paramPath, String binPath, String labelsPath,
                               int inputSize, float confThr, float nmsThr,
                               int numThreads, boolean useGpu);

    public native DetectionBox[] detectBitmap(Bitmap bitmap, float confThr, float nmsThr);

    // rgba: width*height*4 bytes (RGBA8888)
    public native DetectionBox[] detectImageRGBA(byte[] rgba, int width, int height,
                                                 float confThr, float nmsThr);

    public native void release();
}