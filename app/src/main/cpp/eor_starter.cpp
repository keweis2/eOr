/* Copyright 2017-2025 Rikka contributors; 2026 eOr contributors. Apache-2.0. */
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>
#include <sys/types.h>

// Executed by adb shell. Arguments are validated again by EmbeddedBrokerMain.
int main(int argc, char **argv) {
    if (getuid() != 2000 || argc != 6) return 2;
    if (strncmp(argv[1], "/data/app/", 10) || !strstr(argv[1], ".apk")) return 3;
    pid_t pid = fork();
    if (pid < 0) return 4;
    if (pid > 0) return 0;
    setsid();
    int fd = open("/dev/null", O_RDWR);
    if (fd >= 0) {
        dup2(fd, 0);
        dup2(fd, 1);
        dup2(fd, 2);
        if (fd > 2) close(fd);
    }
    setenv("CLASSPATH", argv[1], 1);
    execl("/system/bin/app_process", "app_process", "/system/bin",
          "--nice-name=eor_privilege_broker",
          "com.gamelaunch.frontend.systemui.EmbeddedBrokerMain",
          argv[1], argv[2], argv[3], argv[4], argv[5], nullptr);
    return 5;
}
