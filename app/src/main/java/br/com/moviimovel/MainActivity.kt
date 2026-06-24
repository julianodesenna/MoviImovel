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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()

    var fotoSelecionada by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var depthResult by remember {
        mutableStateOf<DepthResult?>(null)
    }

    var mostrandoMapa by remember {
        mutableStateOf(false)
    }

    var movimentoNitidoAtivo by remember {
        mutableStateOf(false)
    }

    var processandoMapa by remember {
        mutableStateOf(false)
    }

    var mensagem by remember {
        mutableStateOf(
            "Selecione uma foto. Este teste preserva a qualidade original."
        )
    }

    val seletorFoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bitmapCarregado = carregarBitmapAltaQualidade(
                context = context,
                uriTexto = uri.toString()
            )

            if (bitmapCarregado != null) {
                fotoSelecionada = bitmapCarregado
                depthResult = null
                mostrandoMapa = false
                movimentoNitidoAtivo = false

                mensagem =
                    "Foto carregada em alta qualidade. Teste o movimento nítido."
            } else {
                mensagem = "Não foi possível carregar essa foto."
            }
        }
    }

    val transition = rememberInfiniteTransition(
        label = "movimento_local_nitido"
    )

    val progressoMovimento by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 7000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "camera_suave"
    )

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0E1316)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Text(
                    text = "MoviImovel",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Teste local de qualidade máxima",
                    color = Color(0xFFB8C2C8),
                    fontSize = 15.sp
                )

                PreviewImagemAltaQualidade(
                    bitmap = when {
                        mostrandoMapa -> depthResult?.visualMap
                        else -> fotoSelecionada
                    },
                    titulo = when {
                        mostrandoMapa -> "Mapa de profundidade"
                        movimentoNitidoAtivo -> "Movimento local nítido"
                        else -> "Foto original"
                    },
                    movimentoAtivo = movimentoNitidoAtivo && !mostrandoMapa,
                    progresso = progressoMovimento,
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
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        val foto = fotoSelecionada ?: return@Button

                        processandoMapa = true
                        movimentoNitidoAtivo = false
                        mensagem = "Criando mapa de profundidade real..."

                        scope.launch(Dispatchers.Default) {
                            try {
                                val resultado = DepthEstimator(context)
                                    .gerarProfundidade(foto)

                                withContext(Dispatchers.Main) {
                                    depthResult = resultado
                                    mostrandoMapa = true
                                    processandoMapa = false

                                    mensagem =
                                        "Mapa pronto. A foto original continua preservada."
                                }
                            } catch (erro: Exception) {
                                withContext(Dispatchers.Main) {
                                    processandoMapa = false
                                    mensagem = "Erro no modelo: ${erro.message}"
                                }
                            }
                        }
                    },
                    enabled = fotoSelecionada != null && !processandoMapa,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF315D89),
                        disabledContainerColor = Color(0xFF2A363F)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (processandoMapa) {
                            "Gerando mapa 3D..."
                        } else {
                            "Gerar mapa de profundidade"
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        mostrandoMapa = false
                        movimentoNitidoAtivo = !movimentoNitidoAtivo

                        mensagem = if (movimentoNitidoAtivo) {
                            "Movimento ativado: foto original sem reconstrução de pixels."
                        } else {
                            "Movimento pausado. Foto original parada."
                        }
                    },
                    enabled = fotoSelecionada != null && !processandoMapa,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (movimentoNitidoAtivo) {
                            Color(0xFF7C3030)
                        } else {
                            Color(0xFF6B3D9A)
                        },
                        disabledContainerColor = Color(0xFF30253B)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (movimentoNitidoAtivo) {
                            "Parar movimento nítido"
                        } else {
                            "Testar movimento nítido"
                        },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        movimentoNitidoAtivo = false
                        mostrandoMapa = !mostrandoMapa

                        mensagem = if (mostrandoMapa) {
                            "Mapa de profundidade exibido."
                        } else {
                            "Foto original exibida."
                        }
                    },
                    enabled = depthResult != null && !processandoMapa,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF222C32),
                        disabledContainerColor = Color(0xFF20282E)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (mostrandoMapa) {
                            "Ver foto original"
                        } else {
                            "Ver mapa de profundidade"
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = mensagem,
                    color = Color(0xFF9EABB3),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun PreviewImagemAltaQualidade(
    bitmap: Bitmap?,
    titulo: String,
    movimentoAtivo: Boolean,
    progresso: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                val deslocamentoNormalizado = progresso - 0.5f

                /*
                 * Movimento propositalmente muito curto.
                 * A imagem não é recriada em Bitmap novo.
                 * O Android movimenta a própria foto original.
                 */
                val escala = if (movimentoAtivo) {
                    1.035f + (progresso * 0.010f)
                } else {
                    1f
                }

                val deslocamentoX = if (movimentoAtivo) {
                    deslocamentoNormalizado * 18f
                } else {
                    0f
                }

                val deslocamentoY = if (movimentoAtivo) {
                    deslocamentoNormalizado * -10f
                } else {
                    0f
                }

                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = titulo,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = escala
                            scaleY = escala
                            translationX = deslocamentoX
                            translationY = deslocamentoY
                        }
                )
            } else {
                Text(
                    text = "Selecione uma foto do imóvel",
                    color = Color(0xFFD4DDE2),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.48f))
                    .padding(
                        horizontal = 12.dp,
                        vertical = 7.dp
                    )
            ) {
                Text(
                    text = titulo,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun carregarBitmapAltaQualidade(
    context: Context,
    uriTexto: String
): Bitmap? {
    return try {
        val uri = android.net.Uri.parse(uriTexto)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(
                context.contentResolver,
                uri
            )

            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE

                /*
                 * Antes o limite era 2400.
                 * Agora preservamos até 4096 px no maior lado.
                 */
                val maiorLado = maxOf(
                    info.size.width,
                    info.size.height
                )

                val limite = 4096

                if (maiorLado > limite) {
                    val proporcao = limite.toFloat() / maiorLado.toFloat()

                    decoder.setTargetSize(
                        (info.size.width * proporcao).toInt(),
                        (info.size.height * proporcao).toInt()
                    )
                }
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(
                context.contentResolver,
                uri
            ).copy(
                Bitmap.Config.ARGB_8888,
                false
            )
        }
    } catch (_: Exception) {
        null
    }
}
