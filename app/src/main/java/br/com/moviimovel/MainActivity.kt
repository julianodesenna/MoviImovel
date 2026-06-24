package br.com.moviimovel

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MOVIIMOVEL_VIDEO_WORKER =
    "https://moviimovel-grok-worker.julianoocorretor.workers.dev"

data class MovimentoVideo(
    val nome: String,
    val prompt: String
)

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

    val movimentos = remember {
        listaMovimentosVideo()
    }

    var fotoSelecionada by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var movimentoSelecionado by remember {
        mutableStateOf(movimentos.first())
    }

    var promptVideo by remember {
        mutableStateOf(movimentos.first().prompt)
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

    var mensagem by remember {
        mutableStateOf(
            "Selecione uma foto, escolha o movimento e revise o prompt antes de gerar."
        )
    }

    var mostrandoListaMovimentos by remember {
        mutableStateOf(false)
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
                ultimoVideoUrl = null
                mensagem =
                    "Foto carregada. Escolha o movimento e revise o prompt."
            } else {
                mensagem = "Não foi possível carregar essa foto."
            }
        }
    }

    if (mostrandoListaMovimentos) {
        AlertDialog(
            onDismissRequest = {
                mostrandoListaMovimentos = false
            },
            title = {
                Text(
                    text = "Escolha o movimento",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(430.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    movimentos.forEach { movimento ->
                        TextButton(
                            onClick = {
                                movimentoSelecionado = movimento
                                promptVideo = movimento.prompt
                                mostrandoListaMovimentos = false
                                mensagem =
                                    "Prompt atualizado para: ${movimento.nome}"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = movimento.nome,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                                fontWeight = if (
                                    movimento.nome == movimentoSelecionado.nome
                                ) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrandoListaMovimentos = false
                    }
                ) {
                    Text("Fechar")
                }
            }
        )
    }

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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "MoviImovel Vídeo IA",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Gere vídeos curtos de imóveis com prompt visível e editável.",
                    color = Color(0xFFC6D0D6),
                    fontSize = 14.sp
                )

                Button(
                    onClick = {
                        seletorFoto.launch("image/*")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF284B63)
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
                        fontWeight = FontWeight.Bold
                    )
                }

                if (fotoSelecionada != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF182126)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = fotoSelecionada!!.asImageBitmap(),
                                contentDescription = "Foto selecionada",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF182126)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Movimento escolhido",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = movimentoSelecionado.nome,
                            color = Color(0xFFFFC58B),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Button(
                            onClick = {
                                mostrandoListaMovimentos = true
                            },
                            enabled = !gerandoVideo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF304D5B)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Escolher outro movimento",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Text(
                    text = "Prompt que será enviado",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = promptVideo,
                    onValueChange = {
                        promptVideo = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(245.dp),
                    enabled = !gerandoVideo,
                    label = {
                        Text("Você pode editar este texto")
                    },
                    minLines = 8
                )

                Text(
                    text = "Duração",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(2, 3, 5, 10, 15, 20).chunked(3).forEach { linha ->
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            linha.forEach { segundos ->
                                Button(
                                    onClick = {
                                        duracaoVideo = segundos
                                        mensagem =
                                            "Duração escolhida: $segundos segundos."
                                    },
                                    enabled = !gerandoVideo,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (
                                            duracaoVideo == segundos
                                        ) {
                                            Color(0xFFD46A27)
                                        } else {
                                            Color(0xFF304D5B)
                                        }
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "${segundos}s",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val foto = fotoSelecionada

                        if (foto == null) {
                            mensagem = "Selecione uma foto antes de gerar."
                            return@Button
                        }

                        if (promptVideo.isBlank()) {
                            mensagem = "O prompt não pode ficar vazio."
                            return@Button
                        }

                        gerandoVideo = true
                        mensagem =
                            "Enviando foto e gerando vídeo de $duracaoVideo segundos..."

                        scope.launch(Dispatchers.IO) {
                            try {
                                val videoUrl = gerarVideoPVideo(
                                    bitmap = foto,
                                    duracao = duracaoVideo,
                                    prompt = promptVideo
                                )

                                withContext(Dispatchers.Main) {
                                    ultimoVideoUrl = videoUrl
                                    gerandoVideo = false
                                    mensagem =
                                        "Vídeo pronto e salvo permanentemente no R2."
                                    abrirVideo(context, videoUrl)
                                }
                            } catch (erro: Exception) {
                                withContext(Dispatchers.Main) {
                                    gerandoVideo = false
                                    mensagem =
                                        "Erro ao gerar vídeo: ${erro.message}"
                                }
                            }
                        }
                    },
                    enabled = fotoSelecionada != null && !gerandoVideo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD46A27),
                        disabledContainerColor = Color(0xFF42342C)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (gerandoVideo) {
                            "Gerando vídeo..."
                        } else {
                            "Gerar vídeo IA • ${duracaoVideo}s"
                        },
                        color = Color.White,
                        fontSize = 16.sp,
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
                        text = "Abrir último vídeo",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        ultimoVideoUrl?.let {
                            salvarVideoNoCelular(context, it)
                            mensagem =
                                "Download iniciado. Procure o MP4 na pasta Download."
                        }
                    },
                    enabled = ultimoVideoUrl != null && !gerandoVideo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5A3F8C),
                        disabledContainerColor = Color(0xFF20282E)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Salvar vídeo no celular",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF172228)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = mensagem,
                        color = Color(0xFFF2D8AF),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    )
                }
            }
        }
    }
}

private fun listaMovimentosVideo(): List<MovimentoVideo> {
    fun prompt(movimento: String): String {
        return """
$movimento

Crie um vídeo curto e realista para apresentação imobiliária.
Preserve rigorosamente paredes, piso, teto, portas, janelas, cortinas,
móveis, iluminação, arquitetura, materiais e proporções originais.

Não adicionar pessoas, animais, objetos, reformas, mudanças estruturais,
deformações, duplicações, textos ou qualquer elemento inexistente na foto.
A câmera deve se mover de forma lenta, suave, profissional e estável.
        """.trimIndent()
    }

    return listOf(
        MovimentoVideo(
            "Aproximação frontal suave",
            prompt(
                "A câmera faz uma aproximação frontal muito leve e centralizada."
            )
        ),
        MovimentoVideo(
            "Afastamento suave",
            prompt(
                "A câmera se afasta lentamente do ambiente, ampliando a visão."
            )
        ),
        MovimentoVideo(
            "Pan da esquerda para a direita",
            prompt(
                "A câmera desliza lateralmente da esquerda para a direita."
            )
        ),
        MovimentoVideo(
            "Pan da direita para a esquerda",
            prompt(
                "A câmera desliza lateralmente da direita para a esquerda."
            )
        ),
        MovimentoVideo(
            "Zoom leve para dentro",
            prompt(
                "A câmera faz um zoom muito leve para dentro, sem deformar o ambiente."
            )
        ),
        MovimentoVideo(
            "Zoom leve para fora",
            prompt(
                "A câmera faz um zoom muito leve para fora, revelando mais do ambiente."
            )
        ),
        MovimentoVideo(
            "Subida suave",
            prompt(
                "A câmera sobe lentamente, mantendo o ambiente estável."
            )
        ),
        MovimentoVideo(
            "Descida suave",
            prompt(
                "A câmera desce lentamente, mantendo o ambiente estável."
            )
        ),
        MovimentoVideo(
            "Diagonal para cima à direita",
            prompt(
                "A câmera faz um deslocamento diagonal suave para cima e para a direita."
            )
        ),
        MovimentoVideo(
            "Diagonal para cima à esquerda",
            prompt(
                "A câmera faz um deslocamento diagonal suave para cima e para a esquerda."
            )
        ),
        MovimentoVideo(
            "Diagonal para baixo à direita",
            prompt(
                "A câmera faz um deslocamento diagonal suave para baixo e para a direita."
            )
        ),
        MovimentoVideo(
            "Diagonal para baixo à esquerda",
            prompt(
                "A câmera faz um deslocamento diagonal suave para baixo e para a esquerda."
            )
        ),
        MovimentoVideo(
            "Órbita leve para a direita",
            prompt(
                "A câmera faz uma órbita muito leve para a direita, sem alterar a geometria."
            )
        ),
        MovimentoVideo(
            "Órbita leve para a esquerda",
            prompt(
                "A câmera faz uma órbita muito leve para a esquerda, sem alterar a geometria."
            )
        ),
        MovimentoVideo(
            "Entrada pela porta",
            prompt(
                "A câmera avança suavemente como se estivesse entrando pelo acesso principal."
            )
        ),
        MovimentoVideo(
            "Aproximação da janela",
            prompt(
                "A câmera se aproxima suavemente da janela, valorizando luz e profundidade."
            )
        ),
        MovimentoVideo(
            "Aproximação da varanda",
            prompt(
                "A câmera se aproxima suavemente da varanda ou abertura externa."
            )
        ),
        MovimentoVideo(
            "Revelação panorâmica",
            prompt(
                "A câmera faz uma revelação panorâmica lenta, mostrando o ambiente por completo."
            )
        ),
        MovimentoVideo(
            "Câmera estável com profundidade discreta",
            prompt(
                "A câmera permanece quase estável, com profundidade visual muito discreta."
            )
        ),
        MovimentoVideo(
            "Movimento imobiliário cinematográfico",
            prompt(
                "A câmera executa um movimento cinematográfico leve, elegante e natural."
            )
        )
    )
}

private fun gerarVideoPVideo(
    bitmap: Bitmap,
    duracao: Int,
    prompt: String
): String {
    val imagemBase64 = prepararImagemParaVideo(bitmap)

    val uploadResposta = postJson(
        endpoint = "$MOVIIMOVEL_VIDEO_WORKER/upload-image",
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
        throw IllegalStateException(
            "O Worker não devolveu a URL da foto enviada."
        )
    }

    val gerarResposta = postJson(
        endpoint = "$MOVIIMOVEL_VIDEO_WORKER/generate",
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
                body.toString().toByteArray(Charsets.UTF_8)
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
    val uri = Uri.parse(videoUrl)

    val intent = Intent(
        Intent.ACTION_VIEW,
        uri
    ).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(intent)
}

private fun salvarVideoNoCelular(
    context: Context,
    videoUrl: String
) {
    val dataHora = SimpleDateFormat(
        "yyyyMMdd_HHmmss",
        Locale.US
    ).format(Date())

    val fileName = "MoviImovel_${dataHora}.mp4"

    val request = DownloadManager.Request(
        Uri.parse(videoUrl)
    ).apply {
        setTitle(fileName)
        setDescription("Vídeo gerado pelo MoviImovel")
        setMimeType("video/mp4")
        setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        )
        setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            fileName
        )
        setAllowedOverMetered(true)
        setAllowedOverRoaming(true)
    }

    val downloadManager = context.getSystemService(
        Context.DOWNLOAD_SERVICE
    ) as DownloadManager

    downloadManager.enqueue(request)
}

private fun carregarBitmapAltaQualidade(
    context: Context,
    uriTexto: String
): Bitmap? {
    return try {
        val uri = Uri.parse(uriTexto)

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
                    val proporcao =
                        limite.toFloat() / maiorLado.toFloat()

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
