package com.google.firebase.pnv;

/**
 * exteradive: заглушка вместо com.google.firebase.pnv.
 * Форк держит firebase-messaging 22.0.0 без pnv (pnv даёт 403
 * API_KEY_ANDROID_APP_BLOCKED), но LoginActivity 13.2.2 импортирует
 * VerificationSupportResult. Путь fpnv выключен (BuildVars.SUPPORTS_FPNV = false),
 * поэтому реальные вызовы не происходят.
 */
public final class VerificationSupportResult {
    private VerificationSupportResult() {
    }
}
