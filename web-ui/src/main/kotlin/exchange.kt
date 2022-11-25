package com.kasukusakura.miraimockwebui

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.stream.JsonWriter
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.LowLevelApi
import net.mamoe.mirai.contact.MemberPermission
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.event.events.MessagePostSendEvent
import net.mamoe.mirai.mock.MockBot
import net.mamoe.mirai.mock.MockBotFactory
import net.mamoe.mirai.mock.MockBotFactory.Companion.newMockBotBuilder
import net.mamoe.mirai.mock.contact.MockContact
import net.mamoe.mirai.mock.contact.MockFriend
import net.mamoe.mirai.mock.contact.MockGroup
import net.mamoe.mirai.mock.resserver.TmpResourceServer
import net.mamoe.mirai.mock.userprofile.MockMemberInfoBuilder
import net.mamoe.mirai.mock.utils.broadcastMockEvents
import net.mamoe.mirai.utils.debug
import net.mamoe.mirai.utils.verbose
import java.io.IOException
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedDeque

val connections = ConcurrentLinkedDeque<WebSocketServerSession>()

val gsonx = GsonBuilder().create()


internal suspend fun pushData(data: Any) = pushMsg(
    Frame.Text(gsonx.toJson(data).also { println(it) })
)

suspend fun pushNot(msg: String) {
    logger.verbose { "[notification push] $msg" }

    pushMsg(
        Frame.Text(
            StringWriter().also { sw ->
                JsonWriter(sw).use { jw ->
                    jw.isHtmlSafe = false
                    jw.serializeNulls = false
                    jw.isLenient = false
                    jw.beginObject().kv("type", "not").kv("text", msg).endObject()
                }
            }.toString()
        )
    )
}

suspend fun pushMsg(msg: Frame) {
    connections.forEach { ws ->
        try {
            ws.send(msg.copy())
        } catch (ignored: IOException) {
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}


internal val scopex by lazy { mmfConfig.frameworkKtxCoroutineScopeInit() }

internal val logger by lazy { mmfConfig.loggerInitializer() }

internal var mmfConfig = MiraiMockFrameworkConfig()


internal var booted = false

@Synchronized
fun mmf_boot(config: MiraiMockFrameworkConfig? = null) {
    if (booted) {
        error("System booted")
    }
    booted = true
    if (config != null) {
        mmfConfig = config
    }

    mmfConfig.coroutineScope = scopex
    mmfConfig.logger = logger

    MockBotFactory.initialize()
    startListening()
    publicMockBot


    val server = embeddedServer(Netty, applicationEngineEnvironment {
        this.parentCoroutineContext = scopex.coroutineContext
        developmentMode = true
        connector {
            host = mmfConfig.listenHost
            port = mmfConfig.listenPort
        }
        module {
            install(WebSockets)
            configureRouting()
        }
    }) {
    }.start(wait = false)

    mmfConfig.server = server
}

fun mmf_get_bot(): MockBot = publicMockBot

@PublishedApi
internal fun main() {
    mmf_boot(MiraiMockFrameworkConfig().apply {
        postInitActions.add { println("Booted.") }
        botInitializer = {
            newMockBotBuilder()
                .nick("AAAAAA")
                .id(114514)
                .tmpResourceServer(TmpResourceServer.of(Paths.get("B:/mirai-mock")))
        }
    })

    runBlocking {
        @OptIn(LowLevelApi::class)
        broadcastMockEvents {
            publicMockBot.addFriend(1111, "AAAAA").says("FUKQ")

            publicMockBot.addGroup(145, "WFFF").appendMember(MockMemberInfoBuilder.create {
                nick("FOISSIW").permission(MemberPermission.OWNER)
                uin(114514)
            })
        }
    }

    runBlocking { mmfConfig.coroutineScope.coroutineContext.job.join() }
}

private fun startListening() {
    val channel = publicMockBot.eventChannel

    channel.subscribeAlways<MessageEvent> {
        logger.debug { "Evt -> $this" }
        val data = message.mfkitSerialize()

        pushMsg(Frame.Text(data))
    }

    channel.subscribeAlways<MessagePostSendEvent<*>> {
        val data = (message + receipt!!.source).mfkitSerialize()

        pushMsg(Frame.Text(data))
    }
}

private suspend fun consumeWSMessage(msg: String) {
    logger.verbose { "RECEIVE -> $msg" }
    val msgc = JsonParser.parseString(msg).asJsonObject
    when (msgc.getAsJsonPrimitive("type").asString) {
        "send-msg" -> {
            val cc = msgc.getAsJsonPrimitive("subject").asString.findByContactId()
                ?: error("No contact found by ${msgc["subject"]}")

            if (cc !is MockContact) {
                error("Not allow sending message to bot self")
            }

            val asWho = msgc["sender"].asJsonPrimitive.asLong
            if (asWho == publicMockBot.id) {
                cc.sendMessage(mfkitDecodeMessage(msgc["message"]))
            } else if (cc is MockFriend) {
                cc.says(mfkitDecodeMessage(msgc["message"]))
            } else if (cc is MockGroup) {
                val sendAs = cc[asWho] ?: error("Member $asWho not found in group $cc")
                sendAs.says(mfkitDecodeMessage(msgc["message"]))
            } else {
                error("Failed to process message $msg")
            }
        }
    }
}

private fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Completed")
            println("!")
        }
        get("/grp-avatar") {
            val nid = call.parameters["nid"]

            val nidInt64 = nid?.toLongOrNull()
            if (nidInt64 != null) {
                publicMockBot.getGroup(nidInt64)?.let {
                    call.respondRedirect(permanent = false, url = it.avatarUrl)
                    return@get
                }
            }

            call.respondRedirect(permanent = false, url = "https://p.qlogo.cn/gh/${nid}/${nid}/0")
        }
        get("/usr-avatar") {
            val nid = call.parameters["nid"]

            val nidInt64 = nid?.toLongOrNull()
            if (nidInt64 != null) {
                if (nidInt64 == publicMockBot.id) {
                    call.respondRedirect(permanent = false, url = publicMockBot.avatarUrl)
                    return@get
                }
                publicMockBot.getFriend(nidInt64)?.let {
                    call.respondRedirect(permanent = false, url = it.avatarUrl)
                    return@get
                }
            }

            call.respondRedirect(permanent = false, url = "https://q.qlogo.cn/g?b=qq&nk=${nid}&s=0")
        }
        get("/trusted-broadcast") {
            val msg = call.parameters["msg"]
            if (msg != null) {
                println(msg)
                launch { pushMsg(Frame.Text(msg)) }
                call.respondText { "Broadcasting to all...: $msg" }
            } else {
                call.respondText { "No msg" }
            }
        }

        get("/bot-init.js") {
            call.respondTextWriter(contentType = ContentType.parse("application/javascript; charset=utf8")) {
                publishBotContactLists(this)
            }
        }

        post("/usr-launch") {
            val datac = call.request.receiveChannel()
            val msize = datac.readInt()
            val mmap = HashMap<String, String>(msize)
            repeat(msize) {
                mmap[datac.readUtf8()] = datac.readUtf8()
            }
            scopex.launch {
                customlaunch(mmap)
            }

            call.respondText("Received")
        }

        static("/fontawesome") {
            staticRootFolder = mmfConfig.resourcesDir.resolve("fontawesome")
            files(".")
        }
        static("/static") {
            staticRootFolder = mmfConfig.resourcesDir.resolve("frontend")
            files(".")
        }
        webSocket("/msgc") {
            connections.add(this)
            try {
                for (frame in incoming) {
                    val text = (frame as Frame.Text).readText()
                    consumeWSMessage(text)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            connections.remove(this)
        }
    }
}

private suspend fun ByteReadChannel.readUtf8(): String {
    val size = readShort().toInt() and 0xFFFF
    val bf = ByteArray(size)
    readFully(bf)
    return Charsets.UTF_8.decode(ByteBuffer.wrap(bf)).toString()
}
