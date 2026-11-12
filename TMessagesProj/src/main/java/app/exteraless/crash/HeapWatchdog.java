package app.exteraless.crash;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Debug;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * exteradive: watchdog памяти. Раз в минуту пишет в файл сводку — занятость
 * java-кучи, нативную память и число живых Activity. Хвост файла прикладывается
 * к каждому крашу (CrashLog.record), чтобы по отчёту было видно, как росла
 * память перед падением — иначе OOM не локализовать: в самом стеке OOM виден
 * лишь случайный аллокатор-жертва.
 */
public final class HeapWatchdog {

    private static final long TICK_MS = 60_000;
    private static final int MAX_FILE_CHARS = 24 * 1024;
    private static final String FILE_NAME = "oe_mem_watchdog.txt";

    private static DispatchQueue queue;
    private static int activitiesCreated;
    private static int activitiesDestroyed;

    private HeapWatchdog() {
    }

    public static void install() {
        if (queue != null) {
            return;
        }
        try {
            Application app = (Application) ApplicationLoader.applicationContext;
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    activitiesCreated++;
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    activitiesDestroyed++;
                }

                @Override
                public void onActivityStarted(Activity activity) {
                }

                @Override
                public void onActivityResumed(Activity activity) {
                }

                @Override
                public void onActivityPaused(Activity activity) {
                }

                @Override
                public void onActivityStopped(Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }
            });
            queue = new DispatchQueue("oeHeapWatchdog");
            queue.postRunnable(watchdogRunnable, TICK_MS);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            tick();
            queue.postRunnable(this, TICK_MS);
        }
    };

    private static void tick() {
        try {
            Runtime rt = Runtime.getRuntime();
            long javaUsed = rt.totalMemory() - rt.freeMemory();
            long javaMax = rt.maxMemory();
            long nativeUsed = Debug.getNativeHeapAllocatedSize();
            int live = activitiesCreated - activitiesDestroyed;
            int percent = javaMax == 0 ? 0 : (int) (javaUsed * 100 / javaMax);
            String line = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date())
                    + " java=" + (javaUsed >> 20) + "/" + (javaMax >> 20) + "MB (" + percent + "%)"
                    + " native=" + (nativeUsed >> 20) + "MB"
                    + " activities=" + live + "\n";
            append(line);
        } catch (Throwable ignore) {
        }
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    private static void append(String text) {
        final Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        try {
            File f = file(context);
            String existing = "";
            if (f.exists() && f.length() > 0) {
                byte[] bytes = new byte[(int) Math.min(f.length(), MAX_FILE_CHARS)];
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                try {
                    // Файл уже ограничен по размеру, читаем целиком с начала.
                    int read = in.read(bytes);
                    existing = read > 0 ? new String(bytes, 0, read, StandardCharsets.UTF_8) : "";
                } finally {
                    in.close();
                }
            }
            String combined = existing + text;
            if (combined.length() > MAX_FILE_CHARS) {
                combined = combined.substring(combined.length() - MAX_FILE_CHARS);
            }
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            try {
                out.write(combined.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } finally {
                out.close();
            }
        } catch (Throwable ignore) {
        }
    }

    /** Последние строки журнала — прикладываются к крашу. */
    public static String tail(int maxChars) {
        final Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return null;
        }
        try {
            File f = file(context);
            if (!f.exists() || f.length() == 0) {
                return null;
            }
            byte[] bytes = new byte[(int) Math.min(f.length(), maxChars)];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int skipped = (int) Math.max(0, f.length() - maxChars);
            try {
                in.skip(skipped);
                int read = in.read(bytes);
                return read > 0 ? new String(bytes, 0, read, StandardCharsets.UTF_8) : null;
            } finally {
                in.close();
            }
        } catch (Throwable ignore) {
            return null;
        }
    }
}
