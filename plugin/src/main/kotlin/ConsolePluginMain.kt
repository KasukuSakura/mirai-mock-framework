package com.kasukusakura.miraimockconsole

import com.kasukusakura.miraimockwebui.mmf_get_bot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.extension.PluginComponentStorage
import net.mamoe.mirai.console.plugin.PluginManager
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.utils.cast
import net.mamoe.mirai.utils.debug
import net.mamoe.mirai.utils.warning
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.net.URLClassLoader

object ConsolePluginMain : KotlinPlugin(JvmPluginDescription.loadFromResource()) {
    override fun PluginComponentStorage.onLoad() {
        // Prepare net.mamoe:mirai-core-mock:XXXX

        MethodHandles.lookup().findStatic(
            Class.forName("com.kasukusakura.miraimockconsole.InitializeBoot10086"),
            "load0",
            MethodType.methodType(
                Void.TYPE,
                PluginComponentStorage::class.java,
                JvmPluginClasspath::class.java,
                JvmPlugin::class.java
            )
        ).invokeExact(this@onLoad, jvmPluginClasspath, this@ConsolePluginMain)
    }

    override fun onEnable() {
        MethodHandles.lookup().findStatic(
            Class.forName("com.kasukusakura.miraimockconsole.InitializeBoot10086"),
            "doEnable",
            MethodType.methodType(
                Void.TYPE,
                JvmPlugin::class.java
            )
        ).invokeExact(this@ConsolePluginMain)
    }
}

@PublishedApi
internal object InitializeBoot10086 {

    @PublishedApi
    @JvmStatic
    internal fun doEnable(crtplugin: JvmPlugin) {

        val confx = object : InitializeImplConfig() {
            override val ansiAvailable: Boolean get() = MiraiConsole.isAnsiSupported

            override val topCoroutineContext: CoroutineScope get() = crtplugin

            override fun resolveRemoteLaunchRequestParentClassLoader(data: Map<String, String>): ClassLoader {
                data["console-plugin"]?.let { cid ->
                    val plugin = PluginManager.plugins.find {
                        it is AbstractJvmPlugin && it.description.id == cid
                    }
                    if (plugin == null) {
                        ConsolePluginMain.logger.warning { "Console plugin $cid not found. Fallback to default resolving mode." }
                        return@let
                    }

                    return plugin.javaClass.classLoader
                }

                return javaClass.classLoader
            }
        }


        MethodHandles.lookup().findStatic(
            Class.forName("com.kasukusakura.miraimockconsole.InitializeImpl"),
            "enable0", MethodType.methodType(Void.TYPE, InitializeImplConfig::class.java)
        ).invokeExact(confx as InitializeImplConfig)
    }

    @JvmStatic
    @PublishedApi
    internal fun load0(components: PluginComponentStorage, classpath: JvmPluginClasspath, plugin: JvmPlugin) {

        @Suppress("Since15")
        kotlin.run mockModuleLoad@{
            kotlin.runCatching {
                val proc = ProcessBuilder("git", "rev-parse", "--show-toplevel")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                val rspx = kotlin.runCatching {
                    proc.inputReader()
                }.getOrElse { proc.inputStream.reader() }
                val alldata = rspx.readText()
                if (proc.waitFor() == 0) {
                    val gitRepo = File(alldata.trim())
                    plugin.logger.debug { "Current git repo: $gitRepo" }

                    val classpathFile = "mirai-core-mock/build/tmp/buildRuntimeClasspath/classpath.txt"

                    val isMiraiRepo = listOf(
                        ".github",
                        "mirai-bom",
                        "mirai-console",
                        "mirai-core-mock",
                        "mirai-core-mock/build.gradle.kts",
                        "mirai-core-mock/build/classes/kotlin/main/net/mamoe/mirai/mock",
                        classpathFile
                    ).all { gitRepo.resolve(it).exists() }

                    if (isMiraiRepo) {
                        classpath.addToPath(
                            classpath.pluginSharedLibrariesClassLoader,
                            gitRepo.resolve("mirai-core-mock/build/classes/kotlin/main")
                        )
                        gitRepo.resolve(classpathFile).reader().useLines { libs ->
                            libs.forEach libEach@{ libx ->
                                val libFile = File(libx)
                                if (libFile.path.startsWith(gitRepo.path)) return@libEach

                                classpath.addToPath(
                                    classpath.pluginSharedLibrariesClassLoader,
                                    libFile
                                )
                            }
                        }
                        return@mockModuleLoad
                    }
                }
            }

            val mockVersion = MiraiConsole.version.toString()

            classpath.downloadAndAddToPath(
                classpath.pluginSharedLibrariesClassLoader,
                listOf("net.mamoe:mirai-core-mock:$mockVersion")
            )
        }

        components.contributePostStartupExtension {
            plugin.launch {
                delay(1000L)

                broadcast {
                    append("Broadcasting bot online events......")
                }
                mmf_get_bot().login()

                broadcast {
                    append("Open ")
                    lightYellow()
                    append("http://").append(initimpl_mmfconf.listenHost).append(":")
                    append(initimpl_mmfconf.listenPort.toString()).append("/static/main.html")
                    reset()
                    append(" to open webui.")
                }
            }
        }
    }
}
