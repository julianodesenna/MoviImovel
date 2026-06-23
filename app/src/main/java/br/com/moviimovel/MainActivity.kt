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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
    var movimentoAtual by remember { mutableStateOf("Entrada 3D") }

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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "MoviImovel",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Parallax 3D com profundidade e movimentos de entrada",
                    color = Color(0xFFB9C3C9),
                    fontSize = 15.sp
                )

                PreviewParallax3D(
                    bitmap = fotoSelecionada,
                    movimento = movimentoAtual,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                )

                Text(
                    text = "Movimento atual: $movimentoAtual",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                MovimentoBotao(
                    texto = "Entrada 3D",
                    selecionado = movimentoAtual == "Entrada 3D",
                    onClick = { movimentoAtual = "Entrada 3D" },
                    modifier = Modifier.fillMaxWidth()
                )

                MovimentoBotao(
                    texto = "Pan Profundo Esquerda",
                    selecionado = movimentoAtual == "Pan Profundo Esquerda",
                    onClick = { movimentoAtual = "Pan Profundo Esquerda" },
                    modifier = Modifier.fillMaxWidth()
                )

                MovimentoBotao(
                    texto = "Pan Profundo Direita",
                    selecionado = movimentoAtual == "Pan Profundo Direita",
                    onClick = { movimentoAtual = "Pan Profundo Direita" },
                    modifier = Modifier.fillMaxWidth()
                )

                MovimentoBotao(
                    texto = "Diagonal de Entrada",
                    selecionado = movimentoAtual == "Diagonal de Entrada",
                    onClick = { movimentoAtual = "Diagonal de Entrada" },
                    modifier = Modifier.fillMaxWidth()
                )

                MovimentoBotao(
                    texto = "Reveal Vertical",
                    selecionado = movimentoAtual == "Reveal Vertical",
                    onClick = { movimentoAtual = "Reveal Vertical" },
                    modifier = Modifier.fillMaxWidth()
                )

                MovimentoBotao(
                    texto = "Órbita Leve",
                    selecionado = movimentoAtual == "Órbita Leve",
                    onClick = { movimentoAtual = "Órbita Leve" },
                    modifier = Modifier.fillMaxWidth()
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
                        "Selecione uma foto para testar o novo motor de parallax 3D."
                    } else {
                        "Esta etapa já usa três planos de profundidade com movimentos diferentes."
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
fun MovimentoBotao(
    texto: String,
    selecionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selecionado) {
                Color(0xFF1C8B5E)
            } else {
                Color(0xFF28343A)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = texto,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PreviewParallax3D(
    bitmap: Bitmap?,
    movimento: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "parallax_3d")
    val density = LocalDensity.current.density

    var larguraArea by remember { mutableIntStateOf(1) }
    var alturaArea by remember { mutableIntStateOf(1) }

    val progressoBruto by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 7000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progresso"
    )

    val progresso = smoothStep(progressoBruto)
    val largura = larguraArea.toFloat()
    val altura = alturaArea.toFloat()
    val imagem = bitmap?.asImageBitmap()

    val camera = calcularCameraPath(
        movimento = movimento,
        progresso = progresso,
        largura = largura,
        altura = altura
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF182126)
        )
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black)
                .onSizeChanged {
                    larguraArea = it.width
                    alturaArea = it.height
                }
        ) {
            if (imagem != null) {
                /*
                 * CAMADA DE FUNDO
                 * Move menos, é mais “distante”.
                 */
                ParallaxLayer(
                    image = imagem,
                    scale = camera.scale + 0.26f,
                    translationX = camera.translationX * 0.35f,
                    translationY = camera.translationY * 0.35f,
                    rotationX = camera.rotationX * 0.18f,
                    rotationY = camera.rotationY * 0.25f,
                    rotationZ = camera.rotationZ * 0.10f,
                    alpha = 0.95f,
                    blurAmount = 8.dp,
                    clipTopFraction = 0f,
                    clipBottomFraction = 1f,
                    cameraDistance = 18f * density
                )

                /*
                 * CAMADA DO MEIO
                 * Move mais que o fundo.
                 */
                ParallaxLayer(
                    image = imagem,
                    scale = camera.scale + 0.14f,
                    translationX = camera.translationX * 0.70f,
                    translationY = camera.translationY * 0.70f,
                    rotationX = camera.rotationX * 0.45f,
                    rotationY = camera.rotationY * 0.55f,
                    rotationZ = camera.rotationZ * 0.35f,
                    alpha = 0.48f,
                    blurAmount = 0.dp,
                    clipTopFraction = 0.24f,
                    clipBottomFraction = 0.88f,
                    cameraDistance = 20f * density
                )

                /*
                 * CAMADA FRONTAL
                 * Move mais e dá a sensação de profundidade.
                 */
                ParallaxLayer(
                    image = imagem,
                    scale = camera.scale,
                    translationX = camera.translationX * 1.18f,
                    translationY = camera.translationY * 1.10f,
                    rotationX = camera.rotationX,
                    rotationY = camera.rotationY,
                    rotationZ = camera.rotationZ,
                    alpha = 1f,
                    blurAmount = 0.dp,
                    clipTopFraction = 0.52f,
                    clipBottomFraction = 1f,
                    cameraDistance = 22f * density
                )

                androidx.compose.foundation.layout.Box(
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
                        text = "Agora o app usa três planos para criar parallax 3D.",
                        color = Color(0xFFD3DADF),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ParallaxLayer(
    image: androidx.compose.ui.graphics.ImageBitmap,
    scale: Float,
    translationX: Float,
    translationY: Float,
    rotationX: Float,
    rotationY: Float,
    rotationZ: Float,
    alpha: Float,
    blurAmount: Dp,
    clipTopFraction: Float,
    clipBottomFraction: Float,
    cameraDistance: Float
) {
    val blurModifier = if (blurAmount > 0.dp) {
        Modifier.blur(blurAmount)
    } else {
        Modifier
    }

    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .then(blurModifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.translationX = translationX
                this.translationY = translationY
                this.rotationX = rotationX
                this.rotationY = rotationY
                this.rotationZ = rotationZ
                this.alpha = alpha
                this.cameraDistance = cameraDistance
            }
            .drawWithContent {
                val top = size.height * clipTopFraction
                val bottom = size.height * clipBottomFraction

                clipRect(
                    left = 0f,
                    top = top,
                    right = size.width,
                    bottom = bottom
                ) {
                    this@drawWithContent.drawContent()
                }
            }
    )
}

private data class CameraPath(
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
    val rotationX: Float,
    val rotationY: Float,
    val rotationZ: Float
)

private fun calcularCameraPath(
    movimento: String,
    progresso: Float,
    largura: Float,
    altura: Float
): CameraPath {
    return when (movimento) {
        "Pan Profundo Esquerda" -> {
            CameraPath(
                scale = 1.34f,
                translationX = (largura * 0.10f) - (progresso * largura * 0.20f),
                translationY = (altura * 0.004f) - (progresso * altura * 0.008f),
                rotationX = 0.10f - (progresso * 0.20f),
                rotationY = 0.90f - (progresso * 1.80f),
                rotationZ = 0.06f - (progresso * 0.12f)
            )
        }

        "Pan Profundo Direita" -> {
            CameraPath(
                scale = 1.34f,
                translationX = (-largura * 0.10f) + (progresso * largura * 0.20f),
                translationY = (altura * 0.004f) - (progresso * altura * 0.008f),
                rotationX = 0.10f - (progresso * 0.20f),
                rotationY = -0.90f + (progresso * 1.80f),
                rotationZ = -0.06f + (progresso * 0.12f)
            )
        }

        "Diagonal de Entrada" -> {
            CameraPath(
                scale = 1.24f + (progresso * 0.14f),
                translationX = (-largura * 0.05f) + (progresso * largura * 0.10f),
                translationY = (altura * 0.06f) - (progresso * altura * 0.12f),
                rotationX = 0.30f - (progresso * 0.60f),
                rotationY = -0.60f + (progresso * 1.20f),
                rotationZ = -0.08f + (progresso * 0.16f)
            )
        }

        "Reveal Vertical" -> {
            CameraPath(
                scale = 1.28f + (progresso * 0.10f),
                translationX = (-largura * 0.01f) + (progresso * largura * 0.02f),
                translationY = (altura * 0.08f) - (progresso * altura * 0.16f),
                rotationX = 0.48f - (progresso * 0.96f),
                rotationY = -0.20f + (progresso * 0.40f),
                rotationZ = 0f
            )
        }

        "Órbita Leve" -> {
            CameraPath(
                scale = 1.30f + (progresso * 0.12f),
                translationX = (-largura * 0.06f) + (progresso * largura * 0.12f),
                translationY = (altura * 0.01f) - (progresso * altura * 0.02f),
                rotationX = 0.18f - (progresso * 0.36f),
                rotationY = -1.10f + (progresso * 2.20f),
                rotationZ = -0.05f + (progresso * 0.10f)
            )
        }

        else -> {
            /*
             * Entrada 3D
             * Movimento mais importante para “entrar” no ambiente.
             */
            CameraPath(
                scale = 1.22f + (progresso * 0.18f),
                translationX = (-largura * 0.025f) + (progresso * largura * 0.05f),
                translationY = (altura * 0.018f) - (progresso * altura * 0.036f),
                rotationX = 0.22f - (progresso * 0.44f),
                rotationY = -0.55f + (progresso * 1.10f),
                rotationZ = -0.03f + (progresso * 0.06f)
            )
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
