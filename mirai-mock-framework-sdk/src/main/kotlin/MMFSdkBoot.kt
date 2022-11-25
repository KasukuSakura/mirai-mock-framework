package com.kasukusakura.miraimockframework

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.lang.management.ManagementFactory
import java.net.HttpURLConnection
import java.net.URI

class MMFSdkBoot internal constructor(
    internal val arguments: MutableMap<String, String>,
) {

    fun consoleplugin(id: String): MMFSdkBoot = apply { arguments["console-plugin"] = id }

    fun mainclass(className: String): MMFSdkBoot = apply { arguments["main"] = className }

    fun classpath(classpath: String): MMFSdkBoot = apply { arguments["classpath"] = classpath }

    fun boot(host: String = "http://127.0.0.1:21415") {
        val data = ByteArrayOutputStream().also { out ->
            val dout = DataOutputStream(out)

            dout.writeInt(arguments.size)
            arguments.forEach { (k, v) ->
                dout.writeUTF(k)
                dout.writeUTF(v)
            }
        }.toByteArray()

        val uri = URI.create(host).resolve("/usr-launch")
        val connection = uri.toURL().openConnection()
        connection as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Length", data.size.toString())
        connection.outputStream.use { it.write(data) }
        connection.connect()
        connection.inputStream
        connection.disconnect()
    }
}

fun MMFSdkBoot(): MMFSdkBoot {
    val rsp = MMFSdkBoot(mutableMapOf())
    rsp.classpath(ManagementFactory.getRuntimeMXBean().classPath)
    val sst = Throwable().stackTrace
    rsp.mainclass(sst[1].className)
    return rsp
}
