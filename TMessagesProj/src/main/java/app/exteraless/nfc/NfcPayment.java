package app.exteraless.nfc;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.TextUtils;

import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * exteradive: переход в платёжное приложение по NFC.
 *
 * Платёжных приложений на устройстве нет в каком-то стандартном списке, поэтому
 * собираем их сами: сначала ищем те, что умеют открывать платёжные ссылки и
 * схемы (nfc:, banking:, pay: и т.п.), а если таких нет - берём все приложения
 * с иконкой запуска, платёжные из них оказываются первыми.
 */
public class NfcPayment {

    /** Схемы, которые открывают банковские и платёжные приложения. */
    private static final String[] PAYMENT_SCHEMES = {
            "nfc", "banking", "pay", "wallet", "mirpay", "mir", "sberbank", "sber",
            "tinkoff", "tinkoffpay", "yandexpay", "yuandex", "cashback"
    };

    /** Ссылки, по которым открываются приложения бесконтактной оплаты. */
    private static final String[] PAYMENT_LINKS = {
            "https://qr.nspk.ru/",
            "https://mirpay.ru/",
            "https://www.nspk.ru/"
    };

    public static class App {
        public final String packageName;
        public final String label;
        public final boolean payment;

        public App(String packageName, String label, boolean payment) {
            this.packageName = packageName;
            this.label = label;
            this.payment = payment;
        }
    }

    private NfcPayment() {
    }

    /** Все приложения, из которых можно выбрать платёжное. Платёжные идут первыми. */
    public static ArrayList<App> getApps(Context context) {
        final PackageManager pm = context.getPackageManager();
        final LinkedHashSet<String> payment = new LinkedHashSet<>();

        for (int i = 0; i < PAYMENT_SCHEMES.length; i++) {
            collect(pm, new Intent(Intent.ACTION_VIEW, Uri.parse(PAYMENT_SCHEMES[i] + ":")), payment);
        }
        for (int i = 0; i < PAYMENT_LINKS.length; i++) {
            collect(pm, new Intent(Intent.ACTION_VIEW, Uri.parse(PAYMENT_LINKS[i])), payment);
        }

        final ArrayList<App> result = new ArrayList<>();
        final List<ApplicationInfo> installed = pm.getInstalledApplications(0);
        for (int i = 0; i < installed.size(); i++) {
            final ApplicationInfo info = installed.get(i);
            final String pkg = info.packageName;
            if (pkg == null || pkg.equals(context.getPackageName())) {
                continue;
            }
            if (pm.getLaunchIntentForPackage(pkg) == null) {
                continue;
            }
            final String label = label(context, pkg);
            result.add(new App(pkg, label, payment.contains(pkg)));
        }

        // Платёжные - первыми, остальные по названию.
        result.sort((a, b) -> {
            if (a.payment != b.payment) {
                return a.payment ? -1 : 1;
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        return result;
    }

    private static void collect(PackageManager pm, Intent intent, LinkedHashSet<String> out) {
        try {
            final List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
            for (int i = 0; i < list.size(); i++) {
                final ResolveInfo info = list.get(i);
                if (info.activityInfo != null && info.activityInfo.packageName != null) {
                    out.add(info.activityInfo.packageName);
                }
            }
        } catch (Exception ignore) {
        }
    }

    public static String label(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return "";
        }
        try {
            final PackageManager pm = context.getPackageManager();
            final ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            final CharSequence label = pm.getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    /** Запускает выбранное приложение. false - запустить не удалось. */
    public static boolean launch(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        try {
            final PackageManager pm = context.getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setPackage(packageName);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Диалог выбора платёжного приложения.
     *
     * @param current  пакет выбранного приложения, подсвечивается галочкой
     * @param callback вызывается с пакетом выбранного приложения
     */
    public static void showAppPicker(final org.telegram.ui.ActionBar.BaseFragment fragment,
                                     final String current,
                                     final java.util.function.Consumer<String> callback) {
        final android.app.Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        final ArrayList<App> apps = getApps(activity);
        if (apps.isEmpty()) {
            android.widget.Toast.makeText(activity,
                    org.telegram.messenger.LocaleController.getString(R.string.OENfcNoApps),
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i).label;
        }
        final org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(activity, fragment.getResourceProvider());
        builder.setTitle(org.telegram.messenger.LocaleController.getString(R.string.OENfcPickApp));
        builder.setItems(labels, (dialog, which) -> callback.accept(apps.get(which).packageName));
        if (!TextUtils.isEmpty(current)) {
            builder.setPositiveButton(org.telegram.messenger.LocaleController.getString(R.string.OK), (dialog, which) -> {
                for (int i = 0; i < apps.size(); i++) {
                    if (apps.get(i).packageName.equals(current)) {
                        callback.accept(current);
                        return;
                    }
                }
            });
        }
        fragment.showDialog(builder.create());
    }
}
