package com.kasukusakura.miraimockwebui

import io.ktor.server.engine.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.mamoe.mirai.Bot
import net.mamoe.mirai.mock.MockBot
import net.mamoe.mirai.mock.MockBotFactory
import net.mamoe.mirai.utils.MiraiLogger
import java.io.File
import java.util.*
import java.util.function.Consumer

open class MiraiMockFrameworkConfig {
    @JvmField
    var listenHost: String = "127.0.0.1"

    @JvmField
    var listenPort: Int = 21415

    @JvmField
    var botInitializer: () -> MockBotFactory.BotBuilder = {
        MockBotFactory.newMockBotBuilder()
            .id(Integer.toUnsignedLong(Random().nextInt()))
            .nick("Mock Bot")
    }

    /**
     * 如果为 `true`, 创建的 bot 可以通过 [Bot.getInstance] 获取, 否则该方法执行时不会得到 [MockBot] 实例
     */
    @JvmField
    var registerToMiraiSystem: Boolean = true

    @JvmField
    var postInitActions: MutableList<Consumer<MockBot>> = mutableListOf()

    @JvmField
    var frameworkKtxCoroutineScopeInit: () -> CoroutineScope = {
        CoroutineScope(Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            throwable.printStackTrace()
        })
    }

    @JvmField
    var resourcesDir: File = File(".")

    @JvmField
    var loggerInitializer: () -> MiraiLogger =
        { MiraiLogger.Factory.create(MiraiMockFrameworkConfig::class.java, "mirai-mock-framework") }

    lateinit var server: ApplicationEngine
    lateinit var coroutineScope: CoroutineScope
    lateinit var logger: MiraiLogger

    open fun resolveRemoteLaunchRequestParentClassLoader(data: Map<String, String>): ClassLoader {
        return javaClass.classLoader
    }
}
