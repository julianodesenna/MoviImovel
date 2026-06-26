package br.com.moviimovel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MOVIIMOVEL_VIDEO_WORKER =
    "https://moviimovel-grok-worker.julianoocorretor.workers.dev"

private const val MAX_FOTOS_POR_VIDEO = 5

private object PremiumUi {
    val Fundo = Color(0xFFE6E7E9)
    val FundoSecundario = Color(0xFFF1F1F2)
    val Card = Color(0xFFF8F8F7)
    val CardInterno = Color(0xFFF4F4F3)
    val Texto = Color(0xFF161616)
    val TextoSuave = Color(0xFF5E5E5E)
    val PretoPremium = Color(0xFF111111)
    val PretoPremium2 = Color(0xFF1E1E1E)
    val Dourado = Color(0xFFB89349)
    val DouradoClaro = Color(0xFFE7D4A7)
    val Borda = Color(0xFFD8D5CF)
    val BordaEscura = Color(0xFF2D2D2D)
    val VermelhoSuave = Color(0xFF9D5E5E)
}

data class MovimentoVideo(
    val nome: String,
    val prompt: String
)

data class CenaVideo(
    val id: Long,
    val uri: Uri,
    val nome: String,
    val movimento: MovimentoVideo,
    val prompt: String,
    val duracao: Int
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

    val recomendados = remember {
        movimentos.filter {
            it.nome in nomesMovimentosRecomendados()
        }
    }

    var cenas by remember {
        mutableStateOf<List<CenaVideo>>(emptyList())
    }

    var modoQualidade by remember {
        mutableStateOf("Rascunho 720p")
    }

    var gerandoVideo by remember {
        mutableStateOf(false)
    }

    var cenaAtualGeracao by remember {
        mutableStateOf(0)
    }

    var progressoGeralReal by remember {
        mutableStateOf(0)
    }

    var inicioGeracaoMillis by remember {
        mutableStateOf<Long?>(null)
    }

    var segundosDecorridos by remember {
        mutableStateOf(0L)
    }

    var ultimoVideoUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var mensagem by remember {
        mutableStateOf(
            "Selecione uma foto ou várias fotos para criar as cenas."
        )
    }

    var mostrarProgressoDownload by remember {
        mutableStateOf(false)
    }

    var bytesBaixados by remember {
        mutableStateOf(0L)
    }

    var bytesTotais by remember {
        mutableStateOf(-1L)
    }

    var cenaDownloadAtual by remember {
        mutableStateOf("")
    }

    var cenaParaMovimentoId by remember {
        mutableStateOf<Long?>(null)
    }

    var confirmarGeracao by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(gerandoVideo, inicioGeracaoMillis) {
        while (gerandoVideo && inicioGeracaoMillis != null) {
            segundosDecorridos =
                (System.currentTimeMillis() - inicioGeracaoMillis!!) / 1000L
            delay(1000)
        }
    }

    val seletorFotos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) {
            return@rememberLauncherForActivityResult
        }

        val selecionadas = uris.take(MAX_FOTOS_POR_VIDEO)

        cenas = selecionadas.mapIndexed { index, uri ->
            val movimento = recomendados[
                index % recomendados.size
            ]

            CenaVideo(
                id = System.nanoTime() + index,
                uri = uri,
                nome = "Foto ${index + 1}",
                movimento = movimento,
                prompt = movimento.prompt,
                duracao = 3
            )
        }

        ultimoVideoUri = null

        mensagem = if (uris.size > MAX_FOTOS_POR_VIDEO) {
            "Foram selecionadas as primeiras $MAX_FOTOS_POR_VIDEO fotos."
        } else {
            "${cenas.size} foto(s) preparada(s). Revise cada cena antes de gerar."
        }
    }

    val seletorUmaFoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val movimento = recomendados.first()

            cenas = listOf(
                CenaVideo(
                    id = System.nanoTime(),
                    uri = uri,
                    nome = "Foto 1",
                    movimento = movimento,
                    prompt = movimento.prompt,
                    duracao = 3
                )
            )

            ultimoVideoUri = null

            mensagem =
                "Foto pronta. Escolha movimento, duração e revise o prompt."
        }
    }

    val cenaSelecionada = cenas.firstOrNull {
        it.id == cenaParaMovimentoId
    }

    if (cenaSelecionada != null) {
        TelaEscolherMovimento(
            cena = cenaSelecionada,
            recomendados = recomendados,
            todos = movimentos,
            onVoltar = {
                cenaParaMovimentoId = null
            },
            onEscolher = { movimento ->
                cenas = cenas.map { cena ->
                    if (cena.id == cenaSelecionada.id) {
                        cena.copy(
                            movimento = movimento,
                            prompt = movimento.prompt
                        )
                    } else {
                        cena
                    }
                }

                mensagem =
                    "Movimento atualizado em ${cenaSelecionada.nome}."

                cenaParaMovimentoId = null
            }
        )

        return
    }

    if (confirmarGeracao) {
        val totalSegundos = cenas.sumOf {
            it.duracao
        }

        val custoDolar =
            custoPorSegundo(modoQualidade) * totalSegundos

        val custoReal = custoDolar * 5.22

        AlertDialog(
            onDismissRequest = {
                if (!gerandoVideo) {
                    confirmarGeracao = false
                }
            },
            title = {
                Text(
                    text = "Confirmar geração",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Cenas: ${cenas.size}")
                    Text("Duração total: $totalSegundos segundos")
                    Text("Qualidade: $modoQualidade")

                    Text(
                        text =
                            "Custo estimado: US$ " +
                                String.format(
                                    Locale.US,
                                    "%.3f",
                                    custoDolar
                                ) +
                                " • cerca de R$ " +
                                String.format(
                                    Locale.US,
                                    "%.2f",
                                    custoReal
                                ),
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            "Cada foto será gerada separadamente. Depois o aplicativo junta as cenas e salva o vídeo final na Galeria.",
                        fontSize = 13.sp,
                        color = Color(0xFF5C5C5C)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !gerandoVideo,
                    onClick = {
                        confirmarGeracao = false
                    }
                ) {
                    Text("Cancelar")
                }
            },
            confirmButton = {
                Button(
                    enabled = !gerandoVideo,
                    onClick = {
                        confirmarGeracao = false
                        gerandoVideo = true
                        ultimoVideoUri = null
                        cenaAtualGeracao = 0
                        progressoGeralReal = 0
                        segundosDecorridos = 0
                        inicioGeracaoMillis = System.currentTimeMillis()

                        scope.launch(Dispatchers.IO) {
                            val temporarios = mutableListOf<File>()

                            try {
                                val provider =
                                    if (modoQualidade == "Rascunho 720p") {
                                        "preview"
                                    } else {
                                        "pvideo"
                                    }

                                val resolution =
                                    if (modoQualidade == "Final 1080p") {
                                        "1080p"
                                    } else {
                                        "720p"
                                    }

                                cenas.forEachIndexed { index, cena ->
                                    withContext(Dispatchers.Main) {
                                        cenaAtualGeracao = index + 1
                                        mensagem =
                                            "Vídeo ${index + 1} de ${cenas.size}: preparando ${cena.nome}."
                                    }

                                    val bitmap =
                                        carregarBitmapAltaQualidade(
                                            context,
                                            cena.uri.toString()
                                        )
                                            ?: throw IllegalStateException(
                                                "Não foi possível abrir ${cena.nome}."
                                            )

                                    val videoUrl =
                                        gerarVideoPVideo(
                                            bitmap = bitmap,
                                            duracao = cena.duracao,
                                            prompt = cena.prompt,
                                            provider = provider,
                                            resolution = resolution,
                                            aoAtualizarStatus = { status ->
                                                scope.launch(Dispatchers.Main) {
                                                    mensagem =
                                                        "Vídeo ${index + 1} de ${cenas.size}: " +
                                                            status
                                                }
                                            }
                                        )

                                    val arquivoCena = File(
                                        context.cacheDir,
                                        "moviimovel_cena_${index}_${System.currentTimeMillis()}.mp4"
                                    )

                                    withContext(Dispatchers.Main) {
                                        mostrarProgressoDownload = true
                                        bytesBaixados = 0L
                                        bytesTotais = -1L
                                        cenaDownloadAtual =
                                            "Baixando vídeo ${index + 1} de ${cenas.size}"
                                        mensagem =
                                            "Vídeo ${index + 1} de ${cenas.size}: baixando arquivo..."
                                    }

                                    baixarVideoTemporario(
                                        videoUrl = videoUrl,
                                        destino = arquivoCena
                                    ) { recebidos, total ->
                                        scope.launch(Dispatchers.Main) {
                                            bytesBaixados = recebidos
                                            bytesTotais = total

                                            if (total > 0L) {
                                                val percentual =
                                                    ((recebidos * 100L) / total)
                                                        .coerceIn(0L, 100L)

                                                mensagem =
                                                    "Vídeo ${index + 1} de ${cenas.size}: " +
                                                        "baixando $percentual%."
                                            } else {
                                                mensagem =
                                                    "Vídeo ${index + 1} de ${cenas.size}: " +
                                                        "baixando arquivo..."
                                            }
                                        }
                                    }

                                    withContext(Dispatchers.Main) {
                                        mostrarProgressoDownload = false
                                    }

                                    temporarios += arquivoCena

                                    withContext(Dispatchers.Main) {
                                        progressoGeralReal =
                                            ((index + 1) * 100) / cenas.size
                                        mensagem =
                                            "Vídeo ${index + 1} de ${cenas.size} concluído. " +
                                                "Progresso geral real: $progressoGeralReal%."
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    mostrarProgressoDownload = false
                                    mensagem =
                                        "Montando o vídeo final no celular..."
                                }

                                val resultado = File(
                                    context.cacheDir,
                                    "moviimovel_final_${System.currentTimeMillis()}.mp4"
                                )

                                if (temporarios.size == 1) {
                                    temporarios.first().copyTo(
                                        resultado,
                                        overwrite = true
                                    )
                                } else {
                                    juntarClipesMp4(
                                        temporarios,
                                        resultado
                                    )
                                }

                                val uriFinal =
                                    salvarVideoNaGaleria(
                                        context,
                                        resultado
                                    )

                                resultado.delete()

                                temporarios.forEach {
                                    it.delete()
                                }

                                withContext(Dispatchers.Main) {
                                    mostrarProgressoDownload = false
                                    ultimoVideoUri = uriFinal
                                    mensagem =
                                        "Vídeo final salvo na Galeria em Movies/MoviImovel."
                                    gerandoVideo = false
                                    inicioGeracaoMillis = null
                                }
                            } catch (erro: Exception) {
                                temporarios.forEach {
                                    it.delete()
                                }

                                withContext(Dispatchers.Main) {
                                    mostrarProgressoDownload = false
                                    gerandoVideo = false
                                    inicioGeracaoMillis = null

                                    mensagem =
                                        "Não foi possível concluir o vídeo: " +
                                            (erro.message ?: "erro sem detalhe")
                                }
                            }
                        }
                    }
                ) {
                    Text("Gerar vídeo")
                }
            }
        )
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = PremiumUi.Fundo
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = PremiumUi.Card
                        ),
                        border = BorderStroke(1.dp, PremiumUi.DouradoClaro)
                    ) {
                        Box(
                            modifier = Modifier.size(58.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "M",
                                color = PremiumUi.Texto,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = "Movitmovel Vídeo IA",
                        color = PremiumUi.Texto,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = PremiumUi.Card
                        ),
                        border = BorderStroke(1.dp, PremiumUi.DouradoClaro)
                    ) {
                        Box(
                            modifier = Modifier.size(50.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "♛",
                                color = PremiumUi.Dourado,
                                fontSize = 22.sp
                            )
                        }
                    }
                }

                if (gerandoVideo) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = PremiumUi.Card
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "PROGRESSO REAL DA GERAÇÃO",
                                color = PremiumUi.Texto,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )

                            Text(
                                text = "Vídeo $cenaAtualGeracao de ${cenas.size}",
                                color = PremiumUi.Texto,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Progresso geral real: $progressoGeralReal%",
                                color = PremiumUi.Texto
                            )

                            Text(
                                text = "Tempo decorrido: ${formatarTempoDecorrido(segundosDecorridos)}",
                                color = PremiumUi.TextoSuave
                            )

                            Text(
                                text = "O status da IA aparece abaixo. A porcentagem só sobe quando cada vídeo termina.",
                                color = PremiumUi.TextoSuave,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Text(
                    text =
                        "Cada foto vira uma cena independente. O aplicativo junta tudo e salva o resultado final automaticamente.",
                    color = PremiumUi.TextoSuave,
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            seletorUmaFoto.launch("image/*")
                        },
                        enabled = !gerandoVideo,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        border = BorderStroke(1.dp, PremiumUi.Borda),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PremiumUi.Card
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = "Uma foto",
                            color = PremiumUi.Texto,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            seletorFotos.launch("image/*")
                        },
                        enabled = !gerandoVideo,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        border = BorderStroke(1.dp, PremiumUi.Dourado),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PremiumUi.PretoPremium
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = "Várias fotos",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (cenas.isNotEmpty()) {
                    Button(
                        onClick = {
                            cenas = cenas.mapIndexed { index, cena ->
                                val movimento =
                                    recomendados[
                                        index % recomendados.size
                                    ]

                                cena.copy(
                                    movimento = movimento,
                                    prompt = movimento.prompt
                                )
                            }

                            mensagem =
                                "Movimentos automáticos seguros aplicados."
                        },
                        enabled = !gerandoVideo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        border = BorderStroke(1.dp, PremiumUi.Dourado),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PremiumUi.PretoPremium
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text =
                                "Aplicar movimentos automáticos seguros",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Cenas do vídeo",
                        color = PremiumUi.Texto,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    cenas.forEachIndexed { index, cena ->
                        CardCena(
                            cena = cena,
                            numero = index + 1,
                            bloqueado = gerandoVideo,
                            onAlterarMovimento = {
                                cenaParaMovimentoId = cena.id
                            },
                            onAlterarPrompt = { novoPrompt ->
                                cenas = cenas.map {
                                    if (it.id == cena.id) {
                                        it.copy(
                                            prompt = novoPrompt
                                        )
                                    } else {
                                        it
                                    }
                                }
                            },
                            onRestaurarPrompt = {
                                cenas = cenas.map {
                                    if (it.id == cena.id) {
                                        it.copy(
                                            prompt = it.movimento.prompt
                                        )
                                    } else {
                                        it
                                    }
                                }
                            },
                            onAlterarDuracao = { segundos ->
                                cenas = cenas.map {
                                    if (it.id == cena.id) {
                                        it.copy(
                                            duracao = segundos
                                        )
                                    } else {
                                        it
                                    }
                                }
                            },
                            onRemover = {
                                cenas = cenas.filterNot {
                                    it.id == cena.id
                                }

                                mensagem = "Cena removida."
                            }
                        )
                    }

                    Text(
                        text = "Qualidade",
                        color = PremiumUi.Texto,
                        fontWeight = FontWeight.Bold
                    )

                    QualidadeSelector(
                        selecionada = modoQualidade,
                        habilitado = !gerandoVideo,
                        onEscolher = {
                            modoQualidade = it
                        }
                    )

                    Text(
                        text = textoQualidade(modoQualidade),
                        color = PremiumUi.TextoSuave,
                        fontSize = 13.sp
                    )

                    Button(
                        enabled =
                            cenas.isNotEmpty() &&
                                !gerandoVideo,
                        onClick = {
                            if (cenas.any {
                                    it.prompt.isBlank()
                                }
                            ) {
                                mensagem =
                                    "Revise os prompts: nenhum campo pode ficar vazio."
                            } else {
                                confirmarGeracao = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PremiumUi.PretoPremium,
                            disabledContainerColor = Color(0xFF707070)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text =
                                if (gerandoVideo) {
                                    "Gerando vídeo..."
                                } else {
                                    "Gerar ${cenas.size} cena(s) e salvar vídeo final"
                                },
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = {
                        ultimoVideoUri?.let {
                            abrirVideoLocal(
                                context,
                                it
                            )
                        }
                    },
                    enabled =
                        ultimoVideoUri != null &&
                            !gerandoVideo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PremiumUi.PretoPremium,
                        disabledContainerColor = Color(0xFF707070)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Abrir último vídeo salvo",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (mostrarProgressoDownload) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = PremiumUi.Card
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = cenaDownloadAtual,
                                color = PremiumUi.Texto,
                                fontWeight = FontWeight.Bold
                            )

                            if (bytesTotais > 0L) {
                                val progresso =
                                    (bytesBaixados.toFloat() /
                                        bytesTotais.toFloat())
                                        .coerceIn(0f, 1f)

                                val percentual =
                                    (progresso * 100f).toInt()

                                LinearProgressIndicator(
                                    progress = progresso,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = PremiumUi.Dourado,
                                    trackColor = PremiumUi.Borda
                                )

                                Text(
                                    text =
                                        "$percentual% • " +
                                            formatarBytes(bytesBaixados) +
                                            " de " +
                                            formatarBytes(bytesTotais),
                                    color = PremiumUi.TextoSuave,
                                    fontSize = 13.sp
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = PremiumUi.Dourado,
                                    trackColor = PremiumUi.Borda
                                )

                                Text(
                                    text = "Recebendo o vídeo...",
                                    color = PremiumUi.TextoSuave,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = PremiumUi.Card
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = mensagem,
                        color = PremiumUi.Texto,
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

@Composable
private fun CardCena(
    cena: CenaVideo,
    numero: Int,
    bloqueado: Boolean,
    onAlterarMovimento: () -> Unit,
    onAlterarPrompt: (String) -> Unit,
    onRestaurarPrompt: () -> Unit,
    onAlterarDuracao: (Int) -> Unit,
    onRemover: () -> Unit
) {
    val context = LocalContext.current

    val preview = remember(cena.uri) {
        carregarBitmapPreview(
            context,
            cena.uri.toString()
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = PremiumUi.Card
        ),
        border = BorderStroke(1.dp, PremiumUi.Borda),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (preview != null) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "Prévia da cena",
                        modifier = Modifier
                            .size(74.dp)
                            .clip(
                                RoundedCornerShape(10.dp)
                            ),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(
                    modifier = Modifier.width(10.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "$numero. ${cena.nome}",
                        color = PremiumUi.Texto,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "${cena.duracao} segundos",
                        color = PremiumUi.TextoSuave,
                        fontSize = 13.sp
                    )
                }

                TextButton(
                    enabled = !bloqueado,
                    onClick = onRemover
                ) {
                    Text(
                        text = "Remover",
                        color = PremiumUi.VermelhoSuave
                    )
                }
            }

            Text(
                text = "Movimento: ${cena.movimento.nome}",
                color = PremiumUi.Dourado,
                fontWeight = FontWeight.SemiBold
            )

            Button(
                enabled = !bloqueado,
                onClick = onAlterarMovimento,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                border = BorderStroke(1.dp, PremiumUi.BordaEscura),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PremiumUi.Card
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Escolher movimento",
                    color = PremiumUi.Texto,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "Prompt desta cena",
                color = PremiumUi.Texto,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = cena.prompt,
                onValueChange = onAlterarPrompt,
                enabled = !bloqueado,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                minLines = 6,
                label = {
                    Text(
                        "Você pode editar este texto"
                    )
                },
                colors = campoCores()
            )

            TextButton(
                enabled = !bloqueado,
                onClick = onRestaurarPrompt
            ) {
                Text(
                    text = "Restaurar prompt automático",
                    color = PremiumUi.Dourado
                )
            }

            Text(
                text = "Duração",
                color = PremiumUi.Texto,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(2, 3, 5, 10).forEach { segundos ->
                    Button(
                        enabled = !bloqueado,
                        onClick = {
                            onAlterarDuracao(segundos)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                if (cena.duracao == segundos) {
                                    PremiumUi.PretoPremium
                                } else {
                                    PremiumUi.FundoSecundario
                                }
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${segundos}s",
                            color = if (cena.duracao == segundos) Color.White else PremiumUi.Texto,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelaEscolherMovimento(
    cena: CenaVideo,
    recomendados: List<MovimentoVideo>,
    todos: List<MovimentoVideo>,
    onVoltar: () -> Unit,
    onEscolher: (MovimentoVideo) -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = PremiumUi.Fundo
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onVoltar
                ) {
                    Text(
                        text = "← Voltar para as cenas",
                        color = PremiumUi.Dourado
                    )
                }

                Text(
                    text = "Escolher movimento",
                    color = PremiumUi.Texto,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text =
                        "${cena.nome}. Ao escolher outro movimento, o prompt automático correspondente será restaurado. Depois ele continuará editável.",
                    color = PremiumUi.TextoSuave,
                    fontSize = 14.sp
                )

                Text(
                    text = "RECOMENDADOS PARA IMÓVEIS",
                    color = PremiumUi.Dourado,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        top = 10.dp
                    )
                )

                recomendados.forEach { movimento ->
                    MovimentoLinha(
                        movimento = movimento,
                        selecionado =
                            movimento.nome ==
                                cena.movimento.nome,
                        onClick = {
                            onEscolher(movimento)
                        }
                    )
                }

                Text(
                    text = "TODOS OS MOVIMENTOS",
                    color = PremiumUi.Dourado,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        top = 12.dp
                    )
                )

                todos.forEach { movimento ->
                    MovimentoLinha(
                        movimento = movimento,
                        selecionado =
                            movimento.nome ==
                                cena.movimento.nome,
                        onClick = {
                            onEscolher(movimento)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MovimentoLinha(
    movimento: MovimentoVideo,
    selecionado: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                if (selecionado) {
                    PremiumUi.PretoPremium
                } else {
                    PremiumUi.Card
                }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = movimento.nome,
                color = if (selecionado) Color.White else PremiumUi.Texto,
                textAlign = TextAlign.Start,
                fontWeight =
                    if (selecionado) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun QualidadeSelector(
    selecionada: String,
    habilitado: Boolean,
    onEscolher: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            "Rascunho 720p",
            "Final 720p",
            "Final 1080p"
        ).chunked(2).forEach { linha ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                linha.forEach { qualidade ->
                    Button(
                        enabled = habilitado,
                        onClick = {
                            onEscolher(qualidade)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                if (selecionada == qualidade) {
                                    PremiumUi.PretoPremium
                                } else {
                                    PremiumUi.FundoSecundario
                                }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = qualidade,
                            color = if (selecionada == qualidade) Color.White else PremiumUi.Texto,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun campoCores() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = PremiumUi.Texto,
        unfocusedTextColor = PremiumUi.Texto,
        focusedBorderColor = PremiumUi.Dourado,
        unfocusedBorderColor = PremiumUi.Borda,
        focusedLabelColor = PremiumUi.Dourado,
        unfocusedLabelColor = PremiumUi.TextoSuave,
        cursorColor = PremiumUi.Dourado
    )

private fun nomesMovimentosRecomendados() = setOf(
    "Pan esquerda → direita lento",
    "Pan direita → esquerda lento",
    "Aproximação frontal suave",
    "Afastamento suave",
    "Zoom leve para dentro",
    "Zoom leve para fora",
    "Câmera quase parada"
)

private fun custoPorSegundo(
    qualidade: String
): Double {
    return when (qualidade) {
        "Rascunho 720p" -> 0.005
        "Final 720p" -> 0.02
        else -> 0.04
    }
}

private fun textoQualidade(
    qualidade: String
): String {
    return when (qualidade) {
        "Rascunho 720p" ->
            "Modo barato para testar. Pode ficar mais suave ou borrado."

        "Final 720p" ->
            "Mais estável que o rascunho, mantendo 720p."

        else ->
            "Maior qualidade disponível neste app. Mais caro que 720p."
    }
}

private fun listaMovimentosVideo(): List<MovimentoVideo> {
    fun prompt(
        comando: String,
        bloqueios: String
    ): String {
        return """
COMANDO PRINCIPAL DE CÂMERA:
$comando

REGRAS OBRIGATÓRIAS:
Execute somente o movimento descrito acima.
$bloqueios
Mantenha o enquadramento inicial reconhecível.
Não invente outro movimento e não transforme em aproximação automática.

Vídeo imobiliário realista. Preserve rigorosamente paredes, piso, teto, portas,
janelas, cortinas, pia, fogão, móveis, iluminação, arquitetura, materiais e proporções.
Não adicionar pessoas, silhuetas, animais, objetos, textos, reformas, deformações,
duplicações, itens surgindo, itens desaparecendo ou mudanças estruturais.
        """.trimIndent()
    }

    return listOf(
        MovimentoVideo(
            "Pan esquerda → direita lento",
            prompt(
                "Deslize a câmera horizontalmente da esquerda para a direita, em linha reta e lentamente.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO suba. NÃO desça. NÃO faça diagonal."
            )
        ),
        MovimentoVideo(
            "Pan esquerda → direita médio",
            prompt(
                "Deslize a câmera horizontalmente da esquerda para a direita, em linha reta, com velocidade média.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO suba. NÃO desça. NÃO faça diagonal."
            )
        ),
        MovimentoVideo(
            "Pan direita → esquerda lento",
            prompt(
                "Deslize a câmera horizontalmente da direita para a esquerda, em linha reta e lentamente.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO suba. NÃO desça. NÃO faça diagonal."
            )
        ),
        MovimentoVideo(
            "Pan direita → esquerda médio",
            prompt(
                "Deslize a câmera horizontalmente da direita para a esquerda, em linha reta, com velocidade média.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO suba. NÃO desça. NÃO faça diagonal."
            )
        ),
        MovimentoVideo(
            "Travelling curto para direita",
            prompt(
                "Mova a câmera fisicamente poucos centímetros para a direita, mantendo linhas verticais naturais.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO faça órbita. NÃO mova verticalmente."
            )
        ),
        MovimentoVideo(
            "Travelling curto para esquerda",
            prompt(
                "Mova a câmera fisicamente poucos centímetros para a esquerda, mantendo linhas verticais naturais.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO faça órbita. NÃO mova verticalmente."
            )
        ),
        MovimentoVideo(
            "Aproximação frontal suave",
            prompt(
                "Aproxime a câmera lentamente para frente, mantendo o centro do ambiente como referência.",
                "NÃO faça pan lateral. NÃO suba. NÃO desça. NÃO faça órbita. NÃO faça diagonal."
            )
        ),
        MovimentoVideo(
            "Aproximação frontal média",
            prompt(
                "Aproxime a câmera para frente com intensidade média, preservando todos os objetos e proporções.",
                "NÃO faça pan lateral. NÃO suba. NÃO desça. NÃO faça órbita. NÃO faça diagonal."
            )
        ),
        MovimentoVideo(
            "Afastamento suave",
            prompt(
                "Afaste a câmera lentamente para trás, revelando um pouco mais do ambiente.",
                "NÃO faça pan lateral. NÃO aproxime. NÃO suba. NÃO desça. NÃO faça diagonal."
            )
        ),
        MovimentoVideo(
            "Afastamento médio",
            prompt(
                "Afaste a câmera para trás com intensidade média, revelando mais do ambiente.",
                "NÃO faça pan lateral. NÃO aproxime. NÃO suba. NÃO desça. NÃO faça diagonal."
            )
        ),
        MovimentoVideo(
            "Zoom leve para dentro",
            prompt(
                "Faça somente zoom óptico leve para dentro, sem deslocar a posição da câmera.",
                "NÃO faça pan. NÃO faça dolly para frente. NÃO mova verticalmente. NÃO altere a perspectiva."
            )
        ),
        MovimentoVideo(
            "Zoom médio para dentro",
            prompt(
                "Faça somente zoom óptico médio para dentro, sem deslocar a posição da câmera.",
                "NÃO faça pan. NÃO faça dolly para frente. NÃO mova verticalmente. NÃO altere a perspectiva."
            )
        ),
        MovimentoVideo(
            "Zoom leve para fora",
            prompt(
                "Faça somente zoom óptico leve para fora, sem deslocar a posição da câmera.",
                "NÃO faça pan. NÃO afaste a câmera fisicamente. NÃO mova verticalmente. NÃO altere a perspectiva."
            )
        ),
        MovimentoVideo(
            "Zoom médio para fora",
            prompt(
                "Faça somente zoom óptico médio para fora, sem deslocar a posição da câmera.",
                "NÃO faça pan. NÃO afaste a câmera fisicamente. NÃO mova verticalmente. NÃO altere a perspectiva."
            )
        ),
        MovimentoVideo(
            "Subida suave",
            prompt(
                "Mova a câmera verticalmente para cima, de forma lenta e pequena.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO deslize lateralmente."
            )
        ),
        MovimentoVideo(
            "Descida suave",
            prompt(
                "Mova a câmera verticalmente para baixo, de forma lenta e pequena.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO deslize lateralmente."
            )
        ),
        MovimentoVideo(
            "Inclinação para cima",
            prompt(
                "Incline discretamente a câmera para cima, como um tilt-up controlado.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO deslize lateralmente."
            )
        ),
        MovimentoVideo(
            "Inclinação para baixo",
            prompt(
                "Incline discretamente a câmera para baixo, como um tilt-down controlado.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO deslize lateralmente."
            )
        ),
        MovimentoVideo(
            "Diagonal superior direita",
            prompt(
                "Desloque a câmera suavemente na diagonal para cima e para a direita.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO transforme em pan horizontal puro."
            )
        ),
        MovimentoVideo(
            "Diagonal superior esquerda",
            prompt(
                "Desloque a câmera suavemente na diagonal para cima e para a esquerda.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO transforme em pan horizontal puro."
            )
        ),
        MovimentoVideo(
            "Diagonal inferior direita",
            prompt(
                "Desloque a câmera suavemente na diagonal para baixo e para a direita.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO transforme em pan horizontal puro."
            )
        ),
        MovimentoVideo(
            "Diagonal inferior esquerda",
            prompt(
                "Desloque a câmera suavemente na diagonal para baixo e para a esquerda.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO transforme em pan horizontal puro."
            )
        ),
        MovimentoVideo(
            "Órbita leve para direita",
            prompt(
                "Faça uma órbita muito leve da câmera para a direita, mantendo o centro do ambiente estável.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste demais. NÃO distorça paredes, portas ou janelas."
            )
        ),
        MovimentoVideo(
            "Órbita leve para esquerda",
            prompt(
                "Faça uma órbita muito leve da câmera para a esquerda, mantendo o centro do ambiente estável.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste demais. NÃO distorça paredes, portas ou janelas."
            )
        ),
        MovimentoVideo(
            "Entrada lenta no ambiente",
            prompt(
                "Inicie no enquadramento original e faça uma entrada frontal muito lenta e estável no ambiente.",
                "NÃO faça pan lateral. NÃO faça zoom óptico. NÃO altere objetos ou estrutura."
            )
        ),
        MovimentoVideo(
            "Saída lenta do ambiente",
            prompt(
                "Inicie no enquadramento original e faça uma saída frontal muito lenta e estável do ambiente.",
                "NÃO faça pan lateral. NÃO faça zoom óptico. NÃO altere objetos ou estrutura."
            )
        ),
        MovimentoVideo(
            "Revelação panorâmica para direita",
            prompt(
                "Comece no enquadramento original e revele lentamente mais área à direita por deslocamento horizontal.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO mova verticalmente."
            )
        ),
        MovimentoVideo(
            "Revelação panorâmica para esquerda",
            prompt(
                "Comece no enquadramento original e revele lentamente mais área à esquerda por deslocamento horizontal.",
                "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO mova verticalmente."
            )
        ),
        MovimentoVideo(
            "Foco discreto na janela",
            prompt(
                "Faça uma aproximação frontal muito pequena e elegante na direção da janela, mantendo todo o ambiente reconhecível.",
                "NÃO faça pan. NÃO altere a luz externa. NÃO crie paisagem, pessoas ou objetos novos."
            )
        ),
        MovimentoVideo(
            "Foco discreto na porta",
            prompt(
                "Faça uma aproximação frontal muito pequena e elegante na direção da porta, mantendo todo o ambiente reconhecível.",
                "NÃO faça pan. NÃO altere a porta, paredes, objetos ou proporções."
            )
        ),
        MovimentoVideo(
            "Câmera quase parada",
            prompt(
                "Mantenha a câmera praticamente parada, com apenas profundidade visual muito discreta.",
                "NÃO faça zoom perceptível. NÃO faça pan. NÃO aproxime. NÃO afaste. NÃO altere objetos."
            )
        ),
        MovimentoVideo(
            "Microprofundidade discreta",
            prompt(
                "Mantenha a câmera estável com micro movimento de profundidade quase imperceptível e natural.",
                "NÃO faça zoom perceptível. NÃO faça pan. NÃO aproxime demais. NÃO altere objetos."
            )
        )
    )
}

private fun gerarVideoPVideo(
    bitmap: Bitmap,
    duracao: Int,
    prompt: String,
    provider: String,
    resolution: String,
    aoAtualizarStatus: (String) -> Unit
): String {
    aoAtualizarStatus("enviando imagem para a IA...")

    val imagemBase64 =
        prepararImagemParaVideo(bitmap)

    val uploadResposta = postJson(
        endpoint =
            "$MOVIIMOVEL_VIDEO_WORKER/upload-image",
        body = JSONObject()
            .put("imageBase64", imagemBase64)
            .put("mimeType", "image/jpeg")
    )

    if (!uploadResposta.optBoolean("ok", false)) {
        throw IllegalStateException(
            uploadResposta.optString(
                "error",
                "Falha ao enviar imagem."
            )
        )
    }

    val imageUrl =
        uploadResposta.optString("imageUrl")

    if (imageUrl.isBlank()) {
        throw IllegalStateException(
            "O Worker não devolveu a URL da foto enviada."
        )
    }

    aoAtualizarStatus("solicitando geração à IA...")

    val gerarResposta = postJson(
        endpoint =
            "$MOVIIMOVEL_VIDEO_WORKER/generate",
        body = JSONObject()
            .put("provider", provider)
            .put("imageUrl", imageUrl)
            .put("prompt", prompt)
            .put("duration", duracao)
            .put("resolution", resolution)
            .put("aspectRatio", "16:9")
            .put("fps", 24)
    )

    if (!gerarResposta.optBoolean("ok", false)) {
        throw IllegalStateException(
            gerarResposta.optString(
                "error",
                "Falha ao gerar vídeo."
            )
        )
    }

    var state =
        gerarResposta.optString("state", "starting")
            .lowercase(Locale.ROOT)

    var videoUrl =
        gerarResposta.optString("videoUrl")

    val predictionId =
        gerarResposta.optString("predictionId")

    if (videoUrl.isNotBlank()) {
        aoAtualizarStatus("vídeo pronto. Baixando arquivo...")
        return videoUrl
    }

    if (predictionId.isBlank()) {
        throw IllegalStateException(
            "A IA não devolveu o identificador da geração."
        )
    }

    var tentativas = 0
    val maxTentativas = 240

    while (state !in listOf("succeeded", "failed", "canceled", "cancelled")) {
        aoAtualizarStatus(
            when (state) {
                "starting" ->
                    "IA iniciou a preparação..."
                "processing" ->
                    "gerando pela IA..."
                else ->
                    "status real da IA: $state"
            }
        )

        Thread.sleep(3_000)
        tentativas++

        if (tentativas > maxTentativas) {
            throw IllegalStateException(
                "A IA demorou mais de 12 minutos para concluir."
            )
        }

        val statusResposta = getJson(
            "$MOVIIMOVEL_VIDEO_WORKER/prediction-status/" +
                java.net.URLEncoder.encode(
                    predictionId,
                    "UTF-8"
                )
        )

        if (!statusResposta.optBoolean("ok", false)) {
            throw IllegalStateException(
                statusResposta.optString(
                    "error",
                    "Não foi possível consultar o status da IA."
                )
            )
        }

        state =
            statusResposta.optString("state", "starting")
                .lowercase(Locale.ROOT)

        videoUrl =
            statusResposta.optString("videoUrl")
    }

    if (state != "succeeded") {
        throw IllegalStateException(
            "A IA terminou com status: $state."
        )
    }

    if (videoUrl.isBlank()) {
        throw IllegalStateException(
            "A IA concluiu, mas o Worker não devolveu a URL permanente do vídeo."
        )
    }

    aoAtualizarStatus("vídeo pronto. Baixando arquivo...")
    return videoUrl
}

private fun getJson(
    endpoint: String
): JSONObject {
    val connection =
        URL(endpoint).openConnection() as HttpURLConnection

    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true

        val responseCode = connection.responseCode

        val stream =
            if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

        val responseText =
            stream.bufferedReader().use {
                it.readText()
            }

        if (responseText.isBlank()) {
            throw IllegalStateException(
                "A consulta de status retornou resposta vazia. HTTP $responseCode."
            )
        }

        return JSONObject(responseText)
    } finally {
        connection.disconnect()
    }
}

private fun formatarTempoDecorrido(
    segundos: Long
): String {
    val minutos = segundos / 60
    val resto = segundos % 60

    return String.format(
        Locale.getDefault(),
        "%02d:%02d",
        minutos,
        resto
    )
}

private fun prepararImagemParaVideo(
    bitmap: Bitmap
): String {
    val maiorLado =
        maxOf(bitmap.width, bitmap.height)

    val limite = 2560

    val imagemFinal =
        if (maiorLado > limite) {
            val proporcao =
                limite.toFloat() /
                    maiorLado.toFloat()

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
        92,
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
    val connection =
        URL(endpoint).openConnection()
            as HttpURLConnection

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
                body.toString()
                    .toByteArray(Charsets.UTF_8)
            )
        }

        val code = connection.responseCode

        val stream =
            if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

        val responseText =
            stream?.bufferedReader()?.use {
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

private fun baixarVideoTemporario(
    videoUrl: String,
    destino: File,
    aoProgredir: (recebidos: Long, total: Long) -> Unit
) {
    val connection =
        URL(videoUrl).openConnection()
            as HttpURLConnection

    try {
        connection.connectTimeout = 30_000
        connection.readTimeout = 180_000
        connection.instanceFollowRedirects = true

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException(
                "Não foi possível baixar a cena. HTTP ${connection.responseCode}."
            )
        }

        val total = connection.contentLengthLong
        var recebidos = 0L
        val buffer = ByteArray(64 * 1024)

        aoProgredir(recebidos, total)

        connection.inputStream.use { input ->
            FileOutputStream(destino).use { output ->
                while (true) {
                    val lidos = input.read(buffer)

                    if (lidos == -1) {
                        break
                    }

                    output.write(buffer, 0, lidos)

                    recebidos += lidos.toLong()

                    aoProgredir(recebidos, total)
                }

                output.flush()
            }
        }
    } finally {
        connection.disconnect()
    }
}

private fun formatarBytes(bytes: Long): String {
    if (bytes < 1024L) {
        return "$bytes B"
    }

    val kb = bytes / 1024.0

    if (kb < 1024.0) {
        return String.format(Locale.US, "%.1f KB", kb)
    }

    val mb = kb / 1024.0

    if (mb < 1024.0) {
        return String.format(Locale.US, "%.1f MB", mb)
    }

    val gb = mb / 1024.0

    return String.format(Locale.US, "%.2f GB", gb)
}

private fun juntarClipesMp4(
    arquivos: List<File>,
    destino: File
) {
    if (arquivos.size < 2) {
        arquivos.first().copyTo(
            destino,
            overwrite = true
        )
        return
    }

    val primeiro = MediaExtractor()

    primeiro.setDataSource(
        arquivos.first().absolutePath
    )

    var videoTrackOrigem = -1
    var audioTrackOrigem = -1

    for (i in 0 until primeiro.trackCount) {
        val format = primeiro.getTrackFormat(i)

        val mime =
            format.getString(
                MediaFormat.KEY_MIME
            ).orEmpty()

        if (
            mime.startsWith("video/") &&
                videoTrackOrigem == -1
        ) {
            videoTrackOrigem = i
        }

        if (
            mime.startsWith("audio/") &&
                audioTrackOrigem == -1
        ) {
            audioTrackOrigem = i
        }
    }

    if (videoTrackOrigem == -1) {
        primeiro.release()

        throw IllegalStateException(
            "A primeira cena não contém vídeo MP4 compatível."
        )
    }

    val muxer = MediaMuxer(
        destino.absolutePath,
        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
    )

    val videoTrackDestino =
        muxer.addTrack(
            primeiro.getTrackFormat(videoTrackOrigem)
        )

    val audioTrackDestino =
        if (audioTrackOrigem >= 0) {
            muxer.addTrack(
                primeiro.getTrackFormat(
                    audioTrackOrigem
                )
            )
        } else {
            -1
        }

    primeiro.release()

    muxer.start()

    try {
        var offsetUs = 0L

        arquivos.forEach { arquivo ->
            val extractor = MediaExtractor()

            try {
                extractor.setDataSource(
                    arquivo.absolutePath
                )

                copiarTrack(
                    extractor = extractor,
                    prefixoMime = "video/",
                    destinoTrack = videoTrackDestino,
                    muxer = muxer,
                    offsetUs = offsetUs
                )

                if (audioTrackDestino >= 0) {
                    copiarTrack(
                        extractor = extractor,
                        prefixoMime = "audio/",
                        destinoTrack = audioTrackDestino,
                        muxer = muxer,
                        offsetUs = offsetUs
                    )
                }

                offsetUs += duracaoDoVideoUs(
                    arquivo
                )
            } finally {
                extractor.release()
            }
        }
    } finally {
        muxer.stop()
        muxer.release()
    }
}

private fun copiarTrack(
    extractor: MediaExtractor,
    prefixoMime: String,
    destinoTrack: Int,
    muxer: MediaMuxer,
    offsetUs: Long
) {
    var origemTrack = -1
    var maxBuffer = 2 * 1024 * 1024

    for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)

        val mime =
            format.getString(
                MediaFormat.KEY_MIME
            ).orEmpty()

        if (mime.startsWith(prefixoMime)) {
            origemTrack = i

            if (
                format.containsKey(
                    MediaFormat.KEY_MAX_INPUT_SIZE
                )
            ) {
                maxBuffer = maxOf(
                    maxBuffer,
                    format.getInteger(
                        MediaFormat.KEY_MAX_INPUT_SIZE
                    )
                )
            }

            break
        }
    }

    if (origemTrack == -1) {
        return
    }

    extractor.selectTrack(origemTrack)

    val buffer =
        java.nio.ByteBuffer.allocateDirect(
            maxBuffer
        )

    val info = MediaCodec.BufferInfo()

    while (true) {
        buffer.clear()

        val tamanho =
            extractor.readSampleData(buffer, 0)

        if (tamanho < 0) {
            break
        }

        info.set(
            0,
            tamanho,
            extractor.sampleTime + offsetUs,
            extractor.sampleFlags
        )

        muxer.writeSampleData(
            destinoTrack,
            buffer,
            info
        )

        extractor.advance()
    }

    extractor.unselectTrack(origemTrack)
}

private fun duracaoDoVideoUs(
    arquivo: File
): Long {
    val retriever = MediaMetadataRetriever()

    return try {
        retriever.setDataSource(
            arquivo.absolutePath
        )

        val ms =
            retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull()
                ?: 0L

        ms * 1_000L
    } finally {
        retriever.release()
    }
}

private fun salvarVideoNaGaleria(
    context: Context,
    arquivo: File
): Uri {
    val nome =
        "MoviImovel_" +
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(Date()) +
            ".mp4"

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        throw IllegalStateException(
            "Seu Android precisa estar atualizado para salvar automaticamente na pasta MoviImovel."
        )
    }

    val values = ContentValues().apply {
        put(
            MediaStore.Video.Media.DISPLAY_NAME,
            nome
        )

        put(
            MediaStore.Video.Media.MIME_TYPE,
            "video/mp4"
        )

        put(
            MediaStore.Video.Media.RELATIVE_PATH,
            Environment.DIRECTORY_MOVIES +
                "/MoviImovel"
        )

        put(
            MediaStore.Video.Media.IS_PENDING,
            1
        )
    }

    val resolver = context.contentResolver

    val uri = resolver.insert(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        values
    ) ?: throw IllegalStateException(
        "Não foi possível criar o arquivo na Galeria."
    )

    try {
        resolver.openOutputStream(uri)?.use { output ->
            arquivo.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException(
            "Não foi possível gravar o vídeo na Galeria."
        )

        values.clear()

        values.put(
            MediaStore.Video.Media.IS_PENDING,
            0
        )

        resolver.update(
            uri,
            values,
            null,
            null
        )

        return uri
    } catch (erro: Exception) {
        resolver.delete(
            uri,
            null,
            null
        )

        throw erro
    }
}

private fun abrirVideoLocal(
    context: Context,
    uri: Uri
) {
    val intent = Intent(
        Intent.ACTION_VIEW
    ).apply {
        setDataAndType(
            uri,
            "video/mp4"
        )

        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    context.startActivity(intent)
}

private fun carregarBitmapPreview(
    context: Context,
    uriTexto: String
): Bitmap? {
    return try {
        val uri = Uri.parse(uriTexto)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source =
                ImageDecoder.createSource(
                    context.contentResolver,
                    uri
                )

            ImageDecoder.decodeBitmap(
                source
            ) { decoder, info, _ ->
                decoder.allocator =
                    ImageDecoder.ALLOCATOR_SOFTWARE

                val maior =
                    maxOf(
                        info.size.width,
                        info.size.height
                    )

                if (maior > 600) {
                    val proporcao =
                        600f / maior

                    decoder.setTargetSize(
                        (info.size.width * proporcao).toInt(),
                        (info.size.height * proporcao).toInt()
                    )
                }
            }
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

private fun carregarBitmapAltaQualidade(
    context: Context,
    uriTexto: String
): Bitmap? {
    return try {
        val uri = Uri.parse(uriTexto)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source =
                ImageDecoder.createSource(
                    context.contentResolver,
                    uri
                )

            ImageDecoder.decodeBitmap(
                source
            ) { decoder, info, _ ->
                decoder.allocator =
                    ImageDecoder.ALLOCATOR_SOFTWARE

                val maiorLado =
                    maxOf(
                        info.size.width,
                        info.size.height
                    )

                if (maiorLado > 4096) {
                    val proporcao =
                        4096f / maiorLado

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
