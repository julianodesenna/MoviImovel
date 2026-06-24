package br.com.moviimovel

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.io.ByteArrayOutputStream
import android.util.Base64
import android.net.Uri
import android.content.Intent
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
import androidx.compose.ui.graphics.TransformOrigin
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

private data class MovimentoCamera(
    val escala: Float,
    val deslocamentoX: Float,
    val deslocamentoY: Float,
    val origemX: Float,
    val origemY: Float
)

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

    var movimentoAtivo by remember {
        mutableStateOf(false)
    }

    var processandoMapa by remember {
        mutableStateOf(false)
    }

    var modoAtual by remember {
        mutableStateOf("Entrada frontal")
    }

    var mensagem by remember {
        mutableStateOf(
            "Selecione uma foto. Os movimentos usam a imagem original."
        )
    }


    var duracaoVideo by remember {
        mutableStateOf(3)
    }

    var gerandoVideo by remember {
        mutableStateOf(false)
    }

    var ultimoVideoUrl by remember {
        mutableStateOf<String?>(null)
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
                movimentoAtivo = false

                mensagem =
                    "Foto carregada em alta qualidade. Escolha um movimento."
            } else {
                mensagem = "Não foi possível carregar essa foto."
            }
        }
    }

    val transition = rememberInfiniteTransition(
        label = "movimento_camera_local"
    )

    val progressoMovimento by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 8500,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "camera_lenta"
    )

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0E1316)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
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
                    text = "Movimentos de câmera com foto original nítida",
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
                        movimentoAtivo -> modoAtual
                        else -> "Foto original"
                    },
                    movimentoAtivo = movimentoAtivo && !mostrandoMapa,
                    modoAtual = modoAtual,
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
                        movimentoAtivo = false
                        mensagem = "Criando mapa de profundidade real..."

                        scope.launch(Dispatchers.Default) {
                            try {
                                val resultado = DepthEstimator(context)
                                    .gerarProfundidade(foto)

                                withContext(Dispatchers.Main) {
                                    depthResult = resultado
                                    mostrandoMapa = true
                                    processandoMapa = false
                                    mensagem = "Mapa pronto. A foto original continua preservada."
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
                        movimentoAtivo = !movimentoAtivo

                        mensagem = if (movimentoAtivo) {
                            "Movimento ativo: $modoAtual."
                        } else {
                            "Movimento pausado."
                        }
                    },
                    enabled = fotoSelecionada != null && !processandoMapa,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (movimentoAtivo) {
                            Color(0xFF7C3030)
                        } else {
                            Color(0xFF6B3D9A)
                        },
                        disabledContainerColor = Color(0xFF30253B)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (movimentoAtivo) {
                            "Parar movimento: $modoAtual"
                        } else {
                            "Testar movimento: $modoAtual"
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        modoAtual = proximoModo(modoAtual)
                        mostrandoMapa = false
                        movimentoAtivo = true
                        mensagem = "Modo selecionado: $modoAtual."
                    },
                    enabled = fotoSelecionada != null && !processandoMapa,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF28343A),
                        disabledContainerColor = Color(0xFF20282E)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Trocar movimento: $modoAtual",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        movimentoAtivo = false
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


                Button(
                    onClick = {
                        duracaoVideo = when (duracaoVideo) {
                            2 -> 3
                            3 -> 5
                            5 -> 10
                            10 -> 15
                            15 -> 20
                            else -> 2
                        }

                        mensagem = "Duração escolhida: ${duracaoVideo} segundos."
                    },
                    enabled = fotoSelecionada != null && !gerandoVideo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF284B63),
                        disabledContainerColor = Color(0xFF20282E)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Duração do vídeo: ${duracaoVideo} segundos",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        val foto = fotoSelecionada ?: return@Button

                        gerandoVideo = true
                        movimentoAtivo = false
                        mostrandoMapa = false
                        mensagem = "Enviando foto e gerando vídeo em ${duracaoVideo} segundos..."

                        scope.launch(Dispatchers.IO) {
                            try {
                                val videoUrl = gerarVideoPVideo(
                                    bitmap = foto,
                                    duracao = duracaoVideo,
                                    modoMovimento = modoAtual
                                )

                                withContext(Dispatchers.Main) {
                                    ultimoVideoUrl = videoUrl
                                    gerandoVideo = false
                                    mensagem = "Vídeo pronto e salvo permanentemente."
                                    abrirVideo(context, videoUrl)
                                }
                            } catch (erro: Exception) {
                                withContext(Dispatchers.Main) {
                                    gerandoVideo = false
                                    mensagem = "Erro ao gerar vídeo: ${erro.message}"
                                }
                            }
                        }
                    },
                    enabled = fotoSelecionada != null && !gerandoVideo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD46A27),
                        disabledContainerColor = Color(0xFF40312A)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (gerandoVideo) {
                            "Gerando vídeo IA..."
                        } else {
                            "Gerar vídeo IA: ${duracaoVideo} s"
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        ultimoVideoUrl?.let {
                            abrirVideo(context, it)
                        }
                    },
                    enabled = ultimoVideoUrl != null && !gerandoVideo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF236D52),
                        disabledContainerColor = Color(0xFF20282E)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Abrir último vídeo salvo",
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

private fun proximoModo(
    modoAtual: String
): String {
    return when (modoAtual) {
        "Entrada frontal" -> "Entrada pela direita"
        "Entrada pela direita" -> "Entrada pela esquerda"
        "Entrada pela esquerda" -> "Aproximação no centro"
        "Aproximação no centro" -> "Saída cinematográfica"
        "Saída cinematográfica" -> "Pan entrando à direita"
        "Pan entrando à direita" -> "Pan entrando à esquerda"
        "Pan entrando à esquerda" -> "Diagonal entrando"
        "Diagonal entrando" -> "Subida entrando"
        "Subida entrando" -> "Descida entrando"
        "Descida entrando" -> "Foco no canto superior"
        "Foco no canto superior" -> "Foco no canto inferior"
        "Foco no canto inferior" -> "Zoom lento profundo"
        "Zoom lento profundo" -> "Passeio amplo"
        else -> "Entrada frontal"
    }
}

@Composable
fun PreviewImagemAltaQualidade(
    bitmap: Bitmap?,
    titulo: String,
    movimentoAtivo: Boolean,
    modoAtual: String,
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
                val movimento = calcularMovimentoLocal(
                    modoAtual = modoAtual,
                    progresso = progresso,
                    ativo = movimentoAtivo
                )

                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = titulo,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(
                                pivotFractionX = movimento.origemX,
                                pivotFractionY = movimento.origemY
                            )

                            scaleX = movimento.escala
                            scaleY = movimento.escala
                            translationX = movimento.deslocamentoX
                            translationY = movimento.deslocamentoY
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

private fun calcularMovimentoLocal(
    modoAtual: String,
    progresso: Float,
    ativo: Boolean
): MovimentoCamera {
    if (!ativo) {
        return MovimentoCamera(
            escala = 1f,
            deslocamentoX = 0f,
            deslocamentoY = 0f,
            origemX = 0.5f,
            origemY = 0.5f
        )
    }

    val centro = progresso - 0.5f

    return when (modoAtual) {
        "Entrada pela direita" -> {
            MovimentoCamera(
                escala = 1.10f + (progresso * 0.22f),
                deslocamentoX = centro * 92f,
                deslocamentoY = centro * -10f,
                origemX = 0.72f,
                origemY = 0.52f
            )
        }

        "Entrada pela esquerda" -> {
            MovimentoCamera(
                escala = 1.10f + (progresso * 0.22f),
                deslocamentoX = centro * -92f,
                deslocamentoY = centro * -10f,
                origemX = 0.28f,
                origemY = 0.52f
            )
        }

        "Aproximação no centro" -> {
            MovimentoCamera(
                escala = 1.04f + (progresso * 0.33f),
                deslocamentoX = centro * 16f,
                deslocamentoY = centro * -14f,
                origemX = 0.5f,
                origemY = 0.5f
            )
        }

        "Saída cinematográfica" -> {
            MovimentoCamera(
                escala = 1.38f - (progresso * 0.30f),
                deslocamentoX = centro * -22f,
                deslocamentoY = centro * 16f,
                origemX = 0.5f,
                origemY = 0.5f
            )
        }

        "Pan entrando à direita" -> {
            MovimentoCamera(
                escala = 1.12f + (progresso * 0.20f),
                deslocamentoX = centro * 118f,
                deslocamentoY = centro * -26f,
                origemX = 0.68f,
                origemY = 0.48f
            )
        }

        "Pan entrando à esquerda" -> {
            MovimentoCamera(
                escala = 1.12f + (progresso * 0.20f),
                deslocamentoX = centro * -118f,
                deslocamentoY = centro * -26f,
                origemX = 0.32f,
                origemY = 0.48f
            )
        }

        "Diagonal entrando" -> {
            MovimentoCamera(
                escala = 1.10f + (progresso * 0.24f),
                deslocamentoX = centro * 92f,
                deslocamentoY = centro * -72f,
                origemX = 0.65f,
                origemY = 0.35f
            )
        }

        "Subida entrando" -> {
            MovimentoCamera(
                escala = 1.09f + (progresso * 0.23f),
                deslocamentoX = centro * 12f,
                deslocamentoY = centro * -118f,
                origemX = 0.5f,
                origemY = 0.28f
            )
        }

        "Descida entrando" -> {
            MovimentoCamera(
                escala = 1.09f + (progresso * 0.23f),
                deslocamentoX = centro * -12f,
                deslocamentoY = centro * 118f,
                origemX = 0.5f,
                origemY = 0.72f
            )
        }

        "Foco no canto superior" -> {
            MovimentoCamera(
                escala = 1.15f + (progresso * 0.22f),
                deslocamentoX = centro * 48f,
                deslocamentoY = centro * -86f,
                origemX = 0.72f,
                origemY = 0.22f
            )
        }

        "Foco no canto inferior" -> {
            MovimentoCamera(
                escala = 1.15f + (progresso * 0.22f),
                deslocamentoX = centro * -48f,
                deslocamentoY = centro * 86f,
                origemX = 0.28f,
                origemY = 0.78f
            )
        }

        "Zoom lento profundo" -> {
            MovimentoCamera(
                escala = 1.02f + (progresso * 0.40f),
                deslocamentoX = centro * 18f,
                deslocamentoY = centro * -18f,
                origemX = 0.5f,
                origemY = 0.48f
            )
        }

        "Passeio amplo" -> {
            MovimentoCamera(
                escala = 1.18f + (progresso * 0.16f),
                deslocamentoX = centro * 128f,
                deslocamentoY = centro * -42f,
                origemX = 0.62f,
                origemY = 0.46f
            )
        }

        else -> {
            MovimentoCamera(
                escala = 1.05f + (progresso * 0.30f),
                deslocamentoX = centro * 24f,
                deslocamentoY = centro * -18f,
                origemX = 0.5f,
                origemY = 0.5f
            )
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


private const val MOVIIMOVEL_VIDEO_WORKER =
    "https://moviimovel-grok-worker.julianoocorretor.workers.dev"

private fun gerarVideoPVideo(
    bitmap: Bitmap,
    duracao: Int,
    modoMovimento: String
): String {
    val imagemBase64 = prepararImagemParaVideo(bitmap)

    val uploadResposta = postJson(
        endpoint = "${MOVIIMOVEL_VIDEO_WORKER}/upload-image",
        body = JSONObject()
            .put("imageBase64", imagemBase64)
            .put("mimeType", "image/jpeg")
    )

    if (!uploadResposta.optBoolean("ok", false)) {
        throw IllegalStateException(
            uploadResposta.optString("error", "Falha ao enviar foto.")
        )
    }

    val imageUrl = uploadResposta.optString("imageUrl")

    if (imageUrl.isBlank()) {
        throw IllegalStateException("O Worker não devolveu a URL da foto.")
    }

    val prompt = criarPromptVideoImovel(modoMovimento)

    val gerarResposta = postJson(
        endpoint = "${MOVIIMOVEL_VIDEO_WORKER}/generate",
        body = JSONObject()
            .put("provider", "preview")
            .put("imageUrl", imageUrl)
            .put("prompt", prompt)
            .put("duration", duracao)
            .put("resolution", "720p")
            .put("aspectRatio", "16:9")
            .put("fps", 24)
    )

    if (!gerarResposta.optBoolean("ok", false)) {
        throw IllegalStateException(
            gerarResposta.optString("error", "Falha ao gerar vídeo.")
        )
    }

    val videoUrl = gerarResposta.optString("videoUrl")

    if (videoUrl.isBlank()) {
        throw IllegalStateException(
            "O vídeo foi gerado, mas não foi devolvida uma URL permanente."
        )
    }

    return videoUrl
}

private fun prepararImagemParaVideo(bitmap: Bitmap): String {
    val maiorLado = maxOf(bitmap.width, bitmap.height)
    val limite = 1920

    val imagemFinal = if (maiorLado > limite) {
        val proporcao = limite.toFloat() / maiorLado.toFloat()

        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * proporcao).toInt(),
            (bitmap.height * proporcao).toInt(),
            true
        )
    } else {
        bitmap
    }

    val output = ByteArrayOutputStream()

    imagemFinal.compress(
        Bitmap.CompressFormat.JPEG,
        84,
        output
    )

    return Base64.encodeToString(
        output.toByteArray(),
        Base64.NO_WRAP
    )
}

private fun criarPromptVideoImovel(
    modoMovimento: String
): String {
    val movimento = when (modoMovimento) {
        "Entrada pela direita" ->
            "Movimento lateral suave da esquerda para a direita."

        "Entrada pela esquerda" ->
            "Movimento lateral suave da direita para a esquerda."

        "Aproximação no centro",
        "Zoom lento profundo" ->
            "Aproximação frontal muito leve e estável."

        "Saída cinematográfica" ->
            "Afastamento suave de câmera."

        "Pan entrando à direita" ->
            "Pan suave para a direita com leve aproximação."

        "Pan entrando à esquerda" ->
            "Pan suave para a esquerda com leve aproximação."

        "Subida entrando" ->
            "Movimento suave de câmera subindo lentamente."

        "Descida entrando" ->
            "Movimento suave de câmera descendo lentamente."

        else ->
            "Movimento suave de câmera para apresentação imobiliária."
    }

    return "$movimento " +
        "Preserve exatamente paredes, piso, teto, janelas, portas, " +
        "cortinas, móveis, iluminação, arquitetura e proporções. " +
        "Não adicionar pessoas, animais, objetos ou mudanças estruturais."
}

private fun postJson(
    endpoint: String,
    body: JSONObject
): JSONObject {
    val connection = URL(endpoint)
        .openConnection() as HttpURLConnection

    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 30_000
        connection.readTimeout = 180_000
        connection.doOutput = true
        connection.setRequestProperty(
            "Content-Type",
            "application/json; charset=utf-8"
        )

        connection.outputStream.use {
            it.write(
                body.toString().toByteArray(
                    Charsets.UTF_8
                )
            )
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val responseText = stream?.bufferedReader()?.use {
            it.readText()
        }.orEmpty()

        if (responseText.isBlank()) {
            throw IllegalStateException(
                "Servidor respondeu sem conteúdo. HTTP $code."
            )
        }

        return JSONObject(responseText)
    } finally {
        connection.disconnect()
    }
}

private fun abrirVideo(
    context: Context,
    videoUrl: String
) {
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(videoUrl)
    ).apply {
        setDataAndType(
            Uri.parse(videoUrl),
            "video/mp4"
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(intent)
}
