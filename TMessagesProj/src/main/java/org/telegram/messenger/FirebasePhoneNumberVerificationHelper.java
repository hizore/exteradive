package org.telegram.messenger;

import android.content.Context;

import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.pnv.VerificationSupportResult;

import org.telegram.tgnet.TLRPC;

import java.util.List;

import androidx.annotation.Nullable;

/**
 * exteradive: заглушка FirebasePhoneNumberVerificationHelper.
 * Апстрим 13.2.2 добавил silent-верификацию через firebase-pnv, но форк
 * сознательно держит firebase-messaging 22.0.0 без pnv (pnv даёт 403
 * API_KEY_ANDROID_APP_BLOCKED и ломает MLKit). Путь fpnv выключен
 * (BuildVars.SUPPORTS_FPNV = false), методы нужны только для компиляции
 * LoginActivity 13.2.2; реальная верификация идёт через SMS/passkey.
 */
public final class FirebasePhoneNumberVerificationHelper {

    public static class SimInfo {
        public String carrierName;
        public String phoneNumber;
        public int slot = -1;
    }

    private FirebasePhoneNumberVerificationHelper() {
    }

    public static SimInfo getSimInfo(Context context, VerificationSupportResult result) {
        return new SimInfo();
    }

    public static void getVerificationSupportInfo(Utilities.Callback<List<VerificationSupportResult>> callback) {
        // exteradive: pnv недоступен - сразу откатываемся на passkey/SMS.
        AndroidUtilities.runOnUIThread(() -> {
            if (callback != null) {
                callback.run(null);
            }
        });
    }

    public static Task<TLRPC.auth_Authorization> loginViaFirebasePhoneNumberVerification(
        Context context,
        int currentAccount,
        CancellationToken cancellationToken,
        @Nullable Runnable onVerifying
    ) {
        final TaskCompletionSource<TLRPC.auth_Authorization> taskSource = new TaskCompletionSource<>(cancellationToken);
        AndroidUtilities.runOnUIThread(() -> taskSource.trySetException(new IllegalStateException("fpnv is not supported in DiveLite")));
        return taskSource.getTask();
    }
}
