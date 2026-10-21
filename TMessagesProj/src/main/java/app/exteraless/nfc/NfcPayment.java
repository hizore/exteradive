package app.exteraless.nfc;

import android.content.Context;
import android.content.Intent;

import org.telegram.messenger.R;

/**
 * exteradive: переход в платёжное приложение по NFC.
 *
 * Приложение берётся из системы - то, что выбрано там как приложение
 * бесконтактной оплаты (Настройки - NFC - Платежи). Свой список не
 * составляем: система и так знает про MirPay, T-Pay, Яндекс Пэй и прочие.
 */
public class NfcPayment {

    /** Настройка системы с выбранным приложением бесконтактной оплаты. */
    private static final String NFC_PAYMENT_DEFAULT_COMPONENT = "nfc_payment_default_component";
    /** Системный диалог выбора приложения бесконтактной оплаты. */
    private static final String ACTION_CHANGE_DEFAULT = "android.nfc.cardemulation.action.ACTION_CHANGE_DEFAULT";
    private static final String EXTRA_CATEGORY = "category";
    private static final String CATEGORY_PAYMENT = "payment";

    private NfcPayment() {
    }

    /** Пакет приложения, выбранного в системе платёжным. Пусто, если не выбрано. */
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
            return "";
        }
    }

    /** Название выбранного в системе приложения. */
    public static String getSystemDefaultPaymentLabel(Context context) {
        final String pkg = getSystemDefaultPaymentApp(context);
        return pkg.isEmpty() ? "" : label(context, pkg);
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
            final android.content.pm.PackageManager pm = context.getPackageManager();
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
            final android.content.pm.PackageManager pm = context.getPackageManager();
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
}
