/* Zair Auto 0.2.1, 2026-10-14. GPL-3.0. */
package me.aap.fermata.util;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/** Local crash/stall evidence; exported only when the user taps Share report. */
public final class ZairDiagnostics {
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile boolean foreground;
    private static volatile boolean started;
    private static volatile long acknowledged;
    private static volatile int screen;
    private static final AtomicBoolean pending = new AtomicBoolean();
    private static long recorded;
    private ZairDiagnostics() {}

    public static void markScreen(int id) { screen = id; }
    public static void setForeground(boolean value) {
        foreground = value;
        acknowledged = SystemClock.uptimeMillis();
    }
    public static synchronized void install() {
        if (started) return;
        started = true;
        var previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            StringWriter text = new StringWriter();
            error.printStackTrace(new PrintWriter(text));
            save("crash.txt", "Thread: " + thread.getName() + "\n" + text);
            if (previous != null) previous.uncaughtException(thread, error);
            else android.os.Process.killProcess(android.os.Process.myPid());
        });
        Thread watchdog = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
                if (!foreground) continue;
                long now = SystemClock.uptimeMillis();
                if (pending.compareAndSet(false, true)) {
                    acknowledged = now;
                    main.post(() -> { acknowledged = SystemClock.uptimeMillis(); pending.set(false); });
                } else if (now - acknowledged > 6000 && now - recorded > 60000) {
                    recorded = now;
                    StringBuilder trace = new StringBuilder("Main thread unresponsive for ")
                            .append(now - acknowledged).append(" ms\n");
                    for (StackTraceElement e : Looper.getMainLooper().getThread().getStackTrace())
                        trace.append(" at ").append(e).append('\n');
                    save("stall.txt", trace.toString());
                }
            }
        }, "Zair diagnostics");
        watchdog.setDaemon(true);
        watchdog.start();
    }
    private static File directory() {
        File dir = new File(FermataApplication.get().getFilesDir(), "diagnostics");
        dir.mkdirs();
        return dir;
    }
    private static String header() {
        return "Zair Auto " + BuildConfig.VERSION_NAME + "\nAndroid " + Build.VERSION.RELEASE
                + " / API " + Build.VERSION.SDK_INT + "\n" + Build.MANUFACTURER + " " + Build.MODEL
                + "\nScreen resource id: " + screen + "\nTime: " + System.currentTimeMillis() + "\n\n";
    }
    private static void save(String name, String value) {
        try { Files.write(new File(directory(), name).toPath(), (header() + value).getBytes(StandardCharsets.UTF_8)); }
        catch (Exception ignored) { /* Reporting must never cause another crash. */ }
    }
    private static String readBounded(java.io.InputStream input) throws java.io.IOException {
        java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
        byte[] buffer=new byte[4096];int remaining=65536;
        while(remaining>0){int count=input.read(buffer,0,Math.min(buffer.length,remaining));if(count<0)break;output.write(buffer,0,count);remaining-=count;}
        return new String(output.toByteArray(),StandardCharsets.UTF_8);
    }
    public static void share(MainActivityDelegate a) {
        FermataApplication app = FermataApplication.get();
        app.execute(() -> {
            StringBuilder report = new StringBuilder(header());
            for (String name : new String[]{"crash.txt", "stall.txt"}) {
                File file = new File(directory(), name);
                if (file.isFile() && file.length() < 131072)
                    report.append('\n').append(name).append('\n')
                            .append(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            }
            if (Build.VERSION.SDK_INT >= 30) {
                ActivityManager manager = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
                for (ApplicationExitInfo exit : manager.getHistoricalProcessExitReasons(app.getPackageName(), 0, 5)) {
                    report.append("\nProcess exit: ").append(exit.getTimestamp()).append(" reason=")
                            .append(exit.getReason()).append(" status=").append(exit.getStatus()).append('\n');
                    if (exit.getReason() == ApplicationExitInfo.REASON_ANR) {
                        try (var trace = exit.getTraceInputStream()) {
                            if (trace != null) report.append(readBounded(trace));
                        } catch (Exception ignored) {}
                    }
                }
            }
            File output = new File(app.getCacheDir(), "diagnostics/Zair-Auto-informe.txt");
            output.getParentFile().mkdirs();
            Files.write(output.toPath(), report.toString().getBytes(StandardCharsets.UTF_8));
            return output;
        }).main().onSuccess(output -> {
            if (a.getSupportFragmentManager().isDestroyed()) return;
            var uri = FileProvider.getUriForFile(app, app.getPackageName() + ".FileProvider", output);
            Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(intent, a.getContext().getString(R.string.zair_share_report)));
        }).onFailure(error -> me.aap.utils.ui.UiUtils.showAlert(a.getContext(), error.getMessage()));
    }
}
