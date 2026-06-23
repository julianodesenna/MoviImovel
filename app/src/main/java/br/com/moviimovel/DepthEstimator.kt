package br.com.moviimovel

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class DepthEstimator(
    private val context: Context
) {

    fun gerarMapaProfundidade(bitmapOriginal: Bitmap): Bitmap {
        val interpreter = Interpreter(
            carregarModelo(),
            Interpreter.Options().apply {
                setNumThreads(4)
            }
        )

        try {
            val inputTensor = interpreter.getInputTensor(0)
            val inputShape = inputTensor.shape()

            if (inputTensor.dataType() != DataType.FLOAT32) {
                throw IllegalStateException(
                    "O modelo usa entrada ${inputTensor.dataType()}, mas este teste foi preparado para FLOAT32."
                )
            }

            if (inputShape.size != 4) {
                throw IllegalStateException(
                    "Formato de entrada inesperado: ${inputShape.contentToString()}"
                )
            }

            val entradaNchw = inputShape[1] == 3

            val inputHeight: Int
            val inputWidth: Int

            if (entradaNchw) {
                inputHeight = inputShape[2]
                inputWidth = inputShape[3]
            } else {
                inputHeight = inputShape[1]
                inputWidth = inputShape[2]
            }

            val bitmapRedimensionado = redimensionar(
                bitmap = bitmapOriginal,
                largura = inputWidth,
                altura = inputHeight
            )

            val inputBuffer = criarInputBuffer(
                bitmap = bitmapRedimensionado,
                entradaNchw = entradaNchw,
                largura = inputWidth,
                altura = inputHeight
            )

            val outputTensor = interpreter.getOutputTensor(0)
            val outputShape = outputTensor.shape()

            if (outputTensor.dataType() != DataType.FLOAT32) {
                throw IllegalStateException(
                    "O modelo usa saída ${outputTensor.dataType()}, mas este teste foi preparado para FLOAT32."
                )
            }

            val outputElements = outputShape.fold(1) { total, valor ->
                total * valor
            }

            val outputBuffer = ByteBuffer
                .allocateDirect(outputElements * 4)
                .order(ByteOrder.nativeOrder())

            interpreter.run(inputBuffer, outputBuffer)

            outputBuffer.rewind()

            val valores = FloatArray(outputElements)

            for (i in valores.indices) {
                valores[i] = outputBuffer.float
            }

            val outputSize = descobrirTamanhoSaida(outputShape)

            return converterParaBitmap(
                valores = valores,
                largura = outputSize.first,
                altura = outputSize.second
            )
        } finally {
            interpreter.close()
        }
    }

    private fun carregarModelo(): ByteBuffer {
        val descriptor: AssetFileDescriptor =
            context.assets.openFd("depth_anything_v2.tflite")

        FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
            return channel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength
            )
        }
    }

    private fun redimensionar(
        bitmap: Bitmap,
        largura: Int,
        altura: Int
    ): Bitmap {
        return Bitmap.createScaledBitmap(
            bitmap,
            largura,
            altura,
            true
        )
    }

    private fun criarInputBuffer(
        bitmap: Bitmap,
        entradaNchw: Boolean,
        largura: Int,
        altura: Int
    ): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(largura * altura * 3 * 4)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(largura * altura)
        bitmap.getPixels(
            pixels,
            0,
            largura,
            0,
            0,
            largura,
            altura
        )

        val mediaR = 0.485f
        val mediaG = 0.456f
        val mediaB = 0.406f

        val desvioR = 0.229f
        val desvioG = 0.224f
        val desvioB = 0.225f

        fun normalizar(valor: Int, media: Float, desvio: Float): Float {
            val zeroUm = valor / 255f
            return (zeroUm - media) / desvio
        }

        if (entradaNchw) {
            for (canal in 0..2) {
                for (pixel in pixels) {
                    val valor = when (canal) {
                        0 -> Color.red(pixel)
                        1 -> Color.green(pixel)
                        else -> Color.blue(pixel)
                    }

                    val normalizado = when (canal) {
                        0 -> normalizar(valor, mediaR, desvioR)
                        1 -> normalizar(valor, mediaG, desvioG)
                        else -> normalizar(valor, mediaB, desvioB)
                    }

                    buffer.putFloat(normalizado)
                }
            }
        } else {
            for (pixel in pixels) {
                buffer.putFloat(normalizar(Color.red(pixel), mediaR, desvioR))
                buffer.putFloat(normalizar(Color.green(pixel), mediaG, desvioG))
                buffer.putFloat(normalizar(Color.blue(pixel), mediaB, desvioB))
            }
        }

        buffer.rewind()
        return buffer
    }

    private fun descobrirTamanhoSaida(shape: IntArray): Pair<Int, Int> {
        return when (shape.size) {
            4 -> {
                if (shape[3] == 1) {
                    Pair(shape[2], shape[1])
                } else {
                    Pair(shape[3], shape[2])
                }
            }

            3 -> Pair(shape[2], shape[1])

            2 -> Pair(shape[1], shape[0])

            else -> throw IllegalStateException(
                "Formato de saída inesperado: ${shape.contentToString()}"
            )
        }
    }

    private fun converterParaBitmap(
        valores: FloatArray,
        largura: Int,
        altura: Int
    ): Bitmap {
        val totalPixels = largura * altura

        if (valores.size < totalPixels) {
            throw IllegalStateException(
                "A saída possui menos valores do que pixels esperados."
            )
        }

        var minimo = Float.MAX_VALUE
        var maximo = -Float.MAX_VALUE

        for (i in 0 until totalPixels) {
            val valor = valores[i]

            if (valor < minimo) minimo = valor
            if (valor > maximo) maximo = valor
        }

        val intervalo = max(0.00001f, maximo - minimo)
        val pixels = IntArray(totalPixels)

        for (i in 0 until totalPixels) {
            val normalizado = (valores[i] - minimo) / intervalo
            val invertido = 1f - normalizado
            val cinza = min(255, max(0, (invertido * 255f).toInt()))

            pixels[i] = Color.rgb(cinza, cinza, cinza)
        }

        return Bitmap.createBitmap(
            pixels,
            largura,
            altura,
            Bitmap.Config.ARGB_8888
        )
    }
}
