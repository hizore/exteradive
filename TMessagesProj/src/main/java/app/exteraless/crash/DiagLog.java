package app.exteraless.crash;

import android.content.Context;

import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * exteradive (диагностика): milestone-журнал запуска. Пишется в files и
 * зеркалируется в getExternalFilesDir — второе доступно с ПК по USB без рута
 * (Android/data/org.divelite.messenger/files/oe_diag.txt). По нему видно,
 * докатился ли запуск до очередного этапа перед крашем (в т.ч. нативным).
 * Каждый mark обёрнут в try/catch: сам не должен ронять приложение.
 */
public final class DiagLog {

    private static final String INTERNAL = "oe_diag.txt";
    private static final String EXTERNAL = "oe_diag.txt";
    private static long lastFlush;

    private DiagLog() {
    }

    public static void mark(String tag) {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return;
            }
            String line = System.currentTimeMillis() + " " + tag + "\n";
            append(new File(context.getFilesDir(), INTERNAL), line);
            File ext = context.getExternalFilesDir(null);
            if (ext != null) {
                append(new File(ext, EXTERNAL), line);
            }
        } catch (Throwable ignore) {
        }
    }

    private static void append(File file, String line) {
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8);
            writer.write(line);
            writer.flush();
        } catch (Throwable ignore) {
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (Throwable ignore) {
            }
        }
    }

    /** Отразить внутренние диагностические файлы во внешнюю папку (для CrashLog). */
    public static void mirror(String internalName) {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return;
            }
            File src = new File(context.getFilesDir(), internalName);
            if (!src.exists()) {
                return;
            }
            File ext = context.getExternalFilesDir(null);
            if (ext == null) {
                return;
            }
            java.io.FileInputStream in = new java.io.FileInputStream(src);
            try {
                OutputStreamWriter out = new OutputStreamWriter(
                        new FileOutputStream(new File(ext, internalName), false), StandardCharsets.UTF_8);
                byte[] buf = new byte[8192];
                int read;
                byte[] all = new byte[(int) src.length()];
                int off = 0;
                while (off < all.length && (read = in.read(all, off, all.length - off)) > 0) {
                    off += read;
                }
                out.write(new String(all, 0, off, StandardCharsets.UTF_8));
                out.flush();
                out.close();
            } finally {
                in.close();
            }
        } catch (Throwable ignore) {
        }
    }
}
