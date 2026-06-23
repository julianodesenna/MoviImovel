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
import androidx.compose.ui.draw.clip
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
    var movimentoAtual by remember { mutableStateOf("Entrada Suave") }

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
            color = Color(0xFF0E1316)
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
                    text = "Movimento estabilizado para foto de imóvel",
                    color = Color(0xFFB6C0C6),
                    fontSize = 15.sp
                )

                PreviewMovimentoLimpo(
                    bitmap = fotoSelecionada,
                    movimento = movimentoAtual,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                )

                Text(
                    text = "Movimento: $movimentoAtual",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                MovimentoBotao(
                    texto = "Entrada Suave",
                    selecionado = movimentoAtual == "Entrada Suave",
                    onClick = { movimentoAtual = "Entrada Suave" },
                    modifier = Modifier.fillMaxWidth()
                )

                MovimentoBotao(
                    texto = "Pan Cinemático Esquerda",
                    selecionado = movimentoAtual == "Pan Cinemático Esquerda",
                    onClick = { movimentoAtual = "Pan Cinemático Esquerda" },
                    modifier = Modifier.fillMaxWidth()
                )

                MovimentoBotao(
                    texto = "Pan Cinemático Direita",
                    selecionado = movimentoAtual == "Pan Cinemático Direita",
                    onClick = { movimentoAtual = "Pan Cinemático Direita" },
                    modifier = Modifier.fillMaxWidth()
                )

                MovimentoBotao(
                    texto = "Diagonal Estabilizada",
                    selecionado = movimentoAtual == "Diagonal Estabilizada",
                    onClick = { movimentoAtual = "Diagonal Estabilizada" },
                    modifier = Modifier.fillMaxWidth()
                )

                MovimentoBotao(
                    texto = "Reveal Vertical",
                    selecionado = movimentoAtual == "Reveal Vertical",
                    onClick = { movimentoAtual = "Reveal Vertical" },
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
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (fotoSelecionada == null) {
                        "Selecione uma foto para testar a câmera estabilizada."
                    } else {
                        "Esta versão remove os cortes e distorções. O parallax 3D real entra na próxima etapa com mapa de profundidade."
                    },
                    color = Color(0xFF96A2AA),
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
fun PreviewMovimentoLimpo(
    bitmap: Bitmap?,
    movimento: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "movimento_limpo")
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

    val camera = calcularCameraLimpa(
        movimento = movimento,
        progresso = progresso,
        largura = largura,
        altura = altura
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
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
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Foto do imóvel em movimento",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = camera.scale
                            scaleY = camera.scale
                            translationX = camera.translationX
                            translationY = camera.translationY
                            rotationX = camera.rotationX
                            rotationY = camera.rotationY
                            rotationZ = camera.rotationZ
                            cameraDistance = 24f * density
                        }
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
                        text = "Sem faixas, cópias, blur ou cortes artificiais.",
                        color = Color(0xFFD4DCE1),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private data class CameraLimpa(
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
    val rotationX: Float,
    val rotationY: Float,
    val rotationZ: Float
)

private fun calcularCameraLimpa(
    movimento: String,
    progresso: Float,
    largura: Float,
    altura: Float
): CameraLimpa {
    return when (movimento) {
        "Pan Cinemático Esquerda" -> {
            CameraLimpa(
                scale = 1.42f,
                translationX = (largura * 0.08f) - (progresso * largura * 0.16f),
                translationY = 0f,
                rotationX = 0f,
                rotationY = 0.28f - (progresso * 0.56f),
                rotationZ = 0f
            )
        }

        "Pan Cinemático Direita" -> {
            CameraLimpa(
                scale = 1.42f,
                translationX = (-largura * 0.08f) + (progresso * largura * 0.16f),
                translationY = 0f,
                rotationX = 0f,
                rotationY = -0.28f + (progresso * 0.56f),
                rotationZ = 0f
            )
        }

        "Diagonal Estabilizada" -> {
            CameraLimpa(
                scale = 1.38f + (progresso * 0.08f),
                translationX = (-largura * 0.055f) + (progresso * largura * 0.11f),
                translationY = (altura * 0.045f) - (progresso * altura * 0.09f),
                rotationX = 0.12f - (progresso * 0.24f),
                rotationY = -0.18f + (progresso * 0.36f),
                rotationZ = 0f
            )
        }

        "Reveal Vertical" -> {
            CameraLimpa(
                scale = 1.40f,
                translationX = 0f,
                translationY = (altura * 0.075f) - (progresso * altura * 0.15f),
                rotationX = 0.30f - (progresso * 0.60f),
                rotationY = 0f,
                rotationZ = 0f
            )
        }

        else -> {
            CameraLimpa(
                scale = 1.32f + (progresso * 0.16f),
                translationX = (-largura * 0.018f) + (progresso * largura * 0.036f),
                translationY = (altura * 0.012f) - (progresso * altura * 0.024f),
                rotationX = 0.10f - (progresso * 0.20f),
                rotationY = -0.18f + (progresso * 0.36f),
                rotationZ = 0f
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
