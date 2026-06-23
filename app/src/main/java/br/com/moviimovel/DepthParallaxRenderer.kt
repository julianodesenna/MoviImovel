package br.com.moviimovel

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

class DepthParallaxRenderer {

    private var bitmapGuardado: Bitmap? = null
    private var pixelsOriginais = IntArray(0)
    private var larguraOriginal = 0
    private var alturaOriginal = 0

    fun renderizar(
        bitmapOriginal: Bitmap,
        depthResult: DepthResult,
        modo: String,
        progresso: Float,
        larguraSaida: Int,
        alturaSaida: Int
    ): Bitmap {
        prepararBitmap(bitmapOriginal)

        val largura = max(1, larguraSaida)
        val altura = max(1, alturaSaida)

        val pixelsSaida = IntArray(largura * altura)

        val escalaPreenchimento = max(
            largura.toFloat() / larguraOriginal.toFloat(),
            altura.toFloat() / alturaOriginal.toFloat()
        )

        val larguraExibida = larguraOriginal * escalaPreenchimento
        val alturaExibida = alturaOriginal * escalaPreenchimento

        val corteX = (larguraExibida - largura) / 2f
        val corteY = (alturaExibida - altura) / 2f

        val movimento = calcularMovimento(
            modo = modo,
            progresso = progresso,
            largura = largura,
            altura = altura
        )

        val centroX = largura / 2f
        val centroY = altura / 2f

        for (y in 0 until altura) {
            for (x in 0 until largura) {
                val u = x.toFloat() / largura.toFloat()
                val v = y.toFloat() / altura.toFloat()

                val profundidade = profundidadeNoPonto(
                    depthResult = depthResult,
                    u = u,
                    v = v
                )

                /*
                 * Partes próximas expandem e se deslocam mais.
                 * Fundo se desloca pouco.
                 */
                val intensidade = 0.18f + (profundidade * 0.82f)

                val fatorZoom = 1f + (
                    movimento.avanco * profundidade
                )

                val destinoRelativoX = x - centroX
                val destinoRelativoY = y - centroY

                val origemNaTelaX = centroX + (
                    destinoRelativoX / fatorZoom
                ) - (movimento.deslocamentoX * intensidade)

                val origemNaTelaY = centroY + (
                    destinoRelativoY / fatorZoom
                ) - (movimento.deslocamentoY * intensidade)

                val origemOriginalX = (
                    origemNaTelaX + corteX
                ) / escalaPreenchimento

                val origemOriginalY = (
                    origemNaTelaY + corteY
                ) / escalaPreenchimento

                pixelsSaida[(y * largura) + x] = amostrarPixel(
                    x = origemOriginalX,
                    y = origemOriginalY
                )
            }
        }

        return Bitmap.createBitmap(
            pixelsSaida,
            largura,
            altura,
            Bitmap.Config.ARGB_8888
        )
    }

    private fun prepararBitmap(
        bitmap: Bitmap
    ) {
        if (bitmapGuardado === bitmap) {
            return
        }

        bitmapGuardado = bitmap
        larguraOriginal = bitmap.width
        alturaOriginal = bitmap.height

        pixelsOriginais = IntArray(
            larguraOriginal * alturaOriginal
        )

        bitmap.getPixels(
            pixelsOriginais,
            0,
            larguraOriginal,
            0,
            0,
            larguraOriginal,
            alturaOriginal
        )
    }

    private fun amostrarPixel(
        x: Float,
        y: Float
    ): Int {
        val xSeguro = min(
            larguraOriginal - 1,
            max(0, x.toInt())
        )

        val ySeguro = min(
            alturaOriginal - 1,
            max(0, y.toInt())
        )

        return pixelsOriginais[
            (ySeguro * larguraOriginal) + xSeguro
        ]
    }

    private fun profundidadeNoPonto(
        depthResult: DepthResult,
        u: Float,
        v: Float
    ): Float {
        val x = min(
            depthResult.width - 1,
            max(
                0,
                (u * (depthResult.width - 1)).toInt()
            )
        )

        val y = min(
            depthResult.height - 1,
            max(
                0,
                (v * (depthResult.height - 1)).toInt()
            )
        )

        return depthResult.nearValues[
            (y * depthResult.width) + x
        ]
    }

    private fun calcularMovimento(
        modo: String,
        progresso: Float,
        largura: Int,
        altura: Int
    ): Movimento3D {
        return when (modo) {
            "Pan Profundo" -> {
                Movimento3D(
                    avanco = 0.045f,
                    deslocamentoX = (
                        -largura * 0.035f
                    ) + (
                        progresso * largura * 0.070f
                    ),
                    deslocamentoY = 0f
                )
            }

            "Diagonal 3D" -> {
                Movimento3D(
                    avanco = 0.080f,
                    deslocamentoX = (
                        -largura * 0.030f
                    ) + (
                        progresso * largura * 0.060f
                    ),
                    deslocamentoY = (
                        altura * 0.025f
                    ) - (
                        progresso * altura * 0.050f
                    )
                )
            }

            else -> {
                /*
                 * Entrada 3D:
                 * avanço controlado, sem passos e sem tremor.
                 */
                Movimento3D(
                    avanco = 0.115f * progresso,
                    deslocamentoX = (
                        -largura * 0.010f
                    ) + (
                        progresso * largura * 0.020f
                    ),
                    deslocamentoY = (
                        altura * 0.006f
                    ) - (
                        progresso * altura * 0.012f
                    )
                )
            }
        }
    }

    private data class Movimento3D(
        val avanco: Float,
        val deslocamentoX: Float,
        val deslocamentoY: Float
    )
}
