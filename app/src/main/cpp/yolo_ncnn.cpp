#include "yolo_ncnn.h"
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <sstream>
#include <algorithm>
#include <cmath>

#include "net.h"
#include "mat.h"
#include "custom_layers/torch_std.h"


#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "YoloNcnn", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "YoloNcnn", __VA_ARGS__)

static std::string read_asset_text(AAssetManager* mgr, const char* path) {
    AAsset* asset = AAssetManager_open(mgr, path, AASSET_MODE_BUFFER);
    if (!asset) return "";
    size_t len = AAsset_getLength(asset);
    std::string s;
    s.resize(len);
    AAsset_read(asset, s.data(), len);
    AAsset_close(asset);
    return s;
}

static inline float clampf(float v, float lo, float hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

static inline float iou_of(const Detection& a, const Detection& b) {
    float inter_x0 = std::max(a.x0, b.x0);
    float inter_y0 = std::max(a.y0, b.y0);
    float inter_x1 = std::min(a.x1, b.x1);
    float inter_y1 = std::min(a.y1, b.y1);
    float iw = std::max(0.f, inter_x1 - inter_x0);
    float ih = std::max(0.f, inter_y1 - inter_y0);
    float inter = iw * ih;
    float area_a = std::max(0.f, a.x1 - a.x0) * std::max(0.f, a.y1 - a.y0);
    float area_b = std::max(0.f, b.x1 - b.x0) * std::max(0.f, b.y1 - b.y0);
    float uni = area_a + area_b - inter;
    return uni > 0 ? inter / uni : 0.f;
}

static void nms_classwise(std::vector<Detection>& dets, float nms_thr) {
    // 按 class 分组 NMS
    std::sort(dets.begin(), dets.end(), [](const Detection& a, const Detection& b){
        return a.score > b.score;
    });

    std::vector<Detection> keep;
    keep.reserve(dets.size());

    std::vector<int> suppressed(dets.size(), 0);

    for (size_t i = 0; i < dets.size(); i++) {
        if (suppressed[i]) continue;
        keep.push_back(dets[i]);
        for (size_t j = i + 1; j < dets.size(); j++) {
            if (suppressed[j]) continue;
            if (dets[i].label != dets[j].label) continue;
            if (iou_of(dets[i], dets[j]) > nms_thr) suppressed[j] = 1;
        }
    }
    dets.swap(keep);
}

YoloNcnn::YoloNcnn() {}
YoloNcnn::~YoloNcnn() { release(); }

void YoloNcnn::load_labels(AAssetManager* mgr, const char* labels_path) {
    labels_.clear();
    std::string txt = read_asset_text(mgr, labels_path);
    std::istringstream iss(txt);
    std::string line;
    while (std::getline(iss, line)) {
        if (!line.empty()) labels_.push_back(line);
    }
    LOGI("labels=%zu", labels_.size());
}

bool YoloNcnn::init(AAssetManager* mgr,
                    const char* param_path,
                    const char* bin_path,
                    const char* labels_path,
                    int input_size,
                    float conf_thr,
                    float nms_thr,
                    int num_threads,
                    bool use_gpu) {
    release();

    input_size_ = input_size;
    conf_thr_ = conf_thr;
    nms_thr_ = nms_thr;
    num_threads_ = num_threads;
    use_gpu_ = use_gpu;

    load_labels(mgr, labels_path);

    auto* net = new ncnn::Net();
    net_ptr_ = net;

//    net->register_custom_layer("torch.std", ncnn::TorchStd_layer_creator);
//    ncnn::Net::register_custom_layer("torch.std", ncnn::TorchStd_layer_creator);
//    ncnn::Net::register_custom_layer("torch.std", ncnn::TorchStd_layer_creator);
    net->register_custom_layer("torch.std", ncnn::TorchStd_layer_creator);



    net->opt.num_threads = num_threads_;
    net->opt.use_vulkan_compute = use_gpu_; // 需要你编译的 ncnn 支持 Vulkan

    int retp = net->load_param(mgr, param_path);
    int retb = net->load_model(mgr, bin_path);

    LOGI("load_param=%d load_model=%d", retp, retb);
    return (retp == 0 && retb == 0);
}

static bool try_input(ncnn::Extractor& ex, const ncnn::Mat& in,
                      const std::initializer_list<const char*>& names) {
    for (auto n : names) {
        if (ex.input(n, in) == 0) return true;


    }
    return false;
}

static bool try_extract(ncnn::Extractor& ex, ncnn::Mat& out,
                        const std::initializer_list<const char*>& names) {
    for (auto n : names) {

        //DEBUG
        LOGI("hit output name=%s", n);

        if (ex.extract(n, out) == 0) return true;
    }
    return false;
}

std::vector<Detection> YoloNcnn::detect_rgba(const unsigned char* rgba,
                                             int w, int h,
                                             float conf_thr, float nms_thr) {
    std::vector<Detection> dets;
    if (!net_ptr_ || !rgba || w <= 0 || h <= 0) return dets;

    auto* net = reinterpret_cast<ncnn::Net*>(net_ptr_);

    // ---------- 1) Letterbox 到 input_size_ ----------
    const int target = input_size_;
    float scale = std::min(target / (float)w, target / (float)h);
    int new_w = (int)std::round(w * scale);
    int new_h = (int)std::round(h * scale);

    ncnn::Mat in = ncnn::Mat::from_pixels(rgba, ncnn::Mat::PIXEL_RGBA2RGB, w, h);

    ncnn::Mat resized;
    ncnn::resize_bilinear(in, resized, new_w, new_h);

    int pad_w = target - new_w;
    int pad_h = target - new_h;
    int pad_left = pad_w / 2;
    int pad_top  = pad_h / 2;

    ncnn::Mat padded;
    // Ultralytics letterbox padding 常用 114
    ncnn::copy_make_border(resized, padded,
                           pad_top, pad_h - pad_top,
                           pad_left, pad_w - pad_left,
                           ncnn::BORDER_CONSTANT, 114.f);

    // ---------- 2) Normalize ----------
    // 你若训练时是 0~1 归一化，这里就是 /255
    const float mean_vals[3] = {0.f, 0.f, 0.f};
    const float norm_vals[3] = {1/255.f, 1/255.f, 1/255.f};
    padded.substract_mean_normalize(mean_vals, norm_vals);

    // ---------- 3) 推理 ----------
    ncnn::Extractor ex = net->create_extractor();
//    ex.set_num_threads(num_threads_);

    bool ok_in = try_input(ex, padded, {"in0", "images", "input"});
    if (!ok_in) {
        LOGE("input blob name not found.");
        return dets;
    }

    ncnn::Mat pred;
    bool ok_out = try_extract(ex, pred, {"out0", "output", "output0", "prob"});
    if (!ok_out) {
        LOGE("output blob name not found.");
        return dets;
    }

    // ---------- 4) 解析输出 ----------
    // 期望： (4+nc) x 8400 或 8400 x (4+nc)
    int nc = (int)labels_.size();
    if (nc <= 0) {
        LOGE("labels empty, nc=0");
        return dets;
    }

    // ncnn::Mat: w,h,c
    // 常见情况：pred.w = 8400, pred.h = 4+nc, pred.c = 1
    // 或者 pred.w = 4+nc, pred.h = 8400
    int dim0 = pred.w;
    int dim1 = pred.h;
    int dim2 = pred.c;

    // 展平成二维：rows x cols
//    int rows = 0, cols = 0;
//    bool transposed = false;
//
//    if (dim2 == 1) {
//        // 2D
//        if (dim1 == 4 + nc) { rows = dim1; cols = dim0; transposed = false; }      // (4+nc) x 8400
//        else if (dim0 == 4 + nc) { rows = dim0; cols = dim1; transposed = true; } // 8400 x (4+nc)
//        else {
//            LOGE("Unexpected pred shape: w=%d h=%d c=%d (nc=%d)", dim0, dim1, dim2, nc);
//            return dets;
//        }
//    } else {
//        // 3D 情况少见：当作 (c*h) x w
//        // 这里做一个保守兜底：如果 dim0==4+nc 且 dim1*dim2==8400
//        if (dim0 == 4 + nc && dim1 * dim2 == 8400) {
//            rows = dim0;
//            cols = dim1 * dim2;
//            transposed = false;
//        } else if (dim0 == 8400 && dim1 * dim2 == 4 + nc) {
//            rows = 4 + nc;
//            cols = 8400;
//            transposed = true;
//        } else {
//            LOGE("Unexpected 3D pred shape: w=%d h=%d c=%d (nc=%d)", dim0, dim1, dim2, nc);
//            return dets;
//        }
//    }

    // 展平成二维：rows x cols
    int rows = 0, cols = 0;
    bool transposed = false;
    int feat = 0;  // ✅ 可能是 4+nc 或 5+nc

    auto match_feat = [&](int x) {
        return (x == 4 + nc) || (x == 5 + nc);
    };

    if (dim2 == 1) {
        // 2D
        if (match_feat(dim1)) {
            rows = dim1; cols = dim0; transposed = false; feat = dim1;   // (feat) x 8400
        } else if (match_feat(dim0)) {
            rows = dim0; cols = dim1; transposed = true;  feat = dim0;   // 8400 x (feat)
        } else {
            LOGE("Unexpected pred shape: w=%d h=%d c=%d (nc=%d)", dim0, dim1, dim2, nc);
            return dets;
        }
    } else {
        // 3D 兜底：把 dim1*dim2 看成 8400
        if (match_feat(dim0) && dim1 * dim2 == 8400) {
            rows = dim0;
            cols = dim1 * dim2;
            transposed = false;
            feat = dim0;
        } else {
            LOGE("Unexpected 3D pred shape: w=%d h=%d c=%d (nc=%d)", dim0, dim1, dim2, nc);
            return dets;
        }
    }


//    auto get_val = [&](int r, int c)->float {
//        // r: 0..(4+nc-1), c: 0..(8400-1)
//        if (dim2 == 1) {
//            if (!transposed) {
//                // pred shape: rows(=h) x cols(=w)
//                const float* rowptr = pred.row(r);
//                return rowptr[c];
//            } else {
//                // pred shape: cols(=h) x rows(=w)
//                // 访问 pred.row(c)[r]
//                const float* rowptr = pred.row(c);
//                return rowptr[r];
//            }
//        } else {
//            // 3D: flatten on (h,c)
//            // pred.channel(k) gives plane w x h? 这里不展开复杂，按连续内存处理
//            const float* p = (const float*)pred.data;
//            // 视为 [cols][rows] or [rows][cols] 不可靠，兜底直接当 rows x cols 连续存储
//            // （一般不会走到这里）
//            return p[r * cols + c];
//        }
//    };

    //优化版
    auto get_val = [&](int r, int c)->float {
        // 视为二维：w=pred.w, h=pred.h
        // 若 transposed=false:  pred.h == rows(feat), pred.w == cols(8400)
        // 若 transposed=true:   pred.h == cols(8400), pred.w == rows(feat)
        if (dim2 == 1) {
            const float* p = (const float*)pred.data;
            int W = pred.w;
            if (!transposed) {
                // index = r * W + c
                return p[r * W + c];
            } else {
                // index = c * W + r
                return p[c * W + r];
            }
        } else {
            // 3D 不在这里强行支持，先直接返回0，避免错读内存
            //（你要是真走到3D，我再按具体 shape 给你写）
            return 0.f;
        }
    };

    // 输出约定（Ultralytics v8 常见导出之一）：
    // [0]=cx, [1]=cy, [2]=w, [3]=h, [4..]=cls probs
    // 若你的导出是 xyxy，请告诉我，我再改（但“默认未改”通常是 xywh）。
    const int num_points = cols;

    dets.reserve(128);

    float global_max = 0.f;

    for (int i = 0; i < num_points; i++) {
        float cx = get_val(0, i);
        float cy = get_val(1, i);
        float bw = get_val(2, i);
        float bh = get_val(3, i);

        // 找最大类
//        int best = -1;
//        float best_score = 0.f;
//        for (int c = 0; c < nc; c++) {
//            float sc = get_val(4 + c, i);
//            if (sc > best_score) { best_score = sc; best = c; }
//        }
//
//        if (best < 0 || best_score < conf_thr) continue;

        // ✅ 兼容两种输出：
        // - 4+nc: [cx cy w h cls...]
        // - 5+nc: [cx cy w h obj cls...]
        float obj = 1.f;
        int cls_off = 4;
        if (feat == 5 + nc) {
            obj = get_val(4, i);
            cls_off = 5;
        }

        // 找最大类
        int best = -1;
        float best_cls = 0.f;
        for (int c = 0; c < nc; c++) {
            float sc = get_val(cls_off + c, i);
            if (sc > best_cls) { best_cls = sc; best = c; }
        }

        // 最终得分：cls * obj（若无 obj，则 obj=1）
        float score = best_cls * obj;
        if (score > global_max) global_max = score;
        if (best < 0 || score < conf_thr) continue;



        // xywh -> xyxy（在 letterbox 坐标系下）
        float x0 = cx - bw * 0.5f;
        float y0 = cy - bh * 0.5f;
        float x1 = cx + bw * 0.5f;
        float y1 = cy + bh * 0.5f;

        // 还原到原图坐标：去 padding / 除 scale
        x0 = (x0 - pad_left) / scale;
        y0 = (y0 - pad_top)  / scale;
        x1 = (x1 - pad_left) / scale;
        y1 = (y1 - pad_top)  / scale;

        x0 = clampf(x0, 0.f, (float)w);
        y0 = clampf(y0, 0.f, (float)h);
        x1 = clampf(x1, 0.f, (float)w);
        y1 = clampf(y1, 0.f, (float)h);

        Detection d;
        d.x0 = x0; d.y0 = y0; d.x1 = x1; d.y1 = y1;
        d.label = best;
        d.score = score;
        d.label_text = (best >= 0 && best < (int)labels_.size()) ? labels_[best] : std::to_string(best);

        dets.push_back(d);

        static int printed = 0;
        if (printed < 1 && score > 0.5f) {
            LOGI("sample box raw: cx=%.3f cy=%.3f bw=%.3f bh=%.3f (target=%d new_w=%d new_h=%d padL=%d padT=%d scale=%.4f)",
                 cx, cy, bw, bh, target, new_w, new_h, pad_left, pad_top, scale);
            printed++;
        }
    }

    // ---------- 5) NMS ----------
    nms_classwise(dets, nms_thr);

    LOGI("pred w=%d h=%d c=%d nc=%d feat=%d max_score=%.4f",
         pred.w, pred.h, pred.c, nc, feat, global_max);

    return dets;
}

void YoloNcnn::release() {
    if (net_ptr_) {
        auto* net = reinterpret_cast<ncnn::Net*>(net_ptr_);
        delete net;
        net_ptr_ = nullptr;
    }
}
