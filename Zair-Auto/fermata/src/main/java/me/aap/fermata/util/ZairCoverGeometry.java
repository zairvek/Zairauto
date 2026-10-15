/* Zair Auto 0.2.0, 2026-10-14. GPL-3.0. */
package me.aap.fermata.util;

/** Dimensions in dp, shared by the native shelves and regression tests. */
public final class ZairCoverGeometry {
    public final int posterWidth, posterHeight, itemWidth;
    private ZairCoverGeometry(int width, int height) {
        posterWidth = width; posterHeight = height; itemWidth = width + 32;
    }
    public static ZairCoverGeometry fit(int width, int height, float fontScale) {
        int safeWidth = Math.max(120, width);
        int safeHeight = Math.max(120, height);
        int labelSpace = Math.round(64 * Math.max(1f, fontScale));
        int heightLimit = Math.max(64, safeHeight - labelSpace - 30);
        int widthLimit = Math.max(42, Math.min(280, Math.round(safeWidth * .62f)));
        int posterHeight = Math.min(heightLimit, Math.round(widthLimit * 1.5f));
        return new ZairCoverGeometry(Math.max(42, posterHeight * 2 / 3), posterHeight);
    }
}
