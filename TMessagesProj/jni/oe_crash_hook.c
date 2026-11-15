/*
 * exteradive (диагностика): перехват фатальных нативных сигналов.
 * Записывает "NATIVE sig=.. addr=.. pc=.." в файл приложения, затем
 * возвращает управление процессу (SA_RESETHAND + повторный fault) —
 * системный tombstone генерируется как обычно.
 */
#include <jni.h>
#include <signal.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <ucontext.h>
#include <time.h>

static char g_path[256];
static unsigned long g_lib_base = 0;
static unsigned long g_lib_end = 0;

static unsigned long oe_context_pc(void *uctx) {
#if defined(__aarch64__)
    ucontext_t *uc = (ucontext_t *) uctx;
    return (unsigned long) uc->uc_mcontext.pc;
#elif defined(__arm__)
    ucontext_t *uc = (ucontext_t *) uctx;
    return (unsigned long) uc->uc_mcontext.arm_pc;
#else
    (void) uctx;
    return 0;
#endif
}

static void oe_write_handler(int sig, siginfo_t *info, void *uctx) {
    int fd = open(g_path, O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (fd >= 0) {
        char buf[192];
        unsigned long pc = oe_context_pc(uctx);
        unsigned long addr = info ? (unsigned long) info->si_addr : 0;
        long long ts = (long long) time(NULL);
        int n = snprintf(buf, sizeof(buf),
                         "NATIVE sig=%d addr=%lx pc=%lx lib=[%lx..%lx] ts=%lld\n",
                         sig, addr, pc, g_lib_base, g_lib_end, ts);
        if (n > 0) {
            ssize_t ignored = write(fd, buf, (size_t) n);
            (void) ignored;
        }
        close(fd);
    }
    /* SA_RESETHAND снял хендлер; возврат из обработчика повторит fault
       с дефолтной диспетчеризацией — Android запишет tombstone. */
}

static void oe_scan_maps(void) {
    FILE *f = fopen("/proc/self/maps", "r");
    if (f == NULL) {
        return;
    }
    char line[512];
    while (fgets(line, sizeof(line), f) != NULL) {
        if (strstr(line, "libtmessages") != NULL && strstr(line, ".so") != NULL) {
            unsigned long start = 0, end = 0;
            if (sscanf(line, "%lx-%lx", &start, &end) == 2) {
                if (g_lib_base == 0 || start < g_lib_base) {
                    g_lib_base = start;
                }
                if (end > g_lib_end) {
                    g_lib_end = end;
                }
            }
        }
    }
    fclose(f);
}

JNIEXPORT void JNICALL
Java_org_telegram_messenger_NativeCrashHook_install(JNIEnv *env, jclass clazz, jstring path) {
    (void) clazz;
    if (path == NULL) {
        return;
    }
    const char *chars = (*env)->GetStringUTFChars(env, path, NULL);
    if (chars == NULL) {
        return;
    }
    strncpy(g_path, chars, sizeof(g_path) - 1);
    g_path[sizeof(g_path) - 1] = '\0';
    (*env)->ReleaseStringUTFChars(env, path, chars);

    if (g_lib_base == 0) {
        oe_scan_maps();
    }

    int signals[5] = { SIGSEGV, SIGBUS, SIGABRT, SIGILL, SIGFPE };
    for (int i = 0; i < 5; i++) {
        struct sigaction sa;
        memset(&sa, 0, sizeof(sa));
        sa.sa_sigaction = oe_write_handler;
        sa.sa_flags = SA_SIGINFO | SA_RESETHAND | SA_NODEFER | SA_ONSTACK;
        sigemptyset(&sa.sa_mask);
        sigaction(signals[i], &sa, NULL);
    }

    /* маркер: файл создан, хук установлен */
    int fd = open(g_path, O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (fd >= 0) {
        const char *msg = "HOOK-INSTALLED\n";
        ssize_t ignored = write(fd, msg, strlen(msg));
        (void) ignored;
        close(fd);
    }
}
