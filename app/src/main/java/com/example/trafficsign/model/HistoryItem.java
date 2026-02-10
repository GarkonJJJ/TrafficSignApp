package com.example.trafficsign.model;

public class HistoryItem {
    public String id;
    public long timestamp;
    public String imagePath;   // app 私有目录文件路径
    public String boxesJson;   // DetectionBox[] 的 JSON

    public HistoryItem() {}
}