package expo.modules.facerecognition.core

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

internal class Nv21Converter {

    companion object {
        private const val UNKNOWN = -1
        private const val INTERLEAVED = 1
        private const val PLANAR = 0
    }

    private var out: ByteArray? = null
    private var rotated: ByteArray? = null
    private var uvLayout = UNKNOWN

    fun convert(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val total = ySize + ySize / 2

        if (out == null || out!!.size != total) {
            out = ByteArray(total)
            uvLayout = UNKNOWN
        }

        val planes = image.planes

        copyLuma(
            planes[0],
            width,
            height,
            out!!
        )

        val uPlane = planes[1]
        val vPlane = planes[2]

        if (uvLayout == UNKNOWN) {
            uvLayout = if (
                isVuInterleaved(
                    uPlane,
                    vPlane,
                    width,
                    height
                )
            ) {
                INTERLEAVED
            } else {
                PLANAR
            }
        }

        if (uvLayout == INTERLEAVED) {
            copyChromaInterleaved(
                uPlane,
                vPlane,
                ySize,
                out!!
            )
        } else {
            copyChromaPlanar(
                uPlane,
                vPlane,
                width,
                height,
                ySize,
                out!!
            )
        }

        return out!!
    }

    fun rotateUpright(
        src: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int
    ): ByteArray {
        if (rotationDegrees == 0) {
            return src
        }

        if (rotated == null || rotated!!.size != src.size) {
            rotated = ByteArray(src.size)
        }

        val destination = rotated!!
        val ySize = width * height
        var i = 0

        when (rotationDegrees) {
            90 -> {
                for (x in 0 until width) {
                    for (y in height - 1 downTo 0) {
                        destination[i++] = src[y * width + x]
                    }
                }

                for (x in 0 until width step 2) {
                    for (y in height / 2 - 1 downTo 0) {
                        val s = ySize + y * width + x
                        destination[i++] = src[s]
                        destination[i++] = src[s + 1]
                    }
                }
            }

            180 -> {
                for (p in ySize - 1 downTo 0) {
                    destination[i++] = src[p]
                }

                for (p in src.size - 2 downTo ySize step 2) {
                    destination[i++] = src[p]
                    destination[i++] = src[p + 1]
                }
            }

            270 -> {
                for (x in width - 1 downTo 0) {
                    for (y in 0 until height) {
                        destination[i++] = src[y * width + x]
                    }
                }

                for (x in width - 2 downTo 0 step 2) {
                    for (y in 0 until height / 2) {
                        val s = ySize + y * width + x
                        destination[i++] = src[s]
                        destination[i++] = src[s + 1]
                    }
                }
            }

            else -> {
                for (x in width - 1 downTo 0) {
                    for (y in 0 until height) {
                        destination[i++] = src[y * width + x]
                    }
                }

                for (x in width - 2 downTo 0 step 2) {
                    for (y in 0 until height / 2) {
                        val s = ySize + y * width + x
                        destination[i++] = src[s]
                        destination[i++] = src[s + 1]
                    }
                }
            }
        }

        return destination
    }

    private fun copyLuma(
        yPlane: ImageProxy.PlaneProxy,
        width: Int,
        height: Int,
        out: ByteArray
    ) {
        val buf = yPlane.buffer
        val base = buf.position()
        val rowStride = yPlane.rowStride

        if (rowStride == width) {
            buf.get(out, 0, width * height)
        } else {
            for (row in 0 until height) {
                buf.position(base + row * rowStride)
                buf.get(
                    out,
                    row * width,
                    width
                )
            }
        }

        buf.position(base)
    }

    private fun copyChromaInterleaved(
        uPlane: ImageProxy.PlaneProxy,
        vPlane: ImageProxy.PlaneProxy,
        ySize: Int,
        out: ByteArray
    ) {
        val vBuf = vPlane.buffer
        val vPos = vBuf.position()
        val vuSize = ySize / 2
        val n = minOf(
            vBuf.remaining(),
            vuSize
        )

        vBuf.get(
            out,
            ySize,
            n
        )

        vBuf.position(vPos)

        if (n < vuSize) {
            val uBuf = uPlane.buffer
            out[ySize + vuSize - 1] =
                uBuf.get(uBuf.limit() - 1)
        }
    }

    private fun copyChromaPlanar(
        uPlane: ImageProxy.PlaneProxy,
        vPlane: ImageProxy.PlaneProxy,
        width: Int,
        height: Int,
        ySize: Int,
        out: ByteArray
    ) {
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val uBase = uBuf.position()
        val vBase = vBuf.position()

        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride

        val uPixStride = uPlane.pixelStride
        val vPixStride = vPlane.pixelStride

        var pos = ySize

        for (row in 0 until height / 2) {
            val uRow = uBase + row * uRowStride
            val vRow = vBase + row * vRowStride

            for (col in 0 until width / 2) {
                out[pos++] =
                    vBuf.get(vRow + col * vPixStride)

                out[pos++] =
                    uBuf.get(uRow + col * uPixStride)
            }
        }
    }

    private fun isVuInterleaved(
        uPlane: ImageProxy.PlaneProxy,
        vPlane: ImageProxy.PlaneProxy,
        width: Int,
        height: Int
    ): Boolean {
        if (
            uPlane.pixelStride != 2 ||
            vPlane.pixelStride != 2
        ) {
            return false
        }

        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val vPos = vBuf.position()
        val uLimit = uBuf.limit()

        vBuf.position(vPos + 1)
        uBuf.limit(uLimit - 1)

        val interleaved =
            vBuf.remaining() == (width * height / 2 - 2) &&
                vBuf.compareTo(uBuf) == 0

        vBuf.position(vPos)
        uBuf.limit(uLimit)

        return interleaved
    }
}