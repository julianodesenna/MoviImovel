package br.com.moviimovel

import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class DepthParallaxRenderer {

    private var bitmapAtual: Bitmap? = null
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

        val larguraFinal = max(1, larguraSaida)
        val alturaFinal = max(1, alturaSaida)

        val pixelsSaida = IntArray(larguraFinal * alturaFinal)

        val escalaPreenchimento = max(
            larguraFinal.toFloat() / larguraOriginal.toFloat(),
            alturaFinal.toFloat() / alturaOriginal.toFloat()
        )

        val larguraExibida = larguraOriginal * escalaPreenchimento
        val alturaExibida = alturaOriginal * escalaPreenchimento

        val corteX = (larguraExibida - larguraFinal) / 2f
        val corteY = (alturaExibida - alturaFinal) / 2f

        val movimento = calcularMovimento(
            modo = modo,
            progresso = progresso,
            largura = larguraFinal,
            altura = alturaFinal
        )

        val centroX = larguraFinal / 2f
        val centroY = alturaFinal / 2f

        for (y in 0 until alturaFinal) {
            for (x in 0 until larguraFinal) {
                val u = x.toFloat() / larguraFinal.toFloat()
                val v = y.toFloat() / alturaFinal.toFloat()

                val profundidade = profundidadeNoPonto(
                    depthResult = depthResult,
                    u = u,
                    v = v
                )

                /*
                 * O fundo se move pouco.
                 * O primeiro plano se move mais, mas sem exagero.
                 */
                val intensidade = 0.30f + (profundidade * 0.70f)

                val fatorZoom = 1f + (
                    movimento.avanco * profundidade
                )

                val relativoX = x - centroX
                val relativoY = y - centroY

                val origemTelaX = centroX + (
                    relativoX / fatorZoom
                ) - (
                    movimento.deslocamentoX * intensidade
                )

                val origemTelaY = centroY + (
                    relativoY / fatorZoom
                ) - (
                    movimento.deslocamentoY * intensidade
                )

                val origemOriginalX = (
                    origemTelaX + corteX
                ) / escalaPreenchimento

                val origemOriginalY = (
                    origemTelaY + corteY
                ) / escalaPreenchimento

                pixelsSaida[(y * larguraFinal) + x] =
                    amostrarPixelBilinear(
                        x = origemOriginalX,
                        y = origemOriginalY
                    )
            }
        }

        return Bitmap.createBitmap(
            pixelsSaida,
            larguraFinal,
            alturaFinal,
            Bitmap.Config.ARGB_8888
        )
    }

    private fun prepararBitmap(
        bitmap: Bitmap
    ) {
        if (bitmapAtual === bitmap) {
            return
        }

        bitmapAtual = bitmap
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

    private fun amostrarPixelBilinear(
        x: Float,
        y: Float
    ): Int {
        val xLimitado = min(
            larguraOriginal - 1.001f,
            max(0f, x)
        )

        val yLimitado = min(
            alturaOriginal - 1.001f,
            max(0f, y)
        )

        val x0 = floor(xLimitado).toInt()
        val y0 = floor(yLimitado).toInt()

        val x1 = min(
            larguraOriginal - 1,
            x0 + 1
        )

        val y1 = min(
            alturaOriginal - 1,
            y0 + 1
        )

        val dx = xLimitado - x0
        val dy = yLimitado - y0

        val cor00 = pixelsOriginais[(y0 * larguraOriginal) + x0]
        val cor10 = pixelsOriginais[(y0 * larguraOriginal) + x1]
        val cor01 = pixelsOriginais[(y1 * larguraOriginal) + x0]
        val cor11 = pixelsOriginais[(y1 * larguraOriginal) + x1]

        val alpha = interpolarCanal(
            (cor00 ushr 24) and 0xFF,
            (cor10 ushr 24) and 0xFF,
            (cor01 ushr 24) and 0xFF,
            (cor11 ushr 24) and 0xFF,
            dx,
            dy
        )

        val vermelho = interpolarCanal(
            (cor00 shr 16) and 0xFF,
            (cor10 shr 16) and 0xFF,
            (cor01 shr 16) and 0xFF,
            (cor11 shr 16) and 0xFF,
            dx,
            dy
        )

        val verde = interpolarCanal(
            (cor00 shr 8) and 0xFF,
            (cor10 shr 8) and 0xFF,
            (cor01 shr 8) and 0xFF,
            (cor11 shr 8) and 0xFF,
            dx,
            dy
        )

        val azul = interpolarCanal(
            cor00 and 0xFF,
            cor10 and 0xFF,
            cor01 and 0xFF,
            cor11 and 0xFF,
            dx,
            dy
        )

        return (
            (alpha shl 24) or
                (vermelho shl 16) or
                (verde shl 8) or
                azul
            )
    }

    private fun interpolarCanal(
        c00: Int,
        c10: Int,
        c01: Int,
        c11: Int,
        dx: Float,
        dy: Float
    ): Int {
        val parteSuperior = c00 + ((c10 - c00) * dx)
        val parteInferior = c01 + ((c11 - c01) * dx)

        val resultado = parteSuperior + (
            (parteInferior - parteSuperior) * dy
        )

        return min(
            255,
            max(
                0,
                resultado.toInt()
            )
        )
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
                    avanco = 0.012f,
                    deslocamentoX = (
                        -largura * 0.007f
                    ) + (
                        progresso * largura * 0.014f
                    ),
                    deslocamentoY = 0f
                )
            }

            "Diagonal 3D" -> {
                Movimento3D(
                    avanco = 0.018f,
                    deslocamentoX = (
                        -largura * 0.006f
                    ) + (
                        progresso * largura * 0.012f
                    ),
                    deslocamentoY = (
                        altura * 0.005f
                    ) - (
                        progresso * altura * 0.010f
                    )
                )
            }

            else -> {
                /*
                 * Entrada 3D estabilizada:
                 * sem tremor e com deslocamento curto.
                 */
                Movimento3D(
                    avanco = 0.030f * progresso,
                    deslocamentoX = (
                        -largura * 0.0025f
                    ) + (
                        progresso * largura * 0.005f
                    ),
                    deslocamentoY = (
                        altura * 0.002f
                    ) - (
                        progresso * altura * 0.004f
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
