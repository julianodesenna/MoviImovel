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
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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

    var modo by remember { mutableStateOf("Caminhada") }
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
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Simulação de câmera andando com a foto",
                    color = Color(0xFFB8C1C8),
                    fontSize = 15.sp
                )

                PreviewMovimentoGoPro(
                    bitmap = fotoSelecionada,
                    modo = modo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                )

                Text(
                    text = "Modo: $modo",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MovimentoBotao(
                        texto = "Caminhada",
                        selecionado = modo == "Caminhada",
                        onClick = { modo = "Caminhada" },
                        modifier = Modifier.weight(1f)
                    )

                    MovimentoBotao(
                        texto = "Entrada",
                        selecionado = modo == "Entrada",
                        onClick = { modo = "Entrada" },
                        modifier = Modifier.weight(1f)
                    )

                    MovimentoBotao(
                        texto = "Lateral",
                        selecionado = modo == "Lateral",
                        onClick = { modo = "Lateral" },
                        modifier = Modifier.weight(1f)
                    )
                }

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
                        "Escolha uma foto para testar a simulação de caminhada."
                    } else {
                        "O foco agora é o motor de movimento. Ainda não é 3D real, mas já tenta se comportar como câmera em deslocamento."
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
fun PreviewMovimentoGoPro(
    bitmap: Bitmap?,
    modo: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "camera_go_pro")
    val density = LocalDensity.current.density

    var larguraArea by remember { mutableIntStateOf(1) }
    var alturaArea by remember { mutableIntStateOf(1) }

    val progresso by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 5200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "progresso"
    )

    val largura = larguraArea.toFloat()
    val altura = alturaArea.toFloat()

    val anguloBase = progresso * (Math.PI.toFloat() * 2f)
    val passo = sin(anguloBase * 2f)
    val corpo = sin(anguloBase)
    val respiracao = sin(anguloBase * 0.5f)

    val parametros = calcularMovimentoGoPro(
        modo = modo,
        progresso = progresso,
        passo = passo,
        corpo = corpo,
        respiracao = respiracao,
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
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Foto do imóvel em simulação de câmera",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = parametros.scale
                            scaleY = parametros.scale
                            translationX = parametros.translationX
                            translationY = parametros.translationY
                            rotationZ = parametros.rotationZ
                            rotationY = parametros.rotationY
                            rotationX = parametros.rotationX
                            cameraDistance = 18f * density
                        }
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.16f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.12f)
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
                        text = "A foto vai preencher tudo",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Nesta etapa o foco é fazer a imagem se comportar mais como câmera em movimento.",
                        color = Color(0xFFD3DADF),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private data class CameraMotion(
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
    val rotationX: Float,
    val rotationY: Float,
    val rotationZ: Float
)

private fun calcularMovimentoGoPro(
    modo: String,
    progresso: Float,
    passo: Float,
    corpo: Float,
    respiracao: Float,
    largura: Float,
    altura: Float
): CameraMotion {
    return when (modo) {
        "Entrada" -> {
            val pushIn = 1.34f + (progresso * 0.22f)
            val swayX = passo * largura * 0.010f
            val bobY = abs(passo) * altura * 0.014f
            val driftX = (-largura * 0.020f) + (progresso * largura * 0.040f)
            val driftY = (altura * 0.010f) - (progresso * altura * 0.030f)
            val rotZ = passo * 0.55f
            val rotY = corpo * 1.05f
            val rotX = (0.25f - abs(passo)) * 0.9f

            CameraMotion(
                scale = pushIn + abs(passo) * 0.012f,
                translationX = driftX + swayX,
                translationY = driftY + bobY,
                rotationX = rotX,
                rotationY = rotY,
                rotationZ = rotZ
            )
        }

        "Lateral" -> {
            val baseScale = 1.46f
            val lateral = (-largura * 0.11f) + (progresso * largura * 0.22f)
            val bobY = abs(passo) * altura * 0.010f
            val microX = passo * largura * 0.008f
            val rotZ = passo * 0.45f
            val rotY = -1.2f + (progresso * 2.4f)
            val rotX = corpo * 0.45f

            CameraMotion(
                scale = baseScale + abs(corpo) * 0.012f,
                translationX = lateral + microX,
                translationY = bobY,
                rotationX = rotX,
                rotationY = rotY,
                rotationZ = rotZ
            )
        }

        else -> {
            val baseScale = 1.40f + (progresso * 0.08f)
            val advanceX = (-largura * 0.025f) + (progresso * largura * 0.050f)
            val advanceY = (altura * 0.014f) - (progresso * altura * 0.028f)

            val swayX = passo * largura * 0.014f
            val bobY = abs(passo) * altura * 0.016f
            val shoulderY = corpo * altura * 0.004f
            val breathingZoom = abs(respiracao) * 0.010f

            val rotZ = passo * 0.60f
            val rotY = corpo * 0.90f
            val rotX = cos(progresso * Math.PI.toFloat() * 4f) * 0.42f

            CameraMotion(
                scale = baseScale + breathingZoom,
                translationX = advanceX + swayX,
                translationY = advanceY + bobY + shoulderY,
                rotationX = rotX,
                rotationY = rotY,
                rotationZ = rotZ
            )
        }
    }
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
