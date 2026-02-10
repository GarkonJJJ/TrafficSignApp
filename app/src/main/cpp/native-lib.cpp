#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <string>
#include "yolo_ncnn.h"

static YoloNcnn* g_yolo = nullptr;

static jobject makeBox(JNIEnv* env, jclass clsBox,
                       float l,float t,float r,float b,
                       int labelId, const std::string& label, float score) {
    jmethodID ctor = env->GetMethodID(clsBox, "<init>", "(FFFFILjava/lang/String;F)V");
    jstring jlabel = env->NewStringUTF(label.c_str());
    jobject obj = env->NewObject(clsBox, ctor, l,t,r,b, labelId, jlabel, score);
    env->DeleteLocalRef(jlabel);
    return obj;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_xxx_trafficsign_ncnn_NativeYolo_init(JNIEnv *env, jobject thiz,
                                              jobject assetManager,
                                              jstring paramPath, jstring binPath, jstring labelsPath,
                                              jint inputSize, jfloat confThr, jfloat nmsThr,
                                              jint numThreads, jboolean useGpu) {
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr) return JNI_FALSE;

    const char* p = env->GetStringUTFChars(paramPath, nullptr);
    const char* b = env->GetStringUTFChars(binPath, nullptr);
    const char* l = env->GetStringUTFChars(labelsPath, nullptr);

    if (!g_yolo) g_yolo = new YoloNcnn();
    bool ok = g_yolo->init(mgr, p, b, l, inputSize, confThr, nmsThr, numThreads, useGpu);

    env->ReleaseStringUTFChars(paramPath, p);
    env->ReleaseStringUTFChars(binPath, b);
    env->ReleaseStringUTFChars(labelsPath, l);

    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_xxx_trafficsign_ncnn_NativeYolo_detectImageRGBA(JNIEnv *env, jobject thiz,
                                                         jbyteArray rgba, jint width, jint height,
                                                         jfloat confThr, jfloat nmsThr) {
    if (!g_yolo) return nullptr;

    jbyte* ptr = env->GetByteArrayElements(rgba, nullptr);
    auto dets = g_yolo->detect_rgba(reinterpret_cast<unsigned char*>(ptr), width, height, confThr, nmsThr);
    env->ReleaseByteArrayElements(rgba, ptr, JNI_ABORT);

    jclass clsBox = env->FindClass("com/xxx/trafficsign/model/DetectionBox");
    jobjectArray arr = env->NewObjectArray((jsize)dets.size(), clsBox, nullptr);

    for (jsize i = 0; i < (jsize)dets.size(); i++) {
        const auto& d = dets[i];
        jobject obj = makeBox(env, clsBox, d.x0, d.y0, d.x1, d.y1, d.label, d.label_text, d.score);
        env->SetObjectArrayElement(arr, i, obj);
        env->DeleteLocalRef(obj);
    }
    return arr;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_xxx_trafficsign_ncnn_NativeYolo_detectBitmap(JNIEnv *env, jobject thiz,
                                                      jobject bitmap, jfloat confThr, jfloat nmsThr) {
    if (!g_yolo || !bitmap) return nullptr;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return nullptr;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return nullptr;

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return nullptr;

    auto dets = g_yolo->detect_rgba(reinterpret_cast<unsigned char*>(pixels),
                                    (int)info.width, (int)info.height, confThr, nmsThr);

    AndroidBitmap_unlockPixels(env, bitmap);

    jclass clsBox = env->FindClass("com/xxx/trafficsign/model/DetectionBox");
    jobjectArray arr = env->NewObjectArray((jsize)dets.size(), clsBox, nullptr);

    for (jsize i = 0; i < (jsize)dets.size(); i++) {
        const auto& d = dets[i];
        jobject obj = makeBox(env, clsBox, d.x0, d.y0, d.x1, d.y1, d.label, d.label_text, d.score);
        env->SetObjectArrayElement(arr, i, obj);
        env->DeleteLocalRef(obj);
    }
    return arr;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_xxx_trafficsign_ncnn_NativeYolo_release(JNIEnv *env, jobject thiz) {
if (g_yolo) {
g_yolo->release();
delete g_yolo;
g_yolo = nullptr;
}
}
