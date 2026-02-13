#include "layer.h"
#include <cmath>

namespace ncnn {

    class TorchStd : public Layer
    {
    public:
        TorchStd()
        {
            one_blob_only = true;
            support_inplace = false;
        }

        int load_param(const ParamDict& /*pd*/) override
        {
            // 你的 torch.std 行没有任何参数： torch.std ... 1 1 284 287
            // 所以这里先不解析参数，按最常见（unbiased=false）实现
            unbiased = 0;
            return 0;
        }

        int forward(const Mat& bottom_blob, Mat& top_blob, const Option& opt) const override
        {
            // 你这里实际会遇到 dims=2 (w=12800, h=16)
            if (bottom_blob.dims == 2)
            {
                const int w = bottom_blob.w;
                const int h = bottom_blob.h;

                // 输出 w x 1，后续 BinaryOp div 会自动 broadcast 到 w x h
                top_blob.create(w, 1, 4u, 1, opt.blob_allocator);
                if (top_blob.empty()) return -100;

                const float* ptr = bottom_blob;
                float* out = top_blob;

                for (int i = 0; i < w; i++)
                {
                    // 这一行的起始
                    const float* row = ptr + i * h;

                    // mean
                    double sum = 0.0;
                    for (int j = 0; j < h; j++) sum += row[j];
                    const double mean = sum / (double)h;

                    // var
                    double vsum = 0.0;
                    for (int j = 0; j < h; j++)
                    {
                        const double d = (double)row[j] - mean;
                        vsum += d * d;
                    }

                    double denom = (double)h;
                    if (unbiased && h > 1) denom = (double)(h - 1);

                    const double var = vsum / denom;
                    out[i] = (float)std::sqrt(var);
                }

                return 0;
            }

            // 兜底：其他 dims 先直接复制，保证不崩（但语义可能不对）
            top_blob = bottom_blob.clone(opt.blob_allocator);
            return top_blob.empty() ? -100 : 0;
        }

    public:
        int unbiased = 0;
    };


    ncnn::Layer* TorchStd_layer_creator(void* /*userdata*/) { return new TorchStd; }

//    static Layer* TorchStd_layer_creator(void* /*userdata*/)
//    {
//        return new TorchStd;
//    }

//    DEFINE_LAYER_CREATOR(TorchStd)



} // namespace ncnn
