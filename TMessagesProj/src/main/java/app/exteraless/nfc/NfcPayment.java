package app.exteraless.nfc;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * exteradive: переход в платёжное приложение по NFC.
 *
 * Приложение берётся из системы - то, что выбрано там как приложение
 * бесконтактной оплаты (Настройки - NFC - Платежи). Если в системе выбор не
 * сделан, берётся первое приложение из системного списка платёжных.
 */
public class NfcPayment {

    /** Настройка системы с выбранным приложением бесконтактной оплаты. */
    private static final String NFC_PAYMENT_DEFAULT_COMPONENT = "nfc_payment_default_component";
    /** Системный диалог выбора приложения бесконтактной оплаты. */
    private static final String ACTION_CHANGE_DEFAULT = "android.nfc.cardemulation.action.ACTION_CHANGE_DEFAULT";
    private static final String EXTRA_CATEGORY = "category";
    private static final String CATEGORY_PAYMENT = "payment";

    /** Экшен HCE-сервиса бесконтактной оплаты. */
    private static final String ACTION_HOST_APDU_SERVICE = "android.nfc.cardemulation.action.HOST_APDU_SERVICE";
    /** Категория HCE-сервисов оплаты - так их находит система. */
    private static final String CATEGORY_PAYMENT_SERVICE = "android.nfc.cardemulation.category.PAYMENT";

    /** Схемы, которые открывают банковские и платёжные приложения. */
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

    private NfcPayment() {
    }

    /**
     * Приложение, в которое уходим по NFC.
     *
     * Сначала то, что выбрано в системе платёжным. Если в системе выбор не
     * сделан - первое приложение из системного списка бесконтактной оплаты.
     */
    public static String getPaymentApp(Context context) {
        if (context == null) {
            return "";
        }
        final String sys = getSystemDefaultPaymentApp(context);
        if (!sys.isEmpty()) {
            return sys;
        }
        final ArrayList<String> apps = getPaymentApps(context);
        return apps.isEmpty() ? "" : apps.get(0);
    }

    /** Пакет приложения, выбранного в системе платёжным. */
    public static String getSystemDefaultPaymentApp(Context context) {
        if (context == null) {
            return "";
        }
        try {
            final String value = android.provider.Settings.Secure.getString(
                    context.getContentResolver(), NFC_PAYMENT_DEFAULT_COMPONENT);
            if (value == null || value.isEmpty()) {
                return "";
            }
            final android.content.ComponentName component =
                    android.content.ComponentName.unflattenFromString(value);
            return component == null ? "" : component.getPackageName();
        } catch (Exception e) {
            FileLog.d("NFC default payment app read failed: " + e);
            return "";
        }
    }

    /** Название выбранного в системе приложения. */
    public static String getSystemDefaultPaymentLabel(Context context) {
        final String pkg = getSystemDefaultPaymentApp(context);
        return pkg.isEmpty() ? "" : label(context, pkg);
    }

    /**
     * Приложения бесконтактной оплаты из системного списка.
     *
     * Берутся те, у кого есть HCE-сервис оплаты (так их показывает системная
     * настройка NFC), плюс те, кто открывает платёжные схемы и ссылки.
     */
    public static ArrayList<String> getPaymentApps(Context context) {
        final ArrayList<String> result = new ArrayList<>();
        if (context == null) {
            return result;
        }
        final PackageManager pm = context.getPackageManager();
        final LinkedHashSet<String> packages = new LinkedHashSet<>();

        // Системный список: HCE-сервисы оплаты.
        try {
            final Intent serviceIntent = new Intent(ACTION_HOST_APDU_SERVICE);
            serviceIntent.addCategory(CATEGORY_PAYMENT_SERVICE);
            final List<ResolveInfo> services = pm.queryIntentServices(serviceIntent, 0);
            for (int i = 0; i < services.size(); i++) {
                final ResolveInfo info = services.get(i);
                if (info.serviceInfo != null && info.serviceInfo.packageName != null) {
                    packages.add(info.serviceInfo.packageName);
                }
            }
        } catch (Exception e) {
            FileLog.d("NFC HCE services query failed: " + e);
        }

        // Приложения, открывающие платёжные схемы.
        for (int i = 0; i < PAYMENT_SCHEMES.length; i++) {
            collect(pm, new Intent(Intent.ACTION_VIEW, Uri.parse(PAYMENT_SCHEMES[i] + ":")), packages);
        }
        // Приложения, открывающие платёжные ссылки.
        for (int i = 0; i < PAYMENT_LINKS.length; i++) {
            collect(pm, new Intent(Intent.ACTION_VIEW, Uri.parse(PAYMENT_LINKS[i])), packages);
        }

        for (String pkg : packages) {
            if (pkg == null || pkg.equals(context.getPackageName())) {
                continue;
            }
            result.add(pkg);
        }
        result.sort((a, b) -> label(context, a).compareToIgnoreCase(label(context, b)));
        FileLog.d("NFC payment apps found: " + result.size());
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

    /** Открывает системный выбор приложения бесконтактной оплаты. */
    public static boolean openSystemPaymentChooser(Context context) {
        if (context == null) {
            return false;
        }
        try {
            final Intent intent = new Intent(ACTION_CHANGE_DEFAULT);
            intent.putExtra(EXTRA_CATEGORY, CATEGORY_PAYMENT);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String label(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) {
            return "";
        }
        try {
            final PackageManager pm = context.getPackageManager();
            final android.content.pm.ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            final CharSequence label = pm.getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    /** Запускает приложение. false - запустить не удалось. */
    public static boolean launch(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) {
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
            FileLog.d("NFC launch failed: " + e);
            return false;
        }
    }
}
