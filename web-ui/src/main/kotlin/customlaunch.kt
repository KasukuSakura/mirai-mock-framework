package com.kasukusakura.miraimockwebui

import kotlinx.coroutines.*
import net.mamoe.mirai.mock.MockBot
import net.mamoe.mirai.utils.childScope
import net.mamoe.mirai.utils.debug
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.utils.recoverCatchingSuppressed
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.lang.Runnable
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Paths

internal var lastBootScope: CoroutineScope? = null
internal val savedData: MutableMap<String, String> = mutableMapOf()

private val mmfKitCCL = CustomLaunchClassLoader::class.java.classLoader

@Synchronized
internal fun customlaunch(data: Map<String, String>) {
    lastBootScope?.cancel()
    lastBootScope = null


    val classpath = data["classpath"]?.split(File.pathSeparatorChar) ?: error("Classpath not found.")
    //classpath.forEach { println(it) }
    val mainpoint = data["main"] ?: error("main not found.")

    logger.info { "Received a new launch request: $mainpoint" }

    val mmfkitClassLoader = mmfConfig.resolveRemoteLaunchRequestParentClassLoader(data)


    val ccclassloader = CustomLaunchClassLoader(mmfkitClassLoader)
    classpath.forEach { path ->
        ccclassloader.addURL0(Paths.get(path).toUri().toURL())
    }

    // define stub classes...
    val bootscope = scopex.childScope("MMF Custom Bootstrap")
    lastBootScope = bootscope

    // region
    defineMMF(ccclassloader, bootscope)
    // endregion

    val mainpointClass = Class.forName(mainpoint, false, ccclassloader)
    val lookup = MethodHandles.lookup()

    logger.debug { "Finding mmf_main from $mainpoint" }

    // (CoroutineScope, MockBot)V
    val mainpointMethodHandle: MethodHandle = runCatching {
        val met = mainpointClass.getDeclaredMethod("mmf_main", CoroutineScope::class.java, MockBot::class.java)
        met.isAccessible = true
        return@runCatching lookup.unreflect(met)
    }.recoverCatchingSuppressed {
        val met = mainpointClass.getDeclaredMethod("mmf_main", CoroutineScope::class.java)
        met.isAccessible = true
        val mh = lookup.unreflect(met)
        return@recoverCatchingSuppressed MethodHandles.dropArguments(mh, 1, MockBot::class.java)
    }.recoverCatchingSuppressed {
        val met = mainpointClass.getDeclaredMethod("mmf_main", MockBot::class.java)
        met.isAccessible = true
        val mh = lookup.unreflect(met)
        return@recoverCatchingSuppressed MethodHandles.dropArguments(mh, 0, CoroutineScope::class.java)
    }.recoverCatchingSuppressed {
        val met = mainpointClass.getDeclaredMethod("mmf_main")
        met.isAccessible = true
        val mh = lookup.unreflect(met)
        return@recoverCatchingSuppressed MethodHandles.dropArguments(
            mh,
            0,
            CoroutineScope::class.java,
            MockBot::class.java
        )
    }.getOrThrow().asType(MethodType.methodType(Void.TYPE, CoroutineScope::class.java, MockBot::class.java))

    logger.debug { "Launching....." }

    bootscope.launch {
        mainpointMethodHandle.invokeExact(lastBootScope, publicMockBot)
    }
}

private fun defineMMF(ccclassloader: CustomLaunchClassLoader, bootscope: CoroutineScope) {
    val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)

    @Suppress("LocalVariableName")
    val MMF = "com/kasukusakura/miraimockframework/MiraiMockFramework"
    writer.visit(
        Opcodes.V1_8, Opcodes.ACC_PUBLIC,
        MMF,
        null, "java/lang/Object", null,
    )

    writer.visitField(
        Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
        "cs", "Lkotlinx/coroutines/CoroutineScope;",
        null, null
    )

    writer.visitField(
        Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
        "mb", "Lnet/mamoe/mirai/mock/MockBot;",
        null, null
    )

    writer.visitField(
        Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
        "mh_data_get", "Ljava/lang/invoke/MethodHandle;",
        null, null,
    )
    writer.visitField(
        Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
        "mh_data_set", "Ljava/lang/invoke/MethodHandle;",
        null, null,
    )
    writer.visitField(
        Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
        "mmf_register_shutdown_callback", "Ljava/lang/invoke/MethodHandle;",
        null, null,
    )
    writer.visitField(
        Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
        "mmf_push_notification", "Ljava/lang/invoke/MethodHandle;",
        null, null,
    )

    writer.visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "mmf_mockbot", "()Lnet/mamoe/mirai/mock/MockBot;",
        null, null
    ).let { method ->
        method.visitFieldInsn(Opcodes.GETSTATIC, MMF, "mb", "Lnet/mamoe/mirai/mock/MockBot;")
        method.visitInsn(Opcodes.ARETURN)
        method.visitMaxs(0, 0)
    }

    writer.visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "mmf_coroutinescope", "()Lkotlinx/coroutines/CoroutineScope;",
        null, null
    ).let { method ->
        method.visitFieldInsn(Opcodes.GETSTATIC, MMF, "cs", "Lkotlinx/coroutines/CoroutineScope;")
        method.visitInsn(Opcodes.ARETURN)
        method.visitMaxs(0, 0)
    }

    writer.visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "mmf_get_saved_data", "()Ljava/util/Map;",
        null, null
    ).let { method ->
        method.visitFieldInsn(Opcodes.GETSTATIC, MMF, "mh_data_get", "Ljava/lang/invoke/MethodHandle;")
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeExact",
            "()Ljava/util/Map;", false
        )
        method.visitInsn(Opcodes.ARETURN)
        method.visitMaxs(0, 0)
    }

    writer.visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "mmf_save_data", "(Ljava/util/Map;)V",
        null, null
    ).let { method ->
        method.visitFieldInsn(Opcodes.GETSTATIC, MMF, "mh_data_set", "Ljava/lang/invoke/MethodHandle;")
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeExact",
            "(Ljava/util/Map;)V", false
        )
        method.visitInsn(Opcodes.RETURN)
        method.visitMaxs(0, 0)
    }

    writer.visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "mmf_register_shutdown_callback", "(Ljava/lang/Runnable;)V",
        null, null
    ).let { method ->
        method.visitFieldInsn(
            Opcodes.GETSTATIC,
            MMF,
            "mmf_register_shutdown_callback",
            "Ljava/lang/invoke/MethodHandle;"
        )
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeExact",
            "(Ljava/lang/Runnable;)V", false
        )
        method.visitInsn(Opcodes.RETURN)
        method.visitMaxs(0, 0)
    }

    writer.visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "mmf_push_notification", "(Ljava/lang/String;)V",
        null, null
    ).let { method ->
        method.visitFieldInsn(
            Opcodes.GETSTATIC,
            MMF,
            "mmf_push_notification",
            "Ljava/lang/invoke/MethodHandle;"
        )
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeExact",
            "(Ljava/lang/String;)V", false
        )
        method.visitInsn(Opcodes.RETURN)
        method.visitMaxs(0, 0)
    }


    val datac = ccclassloader.define(writer.toByteArray())
    val lookup = MethodHandles.lookup()

    datac.getDeclaredField("cs").let { f ->
        f.isAccessible = true
        f[null] = bootscope
    }
    datac.getDeclaredField("mb").let { f ->
        f.isAccessible = true
        f[null] = publicMockBot
    }
    datac.getDeclaredField("mh_data_get").let { f ->
        f.isAccessible = true
        f[null] = MethodHandles.constant(Map::class.java, savedData)
    }
    datac.getDeclaredField("mh_data_set").let { f ->
        f.isAccessible = true
        f[null] = lookup.findStatic(
            lookup.lookupClass(), "updateData", MethodType.methodType(Void.TYPE, Map::class.java)
        )
    }
    datac.getDeclaredField("mmf_register_shutdown_callback").let { f ->
        f.isAccessible = true

        f[null] = lookup.findStatic(
            lookup.lookupClass(),
            "registerCallback",
            MethodType.methodType(Void.TYPE, Job::class.java, Runnable::class.java)
        ).bindTo(bootscope.coroutineContext[Job]!!)
    }

    datac.getDeclaredField("mmf_push_notification").let { f ->
        f.isAccessible = true

        f[null] = lookup.findStatic(
            lookup.lookupClass(),
            "pushNotification",
            MethodType.methodType(Void.TYPE, CoroutineScope::class.java, String::class.java)
        ).bindTo(bootscope)
    }

}

@Suppress("unused")
private fun registerCallback(job: Job, callback: Runnable) {
    job.invokeOnCompletion { callback.run() }
}

@Suppress("unused")
private fun pushNotification(cs: CoroutineScope, msg: String) {
    cs.launch(start = CoroutineStart.UNDISPATCHED) { pushNot(msg) }
}

@Suppress("unused")
private fun updateData(map: Map<String, String>) {
    savedData.clear()
    savedData.putAll(map)
}

private class CustomLaunchClassLoader(parent: ClassLoader) : URLClassLoader(arrayOf(), parent) {
    fun define(code: ByteArray): Class<*> {
        return defineClass(null, code, 0, code.size)
    }

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        name ?: throw ClassNotFoundException("Class name = null")

        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
        }

        if (name.startsWith("com.kasukusakura.miraimockframework.")) {
            try {
                return mmfKitCCL.loadClass(name)
            } catch (_: ClassNotFoundException) {
            }
        }

        return super.loadClass(name, resolve)
    }

    internal fun addURL0(url: URL?) {
        addURL(url)
    }
}
