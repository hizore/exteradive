package org.telegram.messenger;

/**
 * exteradive (диагностика): перехват фатальных нативных сигналов.
 * Реализация — jni/oe_crash_hook.c (компилируется в libtmessages.49).
 * install() вызывается в ApplicationLoader.onCreate до любой инициализации;
 * при SIGSEGV/SIGABRT/SIGILL/SIGFPE/SIGBUS хук дописывает в файл строку
 * "NATIVE sig=.. addr=.. pc=.. lib=[base..end]" и отдаёт процесс системе,
 * поэтому tombstone/логи не страдают. pc-base даёт смещение в .so,
 * которое можно символизировать llvm-symbolizer.
 */
public final class NativeCrashHook {

    private NativeCrashHook() {
    }

    public static native void install(String path);
}
