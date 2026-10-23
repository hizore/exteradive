package app.exteraless.updater;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import tw.nekomimi.nekogram.helpers.remote.UpdateHelper;
import xyz.nextalone.nagram.NaConfig;

/**
 * OTA-система ExteraDive: проверяет телеграм-канал @exteradive на наличие
 * новых APK. Канал Beta использует посты с хэштегом #Beta, Release — посты
 * с хэштегом #Release. Тел要学会 троян APK-ссылок в html-представлении
 * публичного пред-просмотра https://t.me/s/exteradive.
 */
public final class GitHubUpdater {

    public static final String CHANNEL_URL = "https://t.me/s/exteradive";
    public static final String RELEASES_URL = "https://t.me/exteradive";
    private static final long AUTO_INTERVAL = 6L * 60L * 60L * 1000L; // 6 часов
    private static final String PREFS = "exteraless_updater";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_SKIPPED = "skipped_tag";
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    private static volatile OkHttpClient client;
    private static volatile boolean checking;
    private static volatile Call download;

    private GitHubUpdater() {
    }

    public static final class Release {
        public String tag;
        public String name;
        public String body;
        public String apkUrl;       // прямой или view-URL (https://t.me/exteradive/N?single)
        public String apkName;      // имя файла, напр. ExteraDive-arm64-v8a.apk
        public long apkSize;
        public long published;
        public long messageId;

        public String displayVersion() {
            String value = TextUtils.isEmpty(tag) ? name : tag;
            return value != null && value.startsWith("v") ? value.substring(1) : value;
        }
    }

    public interface CheckCallback {
        void onChecked(Release release, Boolean newer, boolean failed);
    }

    public interface DownloadListener {
        void onProgress(long done, long total);
        void onFinished(boolean success, boolean canceled);
    }

    private static OkHttpClient client() {
        if (client == null) {
            synchronized (GitHubUpdater.class) {
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                            .followRedirects(true)
                            .build();
                }
            }
        }
        return client;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void check(boolean force) {
        int channel = NaConfig.INSTANCE.getAutoUpdateChannel().Int();
        if (!force) {
            if (channel == UpdateHelper.UPDATE_OFF) {
                return;
            }
            if (Math.abs(System.currentTimeMillis() - lastCheck()) < AUTO_INTERVAL) {
                return;
            }
        }
        if (checking) {
            return;
        }
        checking = true;
        fetch((found, isNewer, failed) -> {
            checking = false;
            boolean offer = found != null && (Boolean.TRUE.equals(isNewer) || force && isNewer == null);
            if (offer && (force || !TextUtils.equals(found.tag, prefs().getString(KEY_SKIPPED, null)))) {
                show(found);
            } else if (force) {
                bulletin(failed
                        ? LocaleController.getString(R.string.OEUpdateCheckFailed)
                        : LocaleController.getString(R.string.YourVersionIsLatestNax), failed);
            }
        });
    }

    public static void fetch(CheckCallback callback) {
        final boolean prerelease = NaConfig.INSTANCE.getAutoUpdateChannel().Int() == UpdateHelper.UPDATE_CHANNEL_BETA;
        new Thread(() -> {
            Release release = null;
            Boolean newer = null;
            boolean failed = false;
            try {
                release = latest(prerelease);
                if (release != null) {
                    newer = isNewer(release);
                }
                prefs().edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
            } catch (Exception e) {
                failed = true;
                FileLog.e("GitHubUpdater: channel check failed", e);
            }
            final Release found = release;
            final Boolean isNewer = newer;
            final boolean error = failed;
            AndroidUtilities.runOnUIThread(() -> callback.onChecked(found, isNewer, error));
        }, "tg-channel-updater").start();
    }

    public static long lastCheck() {
        return prefs().getLong(KEY_LAST_CHECK, 0);
    }

    public static boolean isDownloading() {
        return download != null;
    }

    public static void cancelDownload() {
        Call call = download;
        if (call != null) {
            call.cancel();
        }
    }

    private static void bulletin(String text, boolean error) {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) {
            return;
        }
        if (error) {
            BulletinFactory.of(fragment).createErrorBulletin(text).show();
        } else {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.done, text).show();
        }
    }

    private static class Msg {
        long id;
        String text;
        List<String[]> docs = new ArrayList<>(); // [url, name]
    }

    private static List<Msg> parseChannel(String html) {
        List<Msg> out = new ArrayList<>();
        // Найдём все сообщения по data-post
        Pattern pPost = Pattern.compile("data-post=\"exteradive/(\\d+)\"[\\s\\S]*?(?=data-post=\"exteradive/|</section>|$)");
        Matcher m = pPost.matcher(html);
        while (m.find()) {
            Msg msg = new Msg();
            try { msg.id = Long.parseLong(m.group(1)); } catch (Exception ignore) { continue; }
            String body = m.group(0);
            // текст
            Pattern pText = Pattern.compile("<div class=\"tgme_widget_message_text[^\"]*\"[^>]*>([\\s\\S]*?)</div>");
            Matcher mt = pText.matcher(body);
            if (mt.find()) {
                msg.text = stripTags(mt.group(1));
            } else {
                msg.text = "";
            }
            // документы
            Pattern pDoc = Pattern.compile("<a class=\"tgme_widget_message_document_wrap\" href=\"([^\"]+)\"[\\s\\S]*?<div class=\"tgme_widget_message_document_title[^\"]*\"[^>]*>([^<]+)</div>");
            Matcher md = pDoc.matcher(body);
            while (md.find()) {
                msg.docs.add(new String[]{md.group(1).trim(), md.group(2).trim()});
            }
            out.add(msg);
        }
        return out;
    }

    private static String stripTags(String s) {
        if (s == null) return "";
        s = s.replaceAll("<br\\s*/?>", "\n");
        s = s.replaceAll("<[^>]+>", "");
        s = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        return s.trim();
    }

    private static Msg pickPost(List<Msg> posts, boolean beta) {
        // для Beta - хэштег #Beta (или #Prerelease), для Release - #Release
        Msg best = null;
        for (Msg msg : posts) {
            String t = (msg.text == null) ? "" : msg.text;
            boolean matches = beta ? (t.contains("#Beta") || t.contains("#Prerelease") || t.contains("#beta") || t.contains("#prerelease"))
                    : (t.contains("#Release") || t.contains("#release"));
            if (!matches) continue;
            if (best == null || msg.id > best.id) best = msg;
        }
        return best;
    }

    private static String pickAssetUrl(Msg msg) {
        if (msg == null || msg.docs.isEmpty()) return null;
        String[] supported = Build.SUPPORTED_ABIS;
        for (String abi : supported) {
            String abiLower = abi.toLowerCase(Locale.ROOT);
            for (String[] doc : msg.docs) {
                String name = doc[1].toLowerCase(Locale.ROOT);
                if (name.endsWith(".apk") && name.contains(abiLower)) {
                    return doc[0];
                }
            }
        }
        // fallback: первый .apk
        for (String[] doc : msg.docs) {
            if (doc[1].toLowerCase(Locale.ROOT).endsWith(".apk")) {
                return doc[0];
            }
        }
        return null;
    }

    private static String pickAssetName(Msg msg, String url) {
        if (msg == null) return "";
        if (url == null) return "";
        for (String[] doc : msg.docs) {
            if (doc[0].equals(url)) return doc[1];
        }
        return "";
    }

    private static Release latest(boolean prerelease) throws Exception {
        Request request = new Request.Builder().url(CHANNEL_URL).header("User-Agent", "Mozilla/5.0").build();
        String html;
        try (Response response = client().newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) throw new IllegalStateException("HTTP " + response.code());
            html = body.string();
        }
        List<Msg> posts = parseChannel(html);
        Msg chosen = pickPost(posts, prerelease);
        if (chosen == null) return null;

        Release release = new Release();
        release.messageId = chosen.id;
        // версия
        Matcher vm = VERSION.matcher(chosen.text == null ? "" : chosen.text);
        if (vm.find()) {
            release.tag = "v" + vm.group(1) + "." + vm.group(2) + "." + vm.group(3);
        } else {
            release.tag = "v" + chosen.id;
        }
        release.name = chosen.text;
        release.body = chosen.text;
        String viewUrl = pickAssetUrl(chosen);
        release.apkUrl = viewUrl; // может быть view-URL, разрешим при download
        release.apkName = pickAssetName(chosen, viewUrl);
        return release;
    }

    private static Boolean isNewer(Release release) {
        if (release == null) return null;
        // версия
        String tag = release.tag;
        Integer[] remote = parseVer(tag);
        Integer[] local = parseVer(BuildVars.BUILD_VERSION_STRING);
        if (remote != null && local != null) {
            for (int i = 0; i < 3; i++) {
                if (!remote[i].equals(local[i])) {
                    return remote[i] > local[i];
                }
            }
            return false;
        }
        // fallback: message id > last seen (не идеально, но достаточно)
        return true;
    }

    private static Integer[] parseVer(String s) {
        if (s == null) return null;
        Matcher m = VERSION.matcher(s);
        if (!m.find()) return null;
        try {
            return new Integer[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
        } catch (Exception e) {
            return null;
        }
    }

    private static void show(Release release) {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) return;
        String size = release.apkSize > 0 ? AndroidUtilities.formatFileSize(release.apkSize) : "";
        new UpdateSheet(activity, fragment.getResourceProvider(), LocaleController.getString(R.string.OEUpdateTitle),
                (TextUtils.isEmpty(release.name) ? release.tag : release.name),
                (TextUtils.isEmpty(release.body) ? release.tag : release.body),
                (size.isEmpty() ? LocaleController.getString(R.string.OEUpdateInstall)
                        : LocaleController.formatString(R.string.OEUpdateInstallSize, size)),
                new UpdateSheet.Delegate() {
                    @Override
                    public void onUpdate(UpdateSheet sheet) {
                        sheet.setDownloading(true);
                        download(activity, release, new DownloadListener() {
                            @Override
                            public void onProgress(long done, long total) {
                                sheet.setProgress(done, total);
                            }

                            @Override
                            public void onFinished(boolean success, boolean canceled) {
                                if (success) {
                                    sheet.finishDownload();
                                } else if (!canceled) {
                                    sheet.setDownloading(false);
                                }
                            }
                        });
                    }

                    @Override
                    public void onSkip() {
                        prefs().edit().putString(KEY_SKIPPED, release.tag).apply();
                    }

                    @Override
                    public void onCancelDownload() {
                        cancelDownload();
                    }
                }).show();
    }

    private static String resolveDirectUrl(String viewUrl) {
        if (viewUrl == null) return null;
        if (viewUrl.contains("telesco.pe/file") || viewUrl.toLowerCase(Locale.ROOT).contains(".apk?") && !viewUrl.contains("t.me")) {
            // уже прямой
            if (viewUrl.toLowerCase(Locale.ROOT).contains(".apk")) return viewUrl;
        }
        try {
            Request request = new Request.Builder().url(viewUrl).header("User-Agent", "Mozilla/5.0").build();
            try (Response response = client().newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) return viewUrl;
                String html = body.string();
                // найти прямую CDN-ссылку на любой .apk
                Pattern p = Pattern.compile("href=\"(https?://[^\"]+\\.apk[^\"]*)\"");
                Matcher m = p.matcher(html);
                if (m.find()) return m.group(1);
                // альтернатива: ссылки cdn*.telesco.pe/file
                Pattern p2 = Pattern.compile("(https?://[^\"'<> ]+telesco\\.pe/file[^\"'<> ]+)");
                Matcher m2 = p2.matcher(html);
                if (m2.find()) return m2.group(1);
            }
        } catch (Exception e) {
            FileLog.e("GitHubUpdater: resolve failed", e);
        }
        return viewUrl;
    }

    public static void download(Activity activity, Release release, DownloadListener listener) {
        if (download != null) return;
        final String resolved = resolveDirectUrl(release.apkUrl);
        File dir = new File(activity.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            bulletin(LocaleController.getString(R.string.OEUpdateDownloadFailed), true);
            listener.onFinished(false, false);
            return;
        }
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) f.delete();
        String name = TextUtils.isEmpty(release.apkName) ? "update.apk" : release.apkName;
        File target = new File(dir, name.replaceAll("[^A-Za-z0-9._-]", "_"));
        Call call = client().newCall(new Request.Builder().url(resolved)
                .header("User-Agent", "Mozilla/5.0").build());
        download = call;
        new Thread(() -> {
            boolean ok = false;
            try (Response response = call.execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) throw new IllegalStateException("HTTP " + response.code());
                long total = body.contentLength() > 0 ? body.contentLength() : release.apkSize;
                try (InputStream input = body.byteStream(); OutputStream output = new FileOutputStream(target)) {
                    byte[] buffer = new byte[64 * 1024];
                    long done = 0;
                    int lastPercent = -1;
                    int read;
                    while ((read = input.read(buffer)) > 0) {
                        output.write(buffer, 0, read);
                        done += read;
                        if (total > 0) {
                            int percent = (int) (done * 100 / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final long doneBytes = done;
                                AndroidUtilities.runOnUIThread(() -> listener.onProgress(doneBytes, total));
                            }
                        }
                    }
                }
                ok = true;
            } catch (Exception e) {
                if (!call.isCanceled()) FileLog.e("GitHubUpdater: download failed", e);
            }
            final boolean success = ok;
            AndroidUtilities.runOnUIThread(() -> {
                download = null;
                if (success) {
                    listener.onFinished(true, false);
                    install(activity, target);
                } else {
                    target.delete();
                    listener.onFinished(false, call.isCanceled());
                    if (!call.isCanceled()) bulletin(LocaleController.getString(R.string.OEUpdateDownloadFailed), true);
                }
            });
        }, "tg-channel-download").start();
    }

    private static void install(Activity activity, File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file),
                    "application/vnd.android.package-archive");
            activity.startActivity(intent);
        } catch (Exception e) {
            FileLog.e(e);
            bulletin(LocaleController.getString(R.string.OEUpdateDownloadFailed), true);
        }
    }
}