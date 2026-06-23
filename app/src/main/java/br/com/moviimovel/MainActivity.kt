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
import androidx.compose.foundation.layout.Row
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
    val context = androidx.compose.ui.platform.LocalContext.current

    var movimento by remember { mutableStateOf("Gimbal") }
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
            color = Color(0xFF101417)
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
                    text = "Movimento profissional para fotos de imóveis",
                    color = Color(0xFFB8C1C8),
                    fontSize = 15.sp
                )

                PreviewMovimento(
                    bitmap = fotoSelecionada,
                    movimento = movimento,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                )

                Text(
                    text = "Movimento selecionado: $movimento",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MovimentoBotao(
                        texto = "Zoom In",
                        selecionado = movimento == "Zoom In",
                        onClick = { movimento = "Zoom In" },
                        modifier = Modifier.weight(1f)
                    )

                    MovimentoBotao(
                        texto = "Zoom Out",
                        selecionado = movimento == "Zoom Out",
                        onClick = { movimento = "Zoom Out" },
                        modifier = Modifier.weight(1f)
                    )

                    MovimentoBotao(
                        texto = "Gimbal",
                        selecionado = movimento == "Gimbal",
                        onClick = { movimento = "Gimbal" },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MovimentoBotao(
                        texto = "← Pan",
                        selecionado = movimento == "Pan Esquerda",
                        onClick = { movimento = "Pan Esquerda" },
                        modifier = Modifier.weight(1f)
                    )

                    MovimentoBotao(
                        texto = "Pan →",
                        selecionado = movimento == "Pan Direita",
                        onClick = { movimento = "Pan Direita" },
                        modifier = Modifier.weight(1f)
                    )

                    MovimentoBotao(
                        texto = "Diagonal",
                        selecionado = movimento == "Diagonal",
                        onClick = { movimento = "Diagonal" },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MovimentoBotao(
                        texto = "↑ Cima",
                        selecionado = movimento == "Pan Cima",
                        onClick = { movimento = "Pan Cima" },
                        modifier = Modifier.weight(1f)
                    )

                    MovimentoBotao(
                        texto = "Baixo ↓",
                        selecionado = movimento == "Pan Baixo",
                        onClick = { movimento = "Pan Baixo" },
                        modifier = Modifier.weight(1f)
                    )

                    MovimentoBotao(
                        texto = "Ken Burns",
                        selecionado = movimento == "Ken Burns",
                        onClick = { movimento = "Ken Burns" },
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
                        containerColor = Color(0xFF19A86B)
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
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (fotoSelecionada == null) {
                        "Selecione uma foto para testar os movimentos."
                    } else {
                        "A foto permanece preenchendo a área inteira durante todo o movimento."
                    },
                    color = Color(0xFF9BA7AF),
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
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PreviewMovimento(
    bitmap: Bitmap?,
    movimento: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "movimento")
    val density = LocalDensity.current.density

    var larguraArea by remember { mutableIntStateOf(1) }
    var alturaArea by remember { mutableIntStateOf(1) }

    val progresso by transition.animateFloat(
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

    val largura = larguraArea.toFloat()
    val altura = alturaArea.toFloat()

    val escala = when (movimento) {
        "Zoom In" -> 1.28f + (progresso * 0.20f)
        "Zoom Out" -> 1.48f - (progresso * 0.20f)
        "Pan Esquerda" -> 1.34f
        "Pan Direita" -> 1.34f
        "Pan Cima" -> 1.34f
        "Pan Baixo" -> 1.34f
        "Diagonal" -> 1.38f
        "Ken Burns" -> 1.35f + (progresso * 0.13f)
        else -> 1.38f + (progresso * 0.08f)
    }

    val deslocamentoX = when (movimento) {
        "Pan Esquerda" -> largura * 0.09f - (progresso * largura * 0.18f)
        "Pan Direita" -> -largura * 0.09f + (progresso * largura * 0.18f)
        "Diagonal" -> -largura * 0.08f + (progresso * largura * 0.16f)
        "Ken Burns" -> -largura * 0.06f + (progresso * largura * 0.12f)
        "Gimbal" -> -largura * 0.035f + (progresso * largura * 0.07f)
        else -> 0f
    }

    val deslocamentoY = when (movimento) {
        "Pan Cima" -> altura * 0.08f - (progresso * altura * 0.16f)
        "Pan Baixo" -> -altura * 0.08f + (progresso * altura * 0.16f)
        "Diagonal" -> altura * 0.06f - (progresso * altura * 0.12f)
        "Ken Burns" -> altura * 0.045f - (progresso * altura * 0.09f)
        "Gimbal" -> altura * 0.02f - (progresso * altura * 0.04f)
        else -> 0f
    }

    val rotacaoY = when (movimento) {
        "Gimbal" -> -1.4f + (progresso * 2.8f)
        else -> 0f
    }

    val rotacaoX = when (movimento) {
        "Gimbal" -> 0.8f - (progresso * 1.6f)
        else -> 0f
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A2227)
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
                    contentDescription = "Foto do imóvel em movimento",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = escala
                            scaleY = escala
                            translationX = deslocamentoX
                            translationY = deslocamentoY
                            rotationX = rotacaoX
                            rotationY = rotacaoY
                            cameraDistance = 14f * density
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
                        text = "A foto ocupará toda esta área",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Sem laterais, fundos ou molduras aparentes.",
                        color = Color(0xFFD1D9DE),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
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
