import me.aap.fermata.util.ZairPlaybackGuard;

/** Run without Android or a network: verification/run-checks.sh. */
public final class ZairPlaybackGuardTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Object first = new Object();
        Object replacement = new Object();
        check(ZairPlaybackGuard.isCurrentEngine(first, first), "active engine accepted");
        check(!ZairPlaybackGuard.isCurrentEngine(replacement, first), "late callback after next/fallback rejected");
        check(!ZairPlaybackGuard.isCurrentEngine(null, first), "callback after stop rejected");
        check(!ZairPlaybackGuard.isCurrentEngine(null, null), "null engines must not be accepted");
        check(ZairPlaybackGuard.isMediaUrl("https://example.com/video.mp4"), "direct video");
        check(ZairPlaybackGuard.isMediaUrl("http://192.168.1.10:8080/live.m3u8?token=123"), "local stream");
        check(ZairPlaybackGuard.isMediaUrl("HTTPS://example.com/live"), "case insensitive scheme");
        check(!ZairPlaybackGuard.isMediaUrl("javascript:alert(1)"), "script is not media URL");
        check(!ZairPlaybackGuard.isMediaUrl("file:///etc/passwd"), "file URL is not a remote source");
        check(!ZairPlaybackGuard.isMediaUrl("https://"), "missing host");
        check(!ZairPlaybackGuard.isMediaUrl("https://example.com/a b"), "invalid spaces");
        check(!ZairPlaybackGuard.isMediaUrl("https://name:password@example.com"), "embedded credentials rejected");
        check(!ZairPlaybackGuard.isMediaUrl(null), "null URL");
        check(!ZairPlaybackGuard.isMediaUrl(""), "empty URL");
        check(ZairPlaybackGuard.isAudioPath("/music/TRACK.MP3"), "HTTP MP3 without MIME is audio");
        check(ZairPlaybackGuard.isAudioPath("/music/book.m4b"), "audiobook link is audio");
        check(ZairPlaybackGuard.isAudioPath("/radio/live.opus"), "Opus link is audio");
        check(!ZairPlaybackGuard.isAudioPath("/video/movie.mp4"), "MP4 keeps video fallback");
        check(!ZairPlaybackGuard.isAudioPath("/live/channel.m3u8"), "ambiguous HLS keeps original behavior");
        check(!ZairPlaybackGuard.isAudioPath(null), "missing path keeps original behavior");
        System.out.println(assertions + " regression assertions passed.");
    }
}
