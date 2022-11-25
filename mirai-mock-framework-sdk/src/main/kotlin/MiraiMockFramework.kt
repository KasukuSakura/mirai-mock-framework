@file:JvmName("MiraiMockFramework")
@file:Suppress("UNUSED", "UNUSED_PARAMETER", "FunctionName")

package com.kasukusakura.miraimockframework

import kotlinx.coroutines.CoroutineScope
import net.mamoe.mirai.mock.MockBot

private fun intrinsic(): Nothing = throw NotImplementedError("Implemented as intrinsic")

fun mmf_mockbot(): MockBot = intrinsic()
fun mmf_coroutinescope(): CoroutineScope = intrinsic()

fun mmf_register_shutdown_callback(cb: Runnable) {
    intrinsic()
}


fun mmf_save_data(data: Map<String, String>) {
    intrinsic()
}

fun mmf_get_saved_data(): MutableMap<String, String> = intrinsic()

fun mmf_push_notification(msg: String) {
    intrinsic()
}
