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
import androidx.compose.material3.OutlinedTextFieldDefaults
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

    var modoQualidade by remember {
        mutableStateOf("Rascunho 720p")
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

    var confirmarGeracao by remember {
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

    if (confirmarGeracao) {
        val custoPorSegundo = when (modoQualidade) {
            "Rascunho 720p" -> 0.005
            "Final 720p" -> 0.02
            else -> 0.04
        }

        val custoDolar = custoPorSegundo * duracaoVideo
        val custoReal = custoDolar * 5.22

        AlertDialog(
            onDismissRequest = {
                if (!gerandoVideo) confirmarGeracao = false
            },
            title = {
                Text(
                    text = "Confirmar geração",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Movimento: " + movimentoSelecionado.nome)
                    Text(text = "Duração: " + duracaoVideo + " segundos")
                    Text(text = "Qualidade: " + modoQualidade)
                    Text(
                        text = "Custo estimado: US$ " +
                            String.format(Locale.US, "%.3f", custoDolar) +
                            " • cerca de R$ " +
                            String.format(Locale.US, "%.2f", custoReal),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "O vídeo só será enviado para a IA depois de tocar em Gerar vídeo.",
                        color = Color(0xFF5C5C5C),
                        fontSize = 13.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmarGeracao = false },
                    enabled = !gerandoVideo
                ) { Text("Cancelar") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val foto = fotoSelecionada
                        if (foto == null) {
                            confirmarGeracao = false
                            mensagem = "Selecione uma foto antes de gerar."
                            return@Button
                        }
                        if (promptVideo.isBlank()) {
                            confirmarGeracao = false
                            mensagem = "O prompt não pode ficar vazio."
                            return@Button
                        }
                        confirmarGeracao = false
                        gerandoVideo = true
                        mensagem = "Enviando foto e gerando vídeo de " + duracaoVideo + " segundos..."
                        scope.launch(Dispatchers.IO) {
                            try {
                                val provider = if (modoQualidade == "Rascunho 720p") "preview" else "pvideo"
                                val resolution = if (modoQualidade == "Final 1080p") "1080p" else "720p"
                                val videoUrl = gerarVideoPVideo(
                                    bitmap = foto,
                                    duracao = duracaoVideo,
                                    prompt = promptVideo,
                                    provider = provider,
                                    resolution = resolution
                                )
                                withContext(Dispatchers.Main) {
                                    ultimoVideoUrl = videoUrl
                                    gerandoVideo = false
                                    mensagem = "Vídeo pronto e salvo permanentemente no R2."
                                    abrirVideo(context, videoUrl)
                                }
                            } catch (erro: Exception) {
                                withContext(Dispatchers.Main) {
                                    gerandoVideo = false
                                    mensagem = "Erro ao gerar vídeo: " + erro.message
                                }
                            }
                        }
                    },
                    enabled = !gerandoVideo
                ) { Text("Gerar vídeo") }
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
                    minLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color(0xFFE1E8EC),
                        focusedBorderColor = Color(0xFFFFA45B),
                        unfocusedBorderColor = Color(0xFF74818A),
                        focusedLabelColor = Color(0xFFFFC58B),
                        unfocusedLabelColor = Color(0xFFBBC7CD),
                        cursorColor = Color(0xFFFFA45B)
                    )
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


                Text(
                    text = "Qualidade",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Rascunho 720p",
                        "Final 720p",
                        "Final 1080p"
                    ).chunked(2).forEach { linha ->
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            linha.forEach { qualidade ->
                                Button(
                                    onClick = {
                                        modoQualidade = qualidade
                                        mensagem = "Qualidade escolhida: $qualidade"
                                    },
                                    enabled = !gerandoVideo,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (
                                            modoQualidade == qualidade
                                        ) {
                                            Color(0xFF6F4C9B)
                                        } else {
                                            Color(0xFF304D5B)
                                        }
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = qualidade,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = when (modoQualidade) {
                        "Rascunho 720p" ->
                            "Modo barato para testar. Pode ficar mais suave ou borrado."

                        "Final 720p" ->
                            "Mais estável que o rascunho, mantendo 720p."

                        else ->
                            "Maior qualidade disponível neste app. Mais caro que 720p."
                    },
                    color = Color(0xFFC6D0D6),
                    fontSize = 13.sp
                )

                Button(
                    onClick = {
                        if (fotoSelecionada == null) {
                            mensagem = "Selecione uma foto antes de gerar."
                            return@Button
                        }

                        if (promptVideo.isBlank()) {
                            mensagem = "O prompt não pode ficar vazio."
                            return@Button
                        }

                        confirmarGeracao = true
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
    fun prompt(comando: String, bloqueios: String): String {
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
        MovimentoVideo("Pan esquerda → direita lento", prompt("Deslize a câmera horizontalmente da esquerda para a direita, em linha reta e lentamente.", "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO suba. NÃO desça. NÃO faça diagonal.")),
        MovimentoVideo("Pan esquerda → direita médio", prompt("Deslize a câmera horizontalmente da esquerda para a direita, em linha reta, com velocidade média.", "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO suba. NÃO desça. NÃO faça diagonal.")),
        MovimentoVideo("Pan direita → esquerda lento", prompt("Deslize a câmera horizontalmente da direita para a esquerda, em linha reta e lentamente.", "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO suba. NÃO desça. NÃO faça diagonal.")),
        MovimentoVideo("Pan direita → esquerda médio", prompt("Deslize a câmera horizontalmente da direita para a esquerda, em linha reta, com velocidade média.", "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO suba. NÃO desça. NÃO faça diagonal.")),
        MovimentoVideo("Travelling curto para direita", prompt("Mova a câmera fisicamente poucos centímetros para a direita, mantendo linhas verticais naturais.", "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO faça órbita. NÃO mova verticalmente.")),
        MovimentoVideo("Travelling curto para esquerda", prompt("Mova a câmera fisicamente poucos centímetros para a esquerda, mantendo linhas verticais naturais.", "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO faça órbita. NÃO mova verticalmente.")),
        MovimentoVideo("Aproximação frontal suave", prompt("Aproxime a câmera lentamente para frente, mantendo o centro do ambiente como referência.", "NÃO faça pan lateral. NÃO suba. NÃO desça. NÃO faça órbita. NÃO faça diagonal.")),
        MovimentoVideo("Aproximação frontal média", prompt("Aproxime a câmera para frente com intensidade média, preservando todos os objetos e proporções.", "NÃO faça pan lateral. NÃO suba. NÃO desça. NÃO faça órbita. NÃO faça diagonal.")),
        MovimentoVideo("Afastamento suave", prompt("Afaste a câmera lentamente para trás, revelando um pouco mais do ambiente.", "NÃO faça pan lateral. NÃO aproxime. NÃO suba. NÃO desça. NÃO faça diagonal.")),
        MovimentoVideo("Afastamento médio", prompt("Afaste a câmera para trás com intensidade média, revelando mais do ambiente.", "NÃO faça pan lateral. NÃO aproxime. NÃO suba. NÃO desça. NÃO faça diagonal.")),
        MovimentoVideo("Zoom leve para dentro", prompt("Faça somente zoom óptico leve para dentro, sem deslocar a posição da câmera.", "NÃO faça pan. NÃO faça dolly para frente. NÃO mova verticalmente. NÃO altere a perspectiva.")),
        MovimentoVideo("Zoom médio para dentro", prompt("Faça somente zoom óptico médio para dentro, sem deslocar a posição da câmera.", "NÃO faça pan. NÃO faça dolly para frente. NÃO mova verticalmente. NÃO altere a perspectiva.")),
        MovimentoVideo("Zoom leve para fora", prompt("Faça somente zoom óptico leve para fora, sem deslocar a posição da câmera.", "NÃO faça pan. NÃO afaste a câmera fisicamente. NÃO mova verticalmente. NÃO altere a perspectiva.")),
        MovimentoVideo("Zoom médio para fora", prompt("Faça somente zoom óptico médio para fora, sem deslocar a posição da câmera.", "NÃO faça pan. NÃO afaste a câmera fisicamente. NÃO mova verticalmente. NÃO altere a perspectiva.")),
        MovimentoVideo("Subida suave", prompt("Mova a câmera verticalmente para cima, de forma lenta e pequena.", "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO deslize lateralmente.")),
        MovimentoVideo("Descida suave", prompt("Mova a câmera verticalmente para baixo, de forma lenta e pequena.", "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO deslize lateralmente.")),
        MovimentoVideo("Inclinação para cima", prompt("Incline discretamente a câmera para cima, como um tilt-up controlado.", "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO deslize lateralmente.")),
        MovimentoVideo("Inclinação para baixo", prompt("Incline discretamente a câmera para baixo, como um tilt-down controlado.", "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO deslize lateralmente.")),
        MovimentoVideo("Diagonal superior direita", prompt("Desloque a câmera suavemente na diagonal para cima e para a direita.", "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO transforme em pan horizontal puro.")),
        MovimentoVideo("Diagonal superior esquerda", prompt("Desloque a câmera suavemente na diagonal para cima e para a esquerda.", "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO transforme em pan horizontal puro.")),
        MovimentoVideo("Diagonal inferior direita", prompt("Desloque a câmera suavemente na diagonal para baixo e para a direita.", "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO transforme em pan horizontal puro.")),
        MovimentoVideo("Diagonal inferior esquerda", prompt("Desloque a câmera suavemente na diagonal para baixo e para a esquerda.", "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO transforme em pan horizontal puro.")),
        MovimentoVideo("Órbita leve para direita", prompt("Faça uma órbita muito leve da câmera para a direita, mantendo o centro do ambiente estável.", "NÃO faça zoom. NÃO aproxime. NÃO afaste demais. NÃO distorça paredes, portas ou janelas.")),
        MovimentoVideo("Órbita leve para esquerda", prompt("Faça uma órbita muito leve da câmera para a esquerda, mantendo o centro do ambiente estável.", "NÃO faça zoom. NÃO aproxime. NÃO afaste demais. NÃO distorça paredes, portas ou janelas.")),
        MovimentoVideo("Entrada lenta no ambiente", prompt("Inicie no enquadramento original e faça uma entrada frontal muito lenta e estável no ambiente.", "NÃO faça pan lateral. NÃO faça zoom óptico. NÃO altere objetos ou estrutura.")),
        MovimentoVideo("Saída lenta do ambiente", prompt("Inicie no enquadramento original e faça uma saída frontal muito lenta e estável do ambiente.", "NÃO faça pan lateral. NÃO faça zoom óptico. NÃO altere objetos ou estrutura.")),
        MovimentoVideo("Revelação panorâmica para direita", prompt("Comece no enquadramento original e revele lentamente mais área à direita por deslocamento horizontal.", "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO mova verticalmente.")),
        MovimentoVideo("Revelação panorâmica para esquerda", prompt("Comece no enquadramento original e revele lentamente mais área à esquerda por deslocamento horizontal.", "NÃO faça zoom. NÃO aproxime. NÃO afaste. NÃO mova verticalmente.")),
        MovimentoVideo("Foco discreto na janela", prompt("Faça uma aproximação frontal muito pequena e elegante na direção da janela, mantendo todo o ambiente reconhecível.", "NÃO faça pan. NÃO altere a luz externa. NÃO crie paisagem, pessoas ou objetos novos.")),
        MovimentoVideo("Foco discreto na porta", prompt("Faça uma aproximação frontal muito pequena e elegante na direção da porta, mantendo todo o ambiente reconhecível.", "NÃO faça pan. NÃO altere a porta, paredes, objetos ou proporções.")),
        MovimentoVideo("Câmera quase parada", prompt("Mantenha a câmera praticamente parada, com apenas profundidade visual muito discreta.", "NÃO faça zoom perceptível. NÃO faça pan. NÃO aproxime. NÃO afaste. NÃO altere objetos.")),
        MovimentoVideo("Microprofundidade discreta", prompt("Mantenha a câmera estável com micro movimento de profundidade quase imperceptível e natural.", "NÃO faça zoom perceptível. NÃO faça pan. NÃO aproxime demais. NÃO altere objetos."))
    )
}
private fun gerarVideoPVideo(
    bitmap: Bitmap,
    duracao: Int,
    prompt: String,
    provider: String,
    resolution: String
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
    val limite = 2560

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
