package com.gamelaunch.frontend.domain.system

interface BootCountProvider {
    fun currentBootCount(): Int
}
