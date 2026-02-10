package com.example.trafficsign.model;

public class DetectionBox {
    public float left, top, right, bottom;
    public int labelId;
    public String label;
    public float score;

    public DetectionBox() {}

    public DetectionBox(float left, float top, float right, float bottom,
                        int labelId, String label, float score) {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        this.labelId = labelId; this.label = label; this.score = score;
    }
}