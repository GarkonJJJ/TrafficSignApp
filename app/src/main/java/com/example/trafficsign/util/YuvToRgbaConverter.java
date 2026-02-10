package com.example.trafficsign.util;

import androidx.camera.core.ImageProxy;

public class YuvToRgbaConverter {

    // 简化实现：YUV_420_888 -> RGBA8888（较“快”的纯 Java 实现会更长，这里给可跑的版本）
    public byte[] yuv420ToRgba(ImageProxy image) {
        // 你可以替换成更高性能的 libyuv / RenderScript 替代方案
        return ImageUtils.imageProxyToRgba(image);
    }
}
