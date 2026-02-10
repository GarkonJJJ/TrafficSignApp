cmake_minimum_required(VERSION 3.22.1)
project("trafficsign")

add_library(native-lib SHARED
        native-lib.cpp
        yolo_ncnn.cpp)

# ncnn headers: 指向 include（不是 include/ncnn）
target_include_directories(native-lib PRIVATE
#        ${CMAKE_SOURCE_DIR}/ncnn/${ANDROID_ABI}/include
        ${CMAKE_SOURCE_DIR}/ncnn/arm64-v8a/include/ncnn
)

# 导入静态库 libncnn.a
add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/ncnn/${ANDROID_ABI}/lib/libncnn.a
)

find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)


# ✅ 因为你已经用 NCNN_OPENMP=OFF 编译了 ncnn，所以不需要 omp
#target_link_libraries(native-lib
#        ncnn
#        ${log-lib}
#        ${android-lib}
#        atomic
#        m
#)

target_link_libraries(native-lib
        ncnn
        ${log-lib}
        ${android-lib}
        ${jnigraphics-lib}
        atomic
        m
)

