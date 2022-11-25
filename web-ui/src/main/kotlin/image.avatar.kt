package com.kasukusakura.miraimockwebui

import java.awt.image.BufferedImage
import java.io.File
import java.security.SecureRandom
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.min

internal class IntArrayStack {
    private var buffer: IntArray = IntArray(2048)

    @JvmField
    var len: Int = 0

    fun push(value: Int) {
        if (len == buffer.size) {
            buffer = buffer.copyOf(len + 2048)
        }
        buffer[len++] = value
    }

    fun pop(): Int = buffer[--len]
}

internal fun floodFill(
    img: BufferedImage,
    processed: BitSet,
    pos3x: IntArrayStack,
    x: Int, y: Int,
    rgb: Int,
) {
    val width = img.width
    val wid1 = width - 1
    val hei1 = img.height - 1

    pos3x.push((y * width) + x)

    val rgb255 = rgb or 0xFF000000.toInt()

    val srccolor = img.getRGB(x, y) and 0xFFFFFF

    val sr = srccolor shr 16
    val sg = srccolor shr 8 and 0xFF
    val sb = srccolor and 0xFF

    while (pos3x.len > 0) {
        val pos = pos3x.pop()
        if (processed[pos]) continue
        processed.set(pos)

        val px = pos % width
        val py = pos / width

        val pixel = img.getRGB(px, py) and 0xFFFFFF

        val pr = pixel shr 16
        val pg = pixel shr 8 and 0xFF
        val pb = pixel and 0xFF

        if (abs(sr - pr) > 20) continue
        if (abs(sg - pg) > 20) continue
        if (abs(sb - pb) > 20) continue

        img.setRGB(px, py, rgb255)

        if (px != 0) pos3x.push(pos - 1)
        if (px != wid1) pos3x.push(pos + 1)
        if (py != 0) pos3x.push(pos - width)
        if (py != hei1) pos3x.push(pos + width)
    }
}

internal val avatarTemplate by lazy {
    ImageIO.setUseCache(false)
    ImageIO.read(mmfConfig.resourcesDir.resolve("frontend/imgs/4map.png"))
}

fun generateNewAvatar(random: Random = Random()): BufferedImage {
    // int: 32 bit
    // rgb: 3byte = 24bit
    // 4color = 12byte = 96 bit = 3int

    val color1 = random.nextInt()
    val color2 = random.nextInt()
    val color3 = random.nextInt()

    val color4 = (0
            or (color1 shr 8 and 0xFF0000)
            or (color2 shr 16 and 0x00FF00)
            or (color3 shr 24 and 0x0000FF)
            )

    return generateNewAvatar(color1, color2, color3, color4)
}

fun generateNewAvatar(
    color1: Int, color2: Int, color3: Int, color4: Int,
): BufferedImage {
    val imgex = BufferedImage(avatarTemplate.width, avatarTemplate.height, BufferedImage.TYPE_INT_RGB)
    imgex.createGraphics().let { grp ->
        grp.drawImage(avatarTemplate, 0, 0, null)
        grp.dispose()
    }
    val buf2 = BitSet(imgex.width * imgex.height)
    val tmpxbuf = IntArrayStack()


    floodFill(imgex, buf2, tmpxbuf, 221, 303, color1)
    floodFill(imgex, buf2, tmpxbuf, 495, 661, color1)
    floodFill(imgex, buf2, tmpxbuf, 791, 431, color1)
    floodFill(imgex, buf2, tmpxbuf, 261, 713, color1)
    floodFill(imgex, buf2, tmpxbuf, 166, 837, color1)
    floodFill(imgex, buf2, tmpxbuf, 834, 554, color1)


    floodFill(imgex, buf2, tmpxbuf, 215, 107, color2)
    floodFill(imgex, buf2, tmpxbuf, 358, 190, color2)
    floodFill(imgex, buf2, tmpxbuf, 522, 231, color2)
    floodFill(imgex, buf2, tmpxbuf, 694, 490, color2)
    floodFill(imgex, buf2, tmpxbuf, 669, 990, color2)
    floodFill(imgex, buf2, tmpxbuf, 105, 985, color2)
    floodFill(imgex, buf2, tmpxbuf, 126, 765, color2)
    floodFill(imgex, buf2, tmpxbuf, 201, 634, color2)


    floodFill(imgex, buf2, tmpxbuf, 393, 125, color3)
    floodFill(imgex, buf2, tmpxbuf, 394, 445, color3)
    floodFill(imgex, buf2, tmpxbuf, 518, 489, color3)
    floodFill(imgex, buf2, tmpxbuf, 303, 591, color3)
    floodFill(imgex, buf2, tmpxbuf, 102, 871, color3)
    floodFill(imgex, buf2, tmpxbuf, 504, 926, color3)
    floodFill(imgex, buf2, tmpxbuf, 504, 926, color3)
    floodFill(imgex, buf2, tmpxbuf, 790, 782, color3)
    floodFill(imgex, buf2, tmpxbuf, 329, 702, color3)
    floodFill(imgex, buf2, tmpxbuf, 493, 1111, color3)

    floodFill(imgex, buf2, tmpxbuf, 808, 136, color4)
    floodFill(imgex, buf2, tmpxbuf, 368, 312, color4)
    floodFill(imgex, buf2, tmpxbuf, 258, 404, color4)
    floodFill(imgex, buf2, tmpxbuf, 346, 539, color4)
    floodFill(imgex, buf2, tmpxbuf, 624, 404, color4)
    floodFill(imgex, buf2, tmpxbuf, 807, 540, color4)
    floodFill(imgex, buf2, tmpxbuf, 723, 590, color4)
    floodFill(imgex, buf2, tmpxbuf, 112, 718, color4)
    floodFill(imgex, buf2, tmpxbuf, 315, 679, color4)
    floodFill(imgex, buf2, tmpxbuf, 187, 757, color4)
    floodFill(imgex, buf2, tmpxbuf, 369, 926, color4)

    return imgex
}

fun BufferedImage.cutAvatar(): BufferedImage {
    val wid5x = min(width, height)

    val px = (width - wid5x) / 2
    val py = (height - wid5x) / 2

    val newImg = BufferedImage(wid5x, wid5x, BufferedImage.TYPE_INT_RGB)
    val grap = newImg.createGraphics()
    grap.drawImage(this, 0, 0, wid5x, wid5x, px, py, px + wid5x, py + wid5x, null)
    grap.dispose()
    return newImg
}


internal fun main() {
    ImageIO.write(generateNewAvatar(SecureRandom()).cutAvatar(), "png", File("B:/generated-avatar.png"))
}
