#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <dirent.h>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <cmath>
#include <android/log.h>

#define TAG "ffesp"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static int g_rFd  = -1;   // O_RDONLY
static int g_rwFd = -1;   // O_RDWR  (writes)
static int g_pid  = -1;

extern "C" {

JNIEXPORT jint JNICALL
Java_com_esp_ff_MemReader_findPid(JNIEnv* env, jclass, jstring jpkg) {
    const char* pkg = env->GetStringUTFChars(jpkg, nullptr);
    DIR* dir = opendir("/proc");
    int found = -1;
    if (dir) {
        struct dirent* de;
        while ((de = readdir(dir))) {
            int pid = atoi(de->d_name);
            if (pid <= 0) continue;
            char path[64];
            snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);
            int fd = open(path, O_RDONLY);
            if (fd < 0) continue;
            char buf[256]{};
            read(fd, buf, sizeof(buf)-1);
            close(fd);
            if (strstr(buf, pkg)) { found = pid; break; }
        }
        closedir(dir);
    }
    env->ReleaseStringUTFChars(jpkg, pkg);
    LOGI("findPid(%s) = %d", pkg, found);
    return found;
}

JNIEXPORT jboolean JNICALL
Java_com_esp_ff_MemReader_openMem(JNIEnv*, jclass, jint pid) {
    if (g_rFd  >= 0) { close(g_rFd);  g_rFd  = -1; }
    if (g_rwFd >= 0) { close(g_rwFd); g_rwFd = -1; }
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/mem", pid);
    g_rFd  = open(path, O_RDONLY);
    g_rwFd = open(path, O_RDWR);     // needs root; fails gracefully
    g_pid  = pid;
    LOGI("openMem(%d) rFd=%d rwFd=%d", pid, g_rFd, g_rwFd);
    return g_rFd >= 0;
}

JNIEXPORT jlong JNICALL
Java_com_esp_ff_MemReader_getModuleBase(JNIEnv* env, jclass, jint pid, jstring jmod) {
    const char* mod = env->GetStringUTFChars(jmod, nullptr);
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/maps", pid);
    FILE* f = fopen(path, "r");
    long base = 0;
    if (f) {
        char line[512];
        while (fgets(line, sizeof(line), f))
            if (strstr(line, mod) && strstr(line, "r-xp"))
                { base = (long)strtoul(line, nullptr, 16); break; }
        fclose(f);
    }
    env->ReleaseStringUTFChars(jmod, mod);
    LOGI("getModuleBase(%s) = 0x%lx", mod, base);
    return base;
}

// ── reads ────────────────────────────────────────────────────
static inline bool rp(long addr, void* dst, size_t n) {
    return g_rFd >= 0 && addr > 0x1000 &&
           pread(g_rFd, dst, n, (off_t)addr) == (ssize_t)n;
}

JNIEXPORT jint   JNICALL Java_com_esp_ff_MemReader_readInt  (JNIEnv*,jclass,jlong a){int   v=0;rp(a,&v,4);return v;}
JNIEXPORT jlong  JNICALL Java_com_esp_ff_MemReader_readLong (JNIEnv*,jclass,jlong a){long  v=0;rp(a,&v,8);return(jlong)v;}
JNIEXPORT jfloat JNICALL Java_com_esp_ff_MemReader_readFloat(JNIEnv*,jclass,jlong a){float v=0;rp(a,&v,4);return v;}

JNIEXPORT jfloatArray JNICALL
Java_com_esp_ff_MemReader_readFloats(JNIEnv* env, jclass, jlong addr, jint n) {
    jfloatArray arr = env->NewFloatArray(n);
    if (g_rFd < 0 || addr <= 0x1000 || n <= 0) return arr;
    auto* buf = new float[n]{};
    pread(g_rFd, buf, (size_t)n*4, (off_t)addr);
    env->SetFloatArrayRegion(arr, 0, n, buf);
    delete[] buf;
    return arr;
}

JNIEXPORT jstring JNICALL
Java_com_esp_ff_MemReader_readUnityString(JNIEnv* env, jclass, jlong ptr) {
    if (g_rFd < 0 || ptr <= 0x1000) return env->NewStringUTF("");
    int len = 0;
    pread(g_rFd, &len, 4, (off_t)(ptr + 0x10));
    if (len <= 0 || len > 127) return env->NewStringUTF("");
    uint16_t buf[128]{};
    pread(g_rFd, buf, (size_t)len*2, (off_t)(ptr + 0x14));
    char out[128]{};
    for (int i = 0; i < len && i < 127; i++) out[i] = (char)(buf[i] & 0x7F);
    return env->NewStringUTF(out);
}

// ── writes ───────────────────────────────────────────────────
JNIEXPORT jboolean JNICALL
Java_com_esp_ff_MemReader_writeFloat(JNIEnv*, jclass, jlong addr, jfloat val) {
    if (g_rwFd < 0 || addr <= 0x1000) return JNI_FALSE;
    return pwrite(g_rwFd, &val, 4, (off_t)addr) == 4;
}

JNIEXPORT jboolean JNICALL
Java_com_esp_ff_MemReader_writeInt(JNIEnv*, jclass, jlong addr, jint val) {
    if (g_rwFd < 0 || addr <= 0x1000) return JNI_FALSE;
    return pwrite(g_rwFd, &val, 4, (off_t)addr) == 4;
}

// ── aimbot helpers ────────────────────────────────────────────

// From a flat float[count*3] of world positions, camera position+forward,
// find the enemy index closest to the crosshair within fovDeg.
// Returns -1 if none qualify.
JNIEXPORT jint JNICALL
Java_com_esp_ff_MemReader_findBestTarget(JNIEnv* env, jclass,
    jfloatArray jpos, jint count,
    jfloat cx, jfloat cy, jfloat cz,
    jfloat fx, jfloat fy, jfloat fz,
    jfloat fovDeg)
{
    if (count <= 0) return -1;
    float* pos = env->GetFloatArrayElements(jpos, nullptr);
    float cosHalf = cosf(fovDeg * 0.5f * (float)M_PI / 180.f);
    int   best    = -1;
    float bestDot = -2.f;

    for (int i = 0; i < count; i++) {
        float dx = pos[i*3+0] - cx;
        float dy = pos[i*3+1] - cy;
        float dz = pos[i*3+2] - cz;
        float d  = sqrtf(dx*dx + dy*dy + dz*dz);
        if (d < 0.001f) continue;
        float dot = (dx*fx + dy*fy + dz*fz) / d;
        if (dot > cosHalf && dot > bestDot) { bestDot = dot; best = i; }
    }
    env->ReleaseFloatArrayElements(jpos, pos, JNI_ABORT);
    return best;
}

// Compute smoothed yaw/pitch toward target.
// yaw = horizontal (around Y), pitch = vertical (around X).
// smooth 0..1: higher = snappier.
// Returns float[2]: {newYaw, newPitch}.
JNIEXPORT jfloatArray JNICALL
Java_com_esp_ff_MemReader_calcAim(JNIEnv* env, jclass,
    jfloat camX, jfloat camY, jfloat camZ,
    jfloat tgX,  jfloat tgY,  jfloat tgZ,
    jfloat curYaw, jfloat curPitch,
    jfloat smooth)
{
    float dx = tgX - camX, dy = tgY - camY, dz = tgZ - camZ;
    float d2d = sqrtf(dx*dx + dz*dz);

    float wantYaw   =  atan2f(dx, dz) * (180.f / (float)M_PI);
    float wantPitch = -atan2f(dy, d2d) * (180.f / (float)M_PI);

    // Shortest-path delta for yaw (handle 360→0 wrap)
    float dy_ = wantYaw - curYaw;
    if (dy_ >  180.f) dy_ -= 360.f;
    if (dy_ < -180.f) dy_ += 360.f;

    float newYaw   = curYaw   + dy_               * smooth;
    float newPitch = curPitch + (wantPitch - curPitch) * smooth;

    jfloatArray arr = env->NewFloatArray(2);
    float res[2] = {newYaw, newPitch};
    env->SetFloatArrayRegion(arr, 0, 2, res);
    return arr;
}

} // extern "C"
