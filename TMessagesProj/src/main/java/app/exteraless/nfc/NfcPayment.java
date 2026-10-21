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

    /**
     * Схемы, которые открывают банковские и платёжные приложения.
     * Только платёжные - чтобы в списке не оказалось мессенджеров и маркетов.
     */
    private static final String[] PAYMENT_SCHEMES = {
            "nfc", "banking", "pay", "wallet", "mirpay", "sberbank", "tinkoff",
            "tinkoffpay", "yandexpay", "yuandexpay"
    };

    /** Ссылки, по которым открываются приложения бесконтактной оплаты. */
    private static final String[] PAYMENT_LINKS = {
            "https://qr.nspk.ru/",
            "https://mirpay.ru/",
            "https://www.nspk.ru/"
    };

    /** Экшен HCE-сервиса бесконтактной оплаты. */
    private static final String ACTION_HOST_APDU_SERVICE = "android.nfc.cardemulation.action.HOST_APDU_SERVICE";
    /** Категория HCE-сервисов оплаты - так их находит система. */
    private static final String CATEGORY_PAYMENT = "android.nfc.cardemulation.category.PAYMENT";

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

    /**
     * Платёжные приложения, установленные на устройстве.
     *
     * Список берётся из системы: это приложения, у которых есть HCE-сервис
     * бесконтактной оплаты (так их показывает системная настройка NFC),
     * плюс те, кто умеет открывать платёжные схемы и ссылки. Список всех
     * установленных приложений не используется - туда попадает всё подряд.
     */
    public static ArrayList<App> getApps(Context context) {
        final PackageManager pm = context.getPackageManager();
        final LinkedHashSet<String> packages = new LinkedHashSet<>();

        // Системный список: HCE-сервисы оплаты.
        try {
            final Intent serviceIntent = new Intent(ACTION_HOST_APDU_SERVICE);
            serviceIntent.addCategory(CATEGORY_PAYMENT);
            final List<ResolveInfo> services = pm.queryIntentServices(serviceIntent, 0);
            for (int i = 0; i < services.size(); i++) {
                final ResolveInfo info = services.get(i);
                if (info.serviceInfo != null && info.serviceInfo.packageName != null) {
                    packages.add(info.serviceInfo.packageName);
                }
            }
        } catch (Exception ignore) {
        }
        // Тот же список, но напрямую из системного CardEmulationManager.
        collectSystemServices(context, packages);

        // Приложения, открывающие платёжные схемы.
        for (int i = 0; i < PAYMENT_SCHEMES.length; i++) {
            collect(pm, new Intent(Intent.ACTION_VIEW, Uri.parse(PAYMENT_SCHEMES[i] + ":")), packages);
        }
        // Приложения, открывающие платёжные ссылки.
        for (int i = 0; i < PAYMENT_LINKS.length; i++) {
            collect(pm, new Intent(Intent.ACTION_VIEW, Uri.parse(PAYMENT_LINKS[i])), packages);
        }
        // Платёжное приложение по умолчанию из настроек системы.
        final String defaultApp = getSystemDefaultPaymentApp(context);
        if (!defaultApp.isEmpty()) {
            packages.add(defaultApp);
        }

        final ArrayList<App> result = new ArrayList<>();
        for (String pkg : packages) {
            if (pkg == null || pkg.equals(context.getPackageName())) {
                continue;
            }
            result.add(new App(pkg, label(context, pkg), true));
        }
        result.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        org.telegram.messenger.FileLog.d("NFC payment apps found: " + result.size());
        return result;
    }

    /**
     * Список приложений бесконтактной оплаты прямо из системного
     * CardEmulationManager - так его получает системная настройка NFC.
     * API скрытое, поэтому через reflection; если недоступно - просто ничего.
     */
    private static void collectSystemServices(Context context, LinkedHashSet<String> out) {
        Object manager = null;
        try {
            manager = context.getSystemService("cardemulation");
        } catch (Throwable ignore) {
        }
        if (manager == null) {
            return;
        }
        try {
            for (java.lang.reflect.Method method : manager.getClass().getMethods()) {
                if (!"getServices".equals(method.getName())) {
                    continue;
                }
                final Class<?>[] types = method.getParameterTypes();
                final Object[] args;
                if (types.length == 2 && types[0] == String.class && types[1] == int.class) {
                    args = new Object[]{CATEGORY_PAYMENT, 0};
                } else if (types.length == 2 && types[0] == int.class && types[1] == int.class) {
                    args = new Object[]{0, 0};
                } else if (types.length == 1 && types[0] == String.class) {
                    args = new Object[]{CATEGORY_PAYMENT};
                } else {
                    continue;
                }
                final Object result = method.invoke(manager, args);
                if (!(result instanceof List)) {
                    continue;
                }
                for (Object item : (List<?>) result) {
                    if (item == null) {
                        continue;
                    }
                    try {
                        final Object component = item.getClass().getMethod("getComponent").invoke(item);
                        if (component instanceof android.content.ComponentName) {
                            out.add(((android.content.ComponentName) component).getPackageName());
                        }
                    } catch (Throwable ignore) {
                    }
                }
                return;
            }
        } catch (Throwable ignore) {
        }
    }

    /** Платёжное приложение по умолчанию из системных настроек. */
    public static String getSystemDefaultPaymentApp(Context context) {        try {
            final String value = android.provider.Settings.Secure.getString(
                    context.getContentResolver(), "nfc_payment_default_component");
            if (value == null || value.isEmpty()) {
                return "";
            }
            final android.content.ComponentName component =
                    android.content.ComponentName.unflattenFromString(value);
            return component == null ? "" : component.getPackageName();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Подхватывает выбор, сделанный в системном диалоге: если приложение
     * ещё не выбрано, берём то, что система считает платёжным по умолчанию.
     */
    public static void syncSystemDefault(Context context) {
        if (context == null) {
            return;
        }
        final String current = org.telegram.messenger.SharedConfig.nfcPaymentApp;
        if (current != null && !current.isEmpty()) {
            return;
        }
        final String sys = getSystemDefaultPaymentApp(context);
        if (!sys.isEmpty()) {
            org.telegram.messenger.SharedConfig.setNfcPaymentApp(sys);
        }
    }

    /**
     * Системный выбор приложения бесконтактной оплаты.
     * Список там всегда настоящий - так он и нужен, если свой не нашёлся.
     */
    public static boolean openSystemPaymentChooser(Context context) {
        try {
            final Intent intent = new Intent("android.nfc.cardemulation.action.ACTION_CHANGE_DEFAULT");
            intent.putExtra("category", "payment");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
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
            // своего списка не нашлось - предлагаем системный выбор
            if (openSystemPaymentChooser(activity)) {
                return;
            }
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
