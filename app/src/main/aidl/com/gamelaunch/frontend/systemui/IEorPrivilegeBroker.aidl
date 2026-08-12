package com.gamelaunch.frontend.systemui;

interface IEorPrivilegeBroker {
    int setNavigationLocked(boolean locked);
    int getNavigationLockState();
    int getProtocolVersion();
    void shutdown();
}
