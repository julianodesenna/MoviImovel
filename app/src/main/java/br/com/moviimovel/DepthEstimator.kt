package br.com.moviimovel

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class DepthResult(
    val visualMap: Bitmap,
    val width: Int,
    val height: Int,
    val nearValues: FloatArray
)

class DepthEstimator(
    private val context: Context
) {

    fun gerarProfundidade(bitmapOriginal: Bitmap): DepthResult {
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
                    "Entrada do modelo incompatível: ${inputTensor.dataType()}"
                )
            }

            if (inputShape.size != 4) {
                throw IllegalStateException(
                    "Formato de entrada inesperado: ${inputShape.contentToString()}"
                )
            }

            val entradaNchw = inputShape[1] == 3

            val inputHeight = if (entradaNchw) {
                inputShape[2]
            } else {
                inputShape[1]
            }

            val inputWidth = if (entradaNchw) {
                inputShape[3]
            } else {
                inputShape[2]
            }

            val bitmapSoftware = converterParaBitmapSoftware(bitmapOriginal)

            val bitmapRedimensionado = Bitmap.createScaledBitmap(
                bitmapSoftware,
                inputWidth,
                inputHeight,
                true
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
                    "Saída do modelo incompatível: ${outputTensor.dataType()}"
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

            val tamanhoSaida = descobrirTamanhoSaida(outputShape)

            return criarResultadoProfundidade(
                valores = valores,
                largura = tamanhoSaida.first,
                altura = tamanhoSaida.second
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

    private fun converterParaBitmapSoftware(
        bitmapOriginal: Bitmap
    ): Bitmap {
        val bitmapSoftware = Bitmap.createBitmap(
            bitmapOriginal.width,
            bitmapOriginal.height,
            Bitmap.Config.ARGB_8888
        )

        android.graphics.Canvas(bitmapSoftware).drawBitmap(
            bitmapOriginal,
            0f,
            0f,
            null
        )

        return bitmapSoftware
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

        fun normalizar(
            valor: Int,
            media: Float,
            desvio: Float
        ): Float {
            return ((valor / 255f) - media) / desvio
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
                        0 -> normalizar(valor, 0.485f, 0.229f)
                        1 -> normalizar(valor, 0.456f, 0.224f)
                        else -> normalizar(valor, 0.406f, 0.225f)
                    }

                    buffer.putFloat(normalizado)
                }
            }
        } else {
            for (pixel in pixels) {
                buffer.putFloat(
                    normalizar(Color.red(pixel), 0.485f, 0.229f)
                )

                buffer.putFloat(
                    normalizar(Color.green(pixel), 0.456f, 0.224f)
                )

                buffer.putFloat(
                    normalizar(Color.blue(pixel), 0.406f, 0.225f)
                )
            }
        }

        buffer.rewind()

        return buffer
    }

    private fun descobrirTamanhoSaida(
        shape: IntArray
    ): Pair<Int, Int> {
        return when (shape.size) {
            4 -> when {
                shape[1] == 1 -> Pair(shape[3], shape[2])
                shape[3] == 1 -> Pair(shape[2], shape[1])
                else -> Pair(shape[3], shape[2])
            }

            3 -> Pair(shape[2], shape[1])
            2 -> Pair(shape[1], shape[0])

            else -> throw IllegalStateException(
                "Formato de saída inesperado: ${shape.contentToString()}"
            )
        }
    }

    private fun criarResultadoProfundidade(
        valores: FloatArray,
        largura: Int,
        altura: Int
    ): DepthResult {
        val totalPixels = largura * altura

        if (valores.size < totalPixels) {
            throw IllegalStateException(
                "Saída inválida: menos valores que pixels."
            )
        }

        var minimo = Float.MAX_VALUE
        var maximo = -Float.MAX_VALUE

        for (i in 0 until totalPixels) {
            if (valores[i] < minimo) minimo = valores[i]
            if (valores[i] > maximo) maximo = valores[i]
        }

        val intervalo = max(0.00001f, maximo - minimo)

        val nearValues = FloatArray(totalPixels)
        val pixels = IntArray(totalPixels)

        for (i in 0 until totalPixels) {
            val normalizado = (valores[i] - minimo) / intervalo

            /*
             * Branco = mais perto.
             * Preto = mais longe.
             */
            val perto = 1f - normalizado

            nearValues[i] = perto

            val cinza = min(
                255,
                max(
                    0,
                    (perto * 255f).toInt()
                )
            )

            pixels[i] = Color.rgb(cinza, cinza, cinza)
        }

        val visualMap = Bitmap.createBitmap(
            pixels,
            largura,
            altura,
            Bitmap.Config.ARGB_8888
        )

        return DepthResult(
            visualMap = visualMap,
            width = largura,
            height = altura,
            nearValues = nearValues
        )
    }
}
