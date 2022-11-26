package com.kasukusakura.miraimockwebui

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.kasukusakura.miraimockframework.stream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.mock.MockBot
import net.mamoe.mirai.mock.MockBotFactory
import net.mamoe.mirai.mock.contact.MockContactOrBot
import net.mamoe.mirai.mock.contact.MockFriend
import net.mamoe.mirai.mock.contact.MockGroup
import net.mamoe.mirai.mock.contact.MockMember
import net.mamoe.mirai.mock.utils.mock
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.cast
import java.io.ByteArrayOutputStream
import java.io.Writer
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

val publicMockBot by lazy { initBot() }

private fun initBot(): MockBot {
    MockBotFactory.initialize()


    val bot = mmfConfig.botInitializer().let { builder ->
        if (mmfConfig.registerToMiraiSystem) builder.create()
        else builder.createNoInstanceRegister()
    }

    val datax = ByteArrayOutputStream().also { baos ->
        ImageIO.setUseCache(false)
        ImageIO.write(generateNewAvatar(SecureRandom()).cutAvatar(), "png", baos)
    }.toByteArray()
    runBlocking {
        bot.avatarUrl = bot.tmpResourceServer.uploadResourceAsImage(
            datax.toExternalResource().toAutoCloseable()
        ).toString()
    }

    mmfConfig.postInitActions.forEach { it.accept(bot) }

    return bot
}

@Serializable
internal data class ContactId(
    val type: Int,
    val nativeId: Long,
    val nid2: Long = 0L,
) {
    companion object {
        const val BOT_SELF = 0
        const val FRIEND = 1
        const val GROUP = 2
        const val MEMBER = 3
    }

    fun serialize(): String = jsonx.encodeToString(serializer(), this)
}

private val jsonx = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
}

internal fun MockContactOrBot.contactId(): String {
    return contactId1().serialize()
}

internal fun MockContactOrBot.contactId1(): ContactId {
    return when (this) {
        is MockBot -> ContactId(ContactId.BOT_SELF, id)
        is MockFriend -> {
            if (id == publicMockBot.id) return publicMockBot.contactId1()

            ContactId(ContactId.FRIEND, id)
        }

        is MockGroup -> ContactId(ContactId.GROUP, id)
        is MockMember -> ContactId(ContactId.MEMBER, id, nid2 = group.id)
        else -> error("Unknown how to process $this")
    }
}

internal fun String.findByContactId(): ContactOrBot? {
    val cid = jsonx.decodeFromString(ContactId.serializer(), this)
    return when (cid.type) {
        ContactId.BOT_SELF -> publicMockBot
        ContactId.FRIEND -> publicMockBot.getFriend(cid.nativeId)
        ContactId.GROUP -> publicMockBot.getGroup(cid.nativeId)
        ContactId.MEMBER -> publicMockBot.getGroup(cid.nid2)?.get(cid.nativeId)
        else -> error("Unknown how to process $this")
    }
}

internal fun String.escape(): String = jsonx.encodeToString(this)

internal fun publishBotContactLists(writer: Writer) {
    writer.write("(() => { let lowlevel = mainf.lowlevel; let helper = mainf.helper; let botInfoImpl = mainf.botInfoImpl;\n")
    writer.append("botInfoImpl.nativeId=").append(publicMockBot.id.toString()).append(";\n")

    publicMockBot.friends.forEach { mfriend ->
        writer.write("lowlevel.addfriend(")
        writer.write(mfriend.contactId().escape())
        writer.append(",").append(mfriend.id.toString()).append(",").append(mfriend.nick.escape())
        writer.write(").lastSpeakTimestamp = Date.now();\n")
    }

    publicMockBot.groups.forEach { mgroup ->
        writer.append("\n((mgroup) => {\n")

        writer.append("mgroup.groupInfo.owner.nameCard = ").append(mgroup.owner.nick.escape()).append(";\n")
        mgroup.members.forEach memberLoop@{ mgm ->
            if (mgm.permission == MemberPermission.OWNER) return@memberLoop
            writer.append("mgroup.groupInfo.members[").append(mgm.id.toString()).append("]=lowlevel.createmember(")
            writer.append(mgm.id.toString()).append(", ").append(mgm.nick.escape()).append(", ").append(
                mgm.permission.ordinal.toString()
            ).append(", mgroup);\n")
        }

        writer.append("})(")

        writer.append("lowlevel.addgroup(").append(mgroup.contactId().escape()).append(", ")
        writer.append(mgroup.id.toString()).append(", ").append(mgroup.name.escape()).append(", ")
            .append(mgroup.owner.id.toString()).append("));\n\n")
    }

    writer.write("\nmainf.reloadContactList();\n")

    writer.write("})()")
}

internal val imgDB: Cache<String, Image> = CacheBuilder.newBuilder()
    .cast<CacheBuilder<String, Image>>()
    .expireAfterAccess(20, TimeUnit.MINUTES)
    .maximumSize(1024)
    .initialCapacity(128)
    .build()

internal val gson = GsonBuilder()
    .setPrettyPrinting()
    .registerTypeAdapter(ToMainMessage::class.java, object : TypeAdapter<ToMainMessage>() {
        override fun write(out: JsonWriter, value: ToMainMessage) {
            out.beginObject()

            out.name("type").value("normal")

            out.name("ids").beginArray()
            value.ids.forEach { out.value(it) }
            out.endArray()

            out.name("internalIds").beginArray()
            value.internalIds.forEach { out.value(it) }
            out.endArray()

            out.name("timestamp").value(value.timestamp)
            out.name("sender")
            when (value.sender.type) {
                ContactId.BOT_SELF -> out.value("\$bot")
                ContactId.FRIEND -> out.value(value.sender.serialize())
                ContactId.MEMBER -> out.value(value.sender.nativeId)
                else -> out.nullValue()
            }
            out.name("subject").value(value.subject.serialize())

            out.name("msg")

            gson2.getAdapter(Message::class.java).write(out, value.msg)

            out.endObject()
        }

        override fun read(`in`: JsonReader?): ToMainMessage = error("")
    })
    .registerTypeAdapter(Message::class.java, object : TypeAdapter<Message>() {
        override fun read(`in`: JsonReader?): Message = error("")

        private fun JsonWriter.writeType(type: String): JsonWriter = apply {
            beginObject().name("type").value(type)
        }

        private fun JsonWriter.imgInternal(value: Image) {
            imgDB.put(value.imageId, value)
        }

        override fun write(out: JsonWriter, value: Message) {
            write(out, value, true)
        }

        private fun write(out: JsonWriter, value: Message, fromGson: Boolean) {
            if (value is MessageChain) {
                out.beginArray()
                value.forEach { write(out, it, false) }
                out.endArray()
                return
            }

            if (value is PlainText) {
                out.writeType("plain").kv("content", value.content).endObject()
                return
            }
            if (value is At) {
                out.writeType("@").kv("target", value.target).endObject()
                return
            }
            if (value is Image) {
                out.writeType("image")
                    .kv("imgId", value.imageId)
                    .kv("url", runBlocking { value.queryUrl() })
                out.imgInternal(value)
                out.endObject()
                return
            }
            if (value is FlashImage) {
                out.writeType("flashImage")
                    .kv("imgId", value.image.imageId)
                    .kv("url", runBlocking { value.image.queryUrl() })
                out.imgInternal(value.image)
                out.endObject()
                return
            }

            if (value is ForwardMessage) {
                out.writeType("forward")
                    .kv("title", value.title)
                    .kv("brief", value.brief)
                    .kv("source", value.source)
                    .kv("summary", value.summary)
                out.name("preview").beginArray()
                value.preview.forEach { out.value(it) }
                out.endArray()

                out.name("nodeList").beginArray()

                value.nodeList.forEach { node ->
                    out.beginObject().kv("senderNativeId", node.senderId)
                        .kv("senderName", node.senderName)
                        .kv("timestamp", node.time * 1000)// ms in js
                        .name("msg")
                    write(out, node.messageChain)
                    out.endObject()
                }

                out.endArray().endObject()
            }

            if (fromGson) {
                out.nullValue()
            }
        }
    })
    .create()

internal fun JsonWriter.kv(key: String, value: String): JsonWriter = name(key).value(value)
internal fun JsonWriter.kv(key: String, value: Number): JsonWriter = name(key).value(value)

internal val gson2: Gson get() = gson

internal class ToMainMessage(
    val ids: IntArray,
    val internalIds: IntArray,
    val sender: ContactId,
    val subject: ContactId,
    val msg: MessageChain,
    val timestamp: Long,
)

internal fun MessageChain.mfkitSerialize(): String {
    val src = this.source
    return gson.toJson(
        ToMainMessage(
            ids = src.ids,
            internalIds = src.internalIds,
            sender = src.cast<OnlineMessageSource>().sender.cast<MockContactOrBot>().contactId1(),
            subject = src.subject.mock().contactId1(),
            msg = this,
            timestamp = System.currentTimeMillis(),
        )
    )
}

internal fun parseSource(subcmp: JsonObject): MessageSource? {
    val subj = subcmp["subject"].asJsonPrimitive.asString.findByContactId() ?: return null

    return publicMockBot.buildMessageSource(
        kind = when (subj) {
            is Friend -> MessageSourceKind.FRIEND
            is Group -> MessageSourceKind.GROUP
            is Member -> MessageSourceKind.TEMP
            is Stranger -> MessageSourceKind.STRANGER
            is OtherClient -> MessageSourceKind.FRIEND
            else -> error(subcmp.toString())
        }
    ) {
        this.fromId = subcmp["srcSenderNativeId"].asLong
        this.ids = subcmp["srcIds"].asJsonArray.spliterator().stream()
            .mapToInt { it.asInt }
            .toArray()

        this.internalIds = subcmp["srcInternalIds"].asJsonArray.spliterator().stream()
            .mapToInt { it.asInt }
            .toArray()

        this.time = subcmp["srcTimestamp"].asInt
        this.targetId = subj.id
        subcmp["srcMsgSourceSnapshot"]?.asString?.let { this.messages(PlainText(it)) }
    }
}

internal fun mfkitDecodeMessage(data: JsonElement): MessageChain {
    val msgc = data.asJsonArray
    val builder = MessageChainBuilder()

    msgc.forEach { subraw ->
        val subcmp = subraw.asJsonObject
        when (subcmp["type"].asString) {
            "plain" -> builder.append(subcmp["content"].asString)
            "@" -> builder.append(At(subcmp["target"].asLong))
            "image" -> {
                val imgx = imgDB.getIfPresent(subcmp["imgId"].asString)
                if (imgx != null) builder.append(imgx)
            }

            "flashImage" -> {
                val imgx = imgDB.getIfPresent(subcmp["imgId"].asString)
                if (imgx != null) builder.append(imgx.flash())
            }

            "reply" -> {
                val src = parseSource(subcmp) ?: return@forEach
                builder.append(QuoteReply(src))
            }

            "forward" -> {
                builder.append(ForwardMessage(
                    preview = subcmp["preview"]?.asJsonArray?.map { it.asString } ?: listOf(),
                    title = subcmp["title"]?.asString ?: "<TITLE>",
                    brief = subcmp["brief"]?.asString ?: "<BRIEF>",
                    source = subcmp["source"]?.asString ?: "<SOURCE>",
                    summary = subcmp["summary"]?.asString ?: "<SUMMARY>",
                    nodeList = subcmp["nodeList"].asJsonArray.asSequence().map { it.asJsonObject }.map { node ->
                        ForwardMessage.Node(
                            senderId = node["senderNativeId"].asLong,
                            time = node["timestamp"].asInt / 1000, // ms in js
                            senderName = node["senderName"].asString,
                            message = mfkitDecodeMessage(node["msg"]),
                        )
                    }.toList()
                )
                )
            }
        }
    }

    return builder.build()
}
