#pragma once
#include <string>
#include <vector>
#include <android/asset_manager.h>

struct Detection {
    float x0, y0, x1, y1;
    int label;
    float score;
    std::string label_text;
};

class YoloNcnn {
public:
    YoloNcnn();
    ~YoloNcnn();

    bool init(AAssetManager* mgr,
              const char* param_path,
              const char* bin_path,
              const char* labels_path,
              int input_size,
              float conf_thr,
              float nms_thr,
              int num_threads,
              bool use_gpu);

    std::vector<Detection> detect_rgba(const unsigned char* rgba,
                                       int w, int h,
                                       float conf_thr, float nms_thr);

    void release();

private:
    void load_labels(AAssetManager* mgr, const char* labels_path);

private:
    int input_size_ = 640;
    int num_threads_ = 4;
    bool use_gpu_ = false;

    float conf_thr_ = 0.25f;
    float nms_thr_  = 0.45f;

    std::vector<std::string> labels_;
    // ncnn::Net net_;  // 在 yolo_ncnn.cpp 中 include ncnn 头后定义
    void* net_ptr_ = nullptr;  // 简化：用 void* 避免头文件引入 ncnn
};
