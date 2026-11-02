#pragma once
#include <cmath>

struct Vec3 { float x = 0, y = 0, z = 0; };
struct Vec2 { float x = 0, y = 0; };

// Source 2 row-major view-projection matrix.
// Y-axis: positive = screen-up, so y_screen = (1 - y_ndc) * h * 0.5
inline bool w2s(const float* vm, Vec3 w, Vec2& s, float sw, float sh) {
    float cx = vm[0]*w.x + vm[1]*w.y + vm[2]*w.z  + vm[3];
    float cy = vm[4]*w.x + vm[5]*w.y + vm[6]*w.z  + vm[7];
    float cw = vm[12]*w.x + vm[13]*w.y + vm[14]*w.z + vm[15];
    if (cw < 0.001f) return false;
    s.x = sw * 0.5f * (1.f + cx / cw);
    s.y = sh * 0.5f * (1.f - cy / cw);
    return s.x > 0.f && s.x < sw && s.y > 0.f && s.y < sh;
}

inline float dist3d(Vec3 a, Vec3 b) {
    float dx = a.x-b.x, dy = a.y-b.y, dz = a.z-b.z;
    return sqrtf(dx*dx + dy*dy + dz*dz) * 0.01905f; // Hammer units → metres
}
