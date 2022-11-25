package com.kasukusakura.miraimockconsole

import kotlinx.coroutines.CoroutineScope

abstract class InitializeImplConfig {
    open val ansiAvailable: Boolean get() = true
    abstract val topCoroutineContext: CoroutineScope

    open fun resolveRemoteLaunchRequestParentClassLoader(data: Map<String, String>): ClassLoader {
        return javaClass.classLoader
    }
}
