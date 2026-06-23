package br.com.moviimovel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MoviImovelApp()
        }
    }
}

@Composable
fun MoviImovelApp() {
    val context = LocalContext.current
    var fotoSelecionada by remember { mutableStateOf<Bitmap?>(null) }

    val seletorFoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            fotoSelecionada = carregarBitmap(
                context = context,
                uriTexto = uri.toString()
            )
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F1417)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "MoviImovel",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Movimento estabilizado com profundidade visual",
                    color = Color(0xFFB9C3C9),
                    fontSize = 15.sp
                )

                PreviewProfundidadeEstabilizada(
                    bitmap = fotoSelecionada,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                )

                Button(
                    onClick = {
                        seletorFoto.launch("image/*")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF179A63)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (fotoSelecionada == null) {
                            "Selecionar foto do imóvel"
                        } else {
                            "Trocar foto do imóvel"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = if (fotoSelecionada == null) {
                        "Selecione uma foto para testar o novo movimento."
                    } else {
                        "Esta versão remove o efeito de passos e foca em profundidade visual estabilizada."
                    },
                    color = Color(0xFF97A4AC),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun PreviewProfundidadeEstabilizada(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "profundidade_estabilizada")
    val density = LocalDensity.current.density

    var larguraArea by remember { mutableIntStateOf(1) }
    var alturaArea by remember { mutableIntStateOf(1) }

    val progressoBruto by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 6200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progresso"
    )

    val progresso = smoothStep(progressoBruto)

    val largura = larguraArea.toFloat()
    val altura = alturaArea.toFloat()

    /*
     * CAMADA DE FUNDO
     * Mais lenta, mais ampliada, levemente desfocada.
     * Serve para dar sensação de profundidade.
     */
    val escalaFundo = 1.52f + (progresso * 0.05f)
    val deslocamentoXFundo = (-largura * 0.010f) + (progresso * largura * 0.020f)
    val deslocamentoYFundo = (altura * 0.004f) - (progresso * altura * 0.008f)
    val rotacaoYFundo = -0.18f + (progresso * 0.36f)

    /*
     * CAMADA INTERMEDIÁRIA
     * Movimento moderado.
     */
    val escalaMeio = 1.38f + (progresso * 0.08f)
    val deslocamentoXMeio = (-largura * 0.018f) + (progresso * largura * 0.036f)
    val deslocamentoYMeio = (altura * 0.007f) - (progresso * altura * 0.014f)
    val rotacaoYMeio = -0.35f + (progresso * 0.70f)
    val rotacaoXMeio = 0.10f - (progresso * 0.20f)

    /*
     * CAMADA FRONTAL
     * Movimento principal, mais “presente”.
     * Continua estabilizado, sem tremida.
     */
    val escalaFrente = 1.24f + (progresso * 0.12f)
    val deslocamentoXFrente = (-largura * 0.028f) + (progresso * largura * 0.056f)
    val deslocamentoYFrente = (altura * 0.010f) - (progresso * altura * 0.020f)
    val rotacaoYFrente = -0.65f + (progresso * 1.30f)
    val rotacaoXFrente = 0.18f - (progresso * 0.36f)
    val rotacaoZFrente = -0.08f + (progresso * 0.16f)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF182126)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black)
                .onSizeChanged {
                    larguraArea = it.width
                    alturaArea = it.height
                }
        ) {
            if (bitmap != null) {
                val imagem = bitmap.asImageBitmap()

                /*
                 * FUNDO DESFOCADO
                 */
                Image(
                    bitmap = imagem,
                    contentDescription = "Camada de fundo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(18.dp)
                        .graphicsLayer {
                            scaleX = escalaFundo
                            scaleY = escalaFundo
                            translationX = deslocamentoXFundo
                            translationY = deslocamentoYFundo
                            rotationY = rotacaoYFundo
                            cameraDistance = 18f * density
                            alpha = 0.82f
                        }
                )

                /*
                 * CAMADA INTERMEDIÁRIA
                 */
                Image(
                    bitmap = imagem,
                    contentDescription = "Camada intermediária",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = escalaMeio
                            scaleY = escalaMeio
                            translationX = deslocamentoXMeio
                            translationY = deslocamentoYMeio
                            rotationX = rotacaoXMeio
                            rotationY = rotacaoYMeio
                            cameraDistance = 18f * density
                            alpha = 0.30f
                        }
                )

                /*
                 * CAMADA PRINCIPAL
                 */
                Image(
                    bitmap = imagem,
                    contentDescription = "Foto do imóvel em profundidade visual",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = escalaFrente
                            scaleY = escalaFrente
                            translationX = deslocamentoXFrente
                            translationY = deslocamentoYFrente
                            rotationX = rotacaoXFrente
                            rotationY = rotacaoYFrente
                            rotationZ = rotacaoZFrente
                            cameraDistance = 20f * density
                        }
                )

                /*
                 * SOMBREAMENTO SUAVE PARA DAR “CLIMA” MAIS CINEMÁTICO
                 */
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.16f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.14f)
                                )
                            )
                        )
                )
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 280.dp)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "A foto preencherá toda esta área",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Agora o movimento é estabilizado e focado em profundidade visual.",
                        color = Color(0xFFD3DADF),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun smoothStep(t: Float): Float {
    return t * t * (3f - 2f * t)
}

private fun carregarBitmap(
    context: Context,
    uriTexto: String
): Bitmap? {
    return try {
        val uri = android.net.Uri.parse(uriTexto)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)

            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val larguraOriginal = info.size.width
                val alturaOriginal = info.size.height
                val maiorLado = maxOf(larguraOriginal, alturaOriginal)
                val limite = 2400

                if (maiorLado > limite) {
                    val proporcao = limite.toFloat() / maiorLado.toFloat()

                    decoder.setTargetSize(
                        (larguraOriginal * proporcao).toInt(),
                        (alturaOriginal * proporcao).toInt()
                    )
                }
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }
}
