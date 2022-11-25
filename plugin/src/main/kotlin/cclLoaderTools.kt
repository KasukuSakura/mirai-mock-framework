package com.kasukusakura.miraimockconsole

import com.kasukusakura.miraimockframework.recoverCatchingSuppressed
import java.lang.invoke.MethodHandles

private class DynCCL : ClassLoader(MethodHandles.lookup().lookupClass().classLoader) {
    fun define(code: ByteArray): Class<*> = defineClass(null, code, 0, code.size)
}

private val dynCCLInst by lazy { DynCCL() }

@Suppress("Since15")
internal fun defineClass(code: ByteArray): Class<*> = runCatching {
    MethodHandles.lookup().defineClass(code)
}.recoverCatchingSuppressed {
    dynCCLInst.define(code)
}.getOrThrow()

