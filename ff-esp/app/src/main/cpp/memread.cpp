#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <dirent.h>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <android/log.h>

#define TAG "ffesp"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static int  g_memFd = -1;
static int  g_pid   = -1;

extern "C" {

// Find PID by matching /proc/[n]/cmdline against pkgName substring.
JNIEXPORT jint JNICALL
Java_com_esp_ff_MemReader_findPid(JNIEnv* env, jclass, jstring jpkg) {
    const char* pkg = env->GetStringUTFChars(jpkg, nullptr);
    DIR* dir = opendir("/proc");
    int found = -1;
    if (dir) {
        struct dirent* de;
        while ((de = readdir(dir)) != nullptr) {
            int pid = atoi(de->d_name);
            if (pid <= 0) continue;
            char path[64];
            snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);
            int fd = open(path, O_RDONLY);
            if (fd < 0) continue;
            char buf[256]{};
            read(fd, buf, sizeof(buf) - 1);
            close(fd);
            if (strstr(buf, pkg)) { found = pid; break; }
        }
        closedir(dir);
    }
    env->ReleaseStringUTFChars(jpkg, pkg);
    LOGI("findPid(%s) = %d", pkg, found);
    return found;
}

// Open /proc/pid/mem for reading.  Requires root (app granted SU in Magisk).
JNIEXPORT jboolean JNICALL
Java_com_esp_ff_MemReader_openMem(JNIEnv*, jclass, jint pid) {
    if (g_memFd >= 0) { close(g_memFd); g_memFd = -1; }
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/mem", pid);
    g_memFd = open(path, O_RDONLY);
    g_pid   = pid;
    LOGI("openMem(%d) fd=%d", pid, g_memFd);
    return g_memFd >= 0;
}

// Parse /proc/pid/maps and return the first rx segment of modName.
JNIEXPORT jlong JNICALL
Java_com_esp_ff_MemReader_getModuleBase(JNIEnv* env, jclass, jint pid, jstring jmod) {
    const char* mod = env->GetStringUTFChars(jmod, nullptr);
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/maps", pid);
    FILE* f = fopen(path, "r");
    long base = 0;
    if (f) {
        char line[512];
        while (fgets(line, sizeof(line), f)) {
            if (strstr(line, mod) && strstr(line, "r-xp")) {
                base = (long)strtoul(line, nullptr, 16);
                break;
            }
        }
        fclose(f);
    }
    env->ReleaseStringUTFChars(jmod, mod);
    LOGI("getModuleBase(%s) = 0x%lx", mod, base);
    return base;
}

// -- Primitive reads --

JNIEXPORT jint JNICALL
Java_com_esp_ff_MemReader_readInt(JNIEnv*, jclass, jlong addr) {
    if (g_memFd < 0 || addr < 0x1000) return 0;
    int v = 0;
    pread(g_memFd, &v, sizeof(v), (off_t)addr);
    return v;
}

JNIEXPORT jlong JNICALL
Java_com_esp_ff_MemReader_readLong(JNIEnv*, jclass, jlong addr) {
    if (g_memFd < 0 || addr < 0x1000) return 0;
    long v = 0;
    pread(g_memFd, &v, sizeof(v), (off_t)addr);
    return (jlong)v;
}

JNIEXPORT jfloat JNICALL
Java_com_esp_ff_MemReader_readFloat(JNIEnv*, jclass, jlong addr) {
    if (g_memFd < 0 || addr < 0x1000) return 0.f;
    float v = 0.f;
    pread(g_memFd, &v, sizeof(v), (off_t)addr);
    return v;
}

// Read count floats starting at addr into a new jfloatArray.
JNIEXPORT jfloatArray JNICALL
Java_com_esp_ff_MemReader_readFloats(JNIEnv* env, jclass, jlong addr, jint count) {
    jfloatArray arr = env->NewFloatArray(count);
    if (g_memFd < 0 || addr < 0x1000 || count <= 0) return arr;
    auto* buf = new float[count];
    pread(g_memFd, buf, (size_t)count * sizeof(float), (off_t)addr);
    env->SetFloatArrayRegion(arr, 0, count, buf);
    delete[] buf;
    return arr;
}

// Read null-terminated UTF-8 string (max 64 bytes).
JNIEXPORT jstring JNICALL
Java_com_esp_ff_MemReader_readString(JNIEnv* env, jclass, jlong addr) {
    if (g_memFd < 0 || addr < 0x1000) return env->NewStringUTF("");
    char buf[64]{};
    // Unity String: length at +0x10, UTF-16 chars at +0x14 — read raw and convert
    // If addr is a char* directly, just pread it.
    pread(g_memFd, buf, sizeof(buf) - 1, (off_t)addr);
    return env->NewStringUTF(buf);
}

// Read a Unity managed System.String (UTF-16, length at +0x10, chars at +0x14).
JNIEXPORT jstring JNICALL
Java_com_esp_ff_MemReader_readUnityString(JNIEnv* env, jclass, jlong strPtr) {
    if (g_memFd < 0 || strPtr < 0x1000) return env->NewStringUTF("");
    int len = 0;
    pread(g_memFd, &len, 4, (off_t)(strPtr + 0x10));
    if (len <= 0 || len > 127) return env->NewStringUTF("");
    auto* chars = new uint16_t[len + 1]{};
    pread(g_memFd, chars, (size_t)len * 2, (off_t)(strPtr + 0x14));
    // downcast UTF-16 to ASCII (good enough for playernames)
    char buf[128]{};
    for (int i = 0; i < len && i < 127; i++)
        buf[i] = (char)(chars[i] & 0x7F);
    delete[] chars;
    return env->NewStringUTF(buf);
}

} // extern "C"
