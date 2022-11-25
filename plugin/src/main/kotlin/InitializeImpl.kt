@file:JvmName("InitializeImpl")
@file:Suppress("unused")

package com.kasukusakura.miraimockconsole

import com.kasukusakura.miraimockwebui.MiraiMockFrameworkConfig
import com.kasukusakura.miraimockwebui.cutAvatar
import com.kasukusakura.miraimockwebui.generateNewAvatar
import com.kasukusakura.miraimockwebui.mmf_boot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.LowLevelApi
import net.mamoe.mirai.console.util.AnsiMessageBuilder
import net.mamoe.mirai.contact.MemberPermission
import net.mamoe.mirai.mock.MockBotFactory
import net.mamoe.mirai.mock.resserver.TmpResourceServer
import net.mamoe.mirai.mock.userprofile.MockMemberInfoBuilder
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.MiraiLogger
import net.mamoe.mirai.utils.childScope
import net.mamoe.mirai.utils.info
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.lang.invoke.MethodHandles
import java.nio.file.Paths
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.imageio.ImageIO

/*
This file will be load by reflection
to avoid any classpath resolving error on different JVMs
 */
internal fun broadcast(block: AnsiMessageBuilder.() -> Unit) {
    loggerx.info {
        AnsiMessageBuilder.create(noAnsi = !ansiAvailable).apply {
            block()
        }.toString()
    }
}

internal val initimpl_mmfconf: MiraiMockFrameworkConfig get() = mmfConfig

private lateinit var mmfConfig: MiraiMockFrameworkConfig
private lateinit var loggerx: MiraiLogger
private var ansiAvailable: Boolean = false

private val random = SecureRandom()

@PublishedApi
internal fun enable0(settingsX: InitializeImplConfig) {
    val metadata = Properties().also { metadata ->
        MethodHandles.lookup().lookupClass().classLoader.getResourceAsStream("metadata.properties")!!.reader()
            .use { metadata.load(it) }
    }
    val projDir = File(metadata.getProperty("projdir"))

    loggerx = run {
        val loggerName = "#Mirai Mock For Console"
        ansiAvailable = settingsX.ansiAvailable

        if (settingsX.ansiAvailable) {
            MiraiLogger.Factory.create(
                MethodHandles.lookup().lookupClass(),
                AnsiMessageBuilder.create().apply {
                    lightYellow().append(loggerName).reset()
                }.toString()
            )
        } else {
            MiraiLogger.Factory.create(
                MethodHandles.lookup().lookupClass(),
                loggerName
            )
        }
    }

    val conf = object : MiraiMockFrameworkConfig() {

        override fun resolveRemoteLaunchRequestParentClassLoader(data: Map<String, String>): ClassLoader {
            return settingsX.resolveRemoteLaunchRequestParentClassLoader(data)
        }

    }.apply {
        loggerInitializer = { loggerx }
        frameworkKtxCoroutineScopeInit = {
            settingsX.topCoroutineContext.coroutineContext.childScope(
                "Mirai Mock Httpd", CoroutineExceptionHandler { _, throwable ->
                    if (throwable is CancellationException) return@CoroutineExceptionHandler

                    loggerx.warning(throwable)
                }
            )
        }

        resourcesDir = projDir
    }
    mmfConfig = conf
    resolveConfig(settingsX, projDir)

    mmf_boot(conf)
}

@PublishedApi
@JvmName("generateNewAvatar")
internal fun generateNewAvatar541(): ByteArray {
    ImageIO.setUseCache(false)
    val imgx = generateNewAvatar(random).cutAvatar()
    val out = ByteArrayOutputStream()
    ImageIO.write(imgx, "png", out)
    return out.toByteArray()
}

@Suppress("LocalVariableName")
private fun attachAvatarGenerator(builder: MockBotFactory.BotBuilder) {
    runCatching {
        val AvatarGenerator = Class.forName("net.mamoe.mirai.mock.utils.AvatarGenerator")
        val setAvatarGenerator = MockBotFactory.BotBuilder::class.java.getMethod("avatarGenerator", AvatarGenerator)
        val AvatarGenerator_generateNewAvatar = AvatarGenerator.getMethod("generateRandomAvatar")

        val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)

        classWriter.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC,
            "com/kasukusakura/miraimockconsole/AvatarGeneratorBind",
            null, "java/lang/Object", arrayOf(AvatarGenerator.name.replace('.', '/'))
        )
        classWriter.visitMethod(
            Opcodes.ACC_PUBLIC, "<init>", "()V", null, null
        ).let { initx ->
            initx.visitVarInsn(Opcodes.ALOAD, 0)
            initx.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            initx.visitInsn(Opcodes.RETURN)
            initx.visitMaxs(0, 0)
        }
        // public fun generateRandomAvatar(): ByteArray

        classWriter.visitMethod(
            Opcodes.ACC_PUBLIC,
            "generateRandomAvatar", "()[B", null,
            null
        ).let { mw ->
            mw.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "com/kasukusakura/miraimockconsole/InitializeImpl",
                "generateNewAvatar",
                "()[B",
                false
            )
            mw.visitInsn(Opcodes.ARETURN)
            mw.visitMaxs(0, 0)
        }

        val instx = defineClass(classWriter.toByteArray()).getConstructor().newInstance()
        AvatarGenerator_generateNewAvatar.invoke(instx)

        setAvatarGenerator.invoke(builder, instx)
    }.onFailure { it.printStackTrace() }
}

@OptIn(LowLevelApi::class)
private fun resolveConfig(settingsX: InitializeImplConfig, projDir: File) {
    val confx = projDir.resolve("plugindata/mmf-config.conf")

    if (!confx.exists()) {
        projDir.resolve("plugindata/mmf-config-default.conf").copyTo(confx)
    }

    val loader = HoconConfigurationLoader.builder()
        .path(confx.toPath())
        .build()

    val topLevelNode = loader.load()

    mmfConfig.listenHost = topLevelNode.node("httpd", "host").getString("127.0.0.1")
    mmfConfig.listenPort = topLevelNode.node("httpd", "port").getInt(21415)

    val botnode = topLevelNode.node("bot")
    var botid = botnode.node("id").long
    if (botid == 0L) botid = Integer.toUnsignedLong(Random().nextInt())
    val nick = botnode.node("nick").string ?: "Mock Bot"

    val tmpResServer: TmpResourceServer = run {
        val ptx = botnode.node("tmp-ress-loc").string ?: "<MEMORY>"
        if (ptx == "<MEMORY>") {
            TmpResourceServer.newInMemoryTmpResourceServer()
        } else {
            TmpResourceServer.of(Paths.get(ptx))
        }
    }

    mmfConfig.botInitializer = {
        MockBotFactory.newMockBotBuilder()
            .id(botid).nick(nick)
            .tmpResourceServer(tmpResServer)
            .also { attachAvatarGenerator(it) }
    }
    mmfConfig.registerToMiraiSystem = botnode.node("register-to-system").getBoolean(true)

    botnode.node("initialize-contacts").childrenList().forEach { contactConf ->
        when (val cctype = contactConf.node("type").string) {
            "friend" -> {
                mmfConfig.postInitActions.add { mb ->
                    mb.addFriend(
                        contactConf.node("id").long,
                        contactConf.node("name").stringNotNull
                    )
                }
            }

            "group" -> {
                mmfConfig.postInitActions.add { mb ->
                    val group = mb.addGroup(
                        contactConf.node("id").long,
                        contactConf.node("name").stringNotNull
                    )

                    contactConf.node("members").childrenList().forEach { subc ->
                        group.appendMember(MockMemberInfoBuilder.create {
                            uin(subc.node("id").long)
                            nick(subc.node("name").stringNotNull)
                            nameCard(subc.node("nick").string ?: "")
                            permission(
                                subc.node("perm").string?.let { MemberPermission.valueOf(it) }
                                    ?: MemberPermission.MEMBER
                            )
                        })
                    }
                    if (group.owner.id != botid) {
                        contactConf.node("botPerm").string?.let { bperms ->
                            if (bperms == "OWNER") {
                                error("${contactConf.path()}: Owner was set but bot is OWNER")
                            }
                            group.botAsMember.mockApi.permission = MemberPermission.valueOf(bperms)
                        }
                    }
                }
            }

            else -> {
                error("${contactConf.path()}: unknown type $cctype")
            }
        }
    }
}

private val ConfigurationNode.stringNotNull: String get() = string ?: error("${path()} is missing")

private fun InputStream.digest(algorithm: String): ByteArray {
    val digest = MessageDigest.getInstance(algorithm)
    digest.reset()

    use { input ->
        input.copyTo(object : OutputStream() {
            override fun write(b: Int) {
                digest.update(b.toByte())
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                digest.update(b, off, len)
            }
        })
    }

    return digest.digest()
}
