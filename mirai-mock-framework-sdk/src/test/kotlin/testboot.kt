package com.kasukusakura.miraimockframework.boottest

import com.kasukusakura.miraimockframework.MMFSdkBoot
import com.kasukusakura.miraimockframework.maybeCreateFriend
import com.kasukusakura.miraimockframework.mmf_push_notification
import com.kasukusakura.miraimockframework.mmf_register_shutdown_callback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.mock.MockBot
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.io.File

fun main() {
    MMFSdkBoot().boot()
}

fun CoroutineScope.mmf_main(bot: MockBot) {
    mmf_register_shutdown_callback {
        println("Cleanup callback called....")
    }
    mmf_push_notification("OK")

    runBlocking {
        bot.maybeCreateFriend(12345987, "寄吸寄吸寄").says {
            append("愿风神忽悠你!")
        }
    }
    println("......")
    println("..!")
}
