/* Zair Auto, GPL-3.0. Changes dated 2026-10-14. */
package me.aap.fermata.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Android-independent validation shared with executable regression tests. */
public final class ZairPlaybackGuard {
    private ZairPlaybackGuard() {}

    public static boolean isCurrentEngine(Object current, Object reported) {
        return current != null && current == reported;
    }

    public static boolean isAudioPath(String path) {
        if (path == null) return false;
        String name = path.toLowerCase(Locale.ROOT);
        return name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".m4b") ||
                name.endsWith(".aac") || name.endsWith(".flac") || name.endsWith(".wav") ||
                name.endsWith(".ogg") || name.endsWith(".opus") || name.endsWith(".aif") ||
                name.endsWith(".aiff") || name.endsWith(".wma");
    }

    public static boolean isMediaUrl(String value) {
        if (value == null || value.isEmpty()) return false;
        try {
            URI uri = new URI(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) ||
                    "http".equalsIgnoreCase(uri.getScheme())) &&
                    uri.getHost() != null && !uri.getHost().isEmpty() &&
                    uri.getRawUserInfo() == null;
        } catch (URISyntaxException ex) {
            return false;
        }
    }
}
