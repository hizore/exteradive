package org.telegram.messenger;

import android.os.SystemClock;
import android.text.TextUtils;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

public class GooglePushListenerServiceProvider implements PushListenerController.IPushListenerServiceProvider {

    public static volatile String lastError;

    private Boolean hasServices;

    public GooglePushListenerServiceProvider() {}

    @Override
    public String getLogTitle() {
        return "Google Play Services";
    }

    @Override
    public int getPushType() {
        return PushListenerController.PUSH_TYPE_FIREBASE;
    }

    private static final java.util.concurrent.atomic.AtomicInteger TOKEN_FETCH_GENERATION =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final long TOKEN_FETCH_TIMEOUT_MS = 45_000;
    private static int tokenFetchRetries;
    private static final java.util.concurrent.atomic.AtomicBoolean TOKEN_FETCH_DONE =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    // exteradive: все исходы fetch (провал / зависание / пустой токен)
    // фиксируем в mainconfig - последняя копия статуса всегда знает,
    // чем закончился последний запрос токена.
    private static void rememberTokenFetchError(String what) {
        lastError = what;
        SharedConfig.pushStringStatus = "__FIREBASE_FAILED__";
        try {
            MessagesController.getGlobalMainSettings().edit()
                    .putString("PushTokenFetchError", what)
                    .putLong("PushTokenFetchErrorTime", System.currentTimeMillis())
                    .apply();
        } catch (Throwable ignore) {}
    }

    @Override
    public void onRequestPushToken() {
        String currentPushString = SharedConfig.pushString;
        if (!TextUtils.isEmpty(currentPushString)) {
            if (BuildVars.DEBUG_PRIVATE_VERSION) {
                FileLog.d("FCM regId = " + currentPushString);
            }
        } else {
            FileLog.d("FCM Registration not found.");
        }
        Utilities.globalQueue.postRunnable(() -> startTokenFetch());
    }

    // exteradive: getToken() может молча зависнуть (стейт Firebase-installations
    // сломан неудавшейся сменой проекта) - статус висит *__GENERATING_SINCE__*
    // навсегда. Вотчдог помечает зависший запрос провалом и перезапускает
    // fetch, максимум 2 раза за запуск.
    private static void startTokenFetch() {
        final int gen = TOKEN_FETCH_GENERATION.incrementAndGet();
        TOKEN_FETCH_DONE.set(false);
        try {
            SharedConfig.pushStringGetTimeStart = SystemClock.elapsedRealtime();
            FirebaseApp.initializeApp(ApplicationLoader.applicationContext);
            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {
                        if (gen != TOKEN_FETCH_GENERATION.get()) {
                            // запрос посчитали зависшим и перезапустили,
                            // опаздывающий ответ игнорируем
                            return;
                        }
                        if (TOKEN_FETCH_DONE.getAndSet(true)) {
                            return;
                        }
                        SharedConfig.pushStringGetTimeEnd = SystemClock.elapsedRealtime();
                        tokenFetchRetries = 0;
                        if (!task.isSuccessful()) {
                            Exception error = task.getException();
                            String err = error == null ? "unknown" : error.getClass().getSimpleName() + ": " + error.getMessage();
                            rememberTokenFetchError(err);
                            FileLog.d("Failed to get regid: " + lastError);
                            android.util.Log.d("PushReg", "getToken failed: " + lastError);
                            String cached = SharedConfig.pushString;
                            if (!TextUtils.isEmpty(cached)) {
                                android.util.Log.d("PushReg", "getToken failed, trying cached token");
                                PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_FIREBASE, cached);
                            } else {
                                PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_FIREBASE, null);
                            }
                            ApplicationLoader.startPushService();
                            return;
                        }
                        lastError = null;
                        try {
                            MessagesController.getGlobalMainSettings().edit()
                                    .remove("PushTokenFetchError")
                                    .remove("PushTokenFetchErrorTime")
                                    .apply();
                        } catch (Throwable ignore) {}
                        String token = task.getResult();
                        if (!TextUtils.isEmpty(token)) {
                            android.util.Log.d("PushReg", "getToken ok len=" + token.length()
                                    + " sender=" + (token.startsWith("1:") && token.indexOf(':', 2) > 2
                                    ? token.substring(2, token.indexOf(':', 2)) : "?"));
                            PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_FIREBASE, token);
                        } else {
                            rememberTokenFetchError("empty token");
                            String cached = SharedConfig.pushString;
                            if (!TextUtils.isEmpty(cached)) {
                                android.util.Log.d("PushReg", "getToken returned empty, trying cached token");
                                PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_FIREBASE, cached);
                            } else {
                                PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_FIREBASE, null);
                            }
                            ApplicationLoader.startPushService();
                        }
                    });
        } catch (Throwable e) {
            FileLog.e(e);
            if (gen == TOKEN_FETCH_GENERATION.get() && !TOKEN_FETCH_DONE.getAndSet(true)) {
                rememberTokenFetchError(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            return;
        }
        armTokenFetchWatchdog(gen);
    }

    private static void armTokenFetchWatchdog(int gen) {
        AndroidUtilities.runOnUIThread(() -> {
            if (gen != TOKEN_FETCH_GENERATION.get()) {
                return;
            }
            if (TOKEN_FETCH_DONE.getAndSet(true)) {
                return;
            }
            rememberTokenFetchError("token fetch hung > " + TOKEN_FETCH_TIMEOUT_MS / 1000 + " s");
            FileLog.d(lastError);
            if (tokenFetchRetries++ < 2) {
                // Перезапуск: новый gen обезвечивает опаздывающий ответ старого
                Utilities.globalQueue.postRunnable(() -> startTokenFetch());
            }
        }, TOKEN_FETCH_TIMEOUT_MS);
    }

    @Override
    public boolean hasServices() {
        if (hasServices == null) {
            try {
                int resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ApplicationLoader.applicationContext);
                hasServices = resultCode == ConnectionResult.SUCCESS;
            } catch (Exception e) {
                FileLog.e(e);
                hasServices = false;
            }
        }
        return hasServices;
    }
}

