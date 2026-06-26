
function decodeBase64ToBytes(base64Text) {
  const binary = atob(base64Text)
  const bytes = new Uint8Array(binary.length)

  for (let index = 0; index < binary.length; index++) {
    bytes[index] = binary.charCodeAt(index)
  }

  return bytes
}

function createUploadedImageKey() {
  const now = new Date()
  const year = now.getUTCFullYear()
  const month = String(now.getUTCMonth() + 1).padStart(2, "0")
  const day = String(now.getUTCDate()).padStart(2, "0")

  return `uploads/${year}-${month}-${day}/foto-${crypto.randomUUID()}.jpg`
}


function createEditedImageKey() {
  const now = new Date()
  const year = now.getUTCFullYear()
  const month = String(now.getUTCMonth() + 1).padStart(2, "0")
  const day = String(now.getUTCDate()).padStart(2, "0")

  return `edits/${year}-${month}-${day}/imagem-${crypto.randomUUID()}.jpg`
}

function bytesToBase64(bytes) {
  const chunkSize = 0x8000
  let binary = ""

  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + chunkSize))
  }

  return btoa(binary)
}

function normalizeImageMimeType(value) {
  const mimeType = String(value || "").split(";")[0].trim().toLowerCase()
  return mimeType.startsWith("image/") ? mimeType : "image/jpeg"
}

function buildImageEditPrompt(operation, customPrompt, roomType, style) {
  const baseRules = [
    "Preserve exactly the structural elements of the original property: walls, floor, ceiling, doors, windows, baseboards, electrical outlets, natural lighting and camera perspective.",
    "Keep realistic scale and perspective.",
    "Do not add people, animals, watermarks, logos or text.",
    "This is an illustrative AI edit for a real estate presentation; do not hide structural defects."
  ].join(" ")

  if (customPrompt) {
    return `${customPrompt.trim()} ${baseRules}`
  }

  if (operation === "empty") {
    return [
      "Remove all movable furniture, sofa, TV, rug, table, appliances, decorations and loose objects from this room.",
      "Reconstruct the visible floor, walls and background naturally.",
      "Leave the room empty, clean and realistic for a real estate listing.",
      baseRules
    ].join(" ")
  }

  const safeRoom = roomType || "room"
  const safeStyle = style || "modern, elegant and neutral"

  return [
    `Furnish this ${safeRoom} with realistic ${safeStyle} furniture and discreet decor appropriate for a real estate listing.`,
    "Do not block doors, windows, passages or fixed appliances.",
    baseRules
  ].join(" ")
}

function findGeneratedImage(result) {
  const direct = result?.output_image || result?.outputImage
  if (direct?.data) {
    return {
      data: direct.data,
      mimeType: normalizeImageMimeType(direct.mime_type || direct.mimeType || "image/jpeg")
    }
  }

  const candidates = Array.isArray(result?.output)
    ? result.output
    : Array.isArray(result?.outputs)
      ? result.outputs
      : []

  for (const item of candidates) {
    const image = item?.image || item?.output_image || item?.outputImage
    if (image?.data) {
      return {
        data: image.data,
        mimeType: normalizeImageMimeType(image.mime_type || image.mimeType || "image/jpeg")
      }
    }

    const content = item?.content || item
    const parts = Array.isArray(content?.parts) ? content.parts : []
    for (const part of parts) {
      const inline = part?.inline_data || part?.inlineData
      if (inline?.data) {
        return {
          data: inline.data,
          mimeType: normalizeImageMimeType(inline.mime_type || inline.mimeType || "image/jpeg")
        }
      }
    }
  }

  return null
}

async function runNanoBananaEdit({ env, imageUrl, operation, customPrompt, roomType, style }) {
  if (!env.GEMINI_API_KEY) {
    throw new Error("GEMINI_API_KEY não está configurada no Worker.")
  }

  const source = await fetch(imageUrl)
  if (!source.ok) {
    throw new Error(`Não foi possível baixar a imagem de origem. HTTP ${source.status}.`)
  }

  const contentType = normalizeImageMimeType(source.headers.get("content-type"))
  const imageBuffer = await source.arrayBuffer()

  if (imageBuffer.byteLength < 100) {
    throw new Error("A imagem de origem está vazia ou inválida.")
  }

  if (imageBuffer.byteLength > 15 * 1024 * 1024) {
    throw new Error("A imagem de origem excede 15 MB. Reduza o tamanho antes de editar.")
  }

  const prompt = buildImageEditPrompt(operation, customPrompt, roomType, style)
  const requestBody = {
    model: "gemini-2.5-flash-image",
    input: [
      { type: "text", text: prompt },
      {
        type: "image",
        data: bytesToBase64(new Uint8Array(imageBuffer)),
        mime_type: contentType
      }
    ],
    response_format: {
      type: "image",
      mime_type: "image/jpeg"
    }
  }

  const response = await fetch("https://generativelanguage.googleapis.com/v1beta/interactions", {
    method: "POST",
    headers: {
      "x-goog-api-key": env.GEMINI_API_KEY,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(requestBody)
  })

  const result = await response.json().catch(() => null)

  if (!response.ok) {
    const detail = result?.error?.message || result?.message || `Gemini retornou HTTP ${response.status}.`
    throw new Error(detail)
  }

  const generated = findGeneratedImage(result)
  if (!generated?.data) {
    throw new Error("O Gemini respondeu, mas não retornou uma imagem editada.")
  }

  const imageBytes = decodeBase64ToBytes(generated.data)
  const key = createEditedImageKey()

  if (!env.VIDEOS) {
    throw new Error("O bucket R2 VIDEOS não está configurado no Worker.")
  }

  await env.VIDEOS.put(key, imageBytes, {
    httpMetadata: { contentType: generated.mimeType },
    customMetadata: {
      origem: "Movimovel Nano Banana",
      operacao: operation
    }
  })

  return {
    prompt,
    imageKey: key,
    mimeType: generated.mimeType,
    raw: result
  }
}

function json(data, status = 200) {
  return Response.json(data, { status })
}

function friendlyImageError(error) {
  const detail = String(error?.message || "")
  const lower = detail.toLowerCase()

  if (
    lower.includes("429") ||
    lower.includes("resource_exhausted") ||
    lower.includes("quota") ||
    lower.includes("rate limit") ||
    lower.includes("limit exceeded")
  ) {
    return "Não foi possível editar a imagem agora. A cota gratuita da IA pode ter sido atingida ou estar temporariamente indisponível. Tente novamente mais tarde."
  }

  if (
    lower.includes("api key") ||
    lower.includes("unauthenticated") ||
    lower.includes("permission denied") ||
    lower.includes("forbidden")
  ) {
    return "Não foi possível autorizar a edição de imagem. Verifique a configuração da chave Gemini no Worker."
  }

  return "Não foi possível editar a imagem. Verifique sua conexão e tente novamente."
}

function imageTestPageHtml(resultHtml = "") {
  return `<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Movimovel · Teste de imagem IA</title>
<style>
*{box-sizing:border-box} body{margin:0;background:#ececef;color:#141414;font-family:Arial,sans-serif}
main{max-width:680px;margin:0 auto;padding:24px 16px 40px} h1{font-size:28px;margin:0 0 8px}.sub{color:#555;line-height:1.45}
.card{background:#fff;border:1px solid #d5d2ca;border-radius:18px;padding:16px;margin:14px 0;box-shadow:0 6px 20px #0000000d}
label{display:block;font-weight:700;margin:14px 0 8px} input,select,textarea,button{width:100%;font:inherit;border-radius:12px}
input,select,textarea{border:1px solid #c9c6be;padding:13px;background:#fff} textarea{min-height:108px}
button{border:1px solid #b89349;background:#151515;color:#fff;padding:15px;font-weight:700;margin-top:16px}
#loading{display:none;margin-top:14px;padding:12px;border-radius:10px;background:#f6f1e6;color:#53421e}
.result-ok{padding:14px;border-radius:12px;background:#edf8ef;color:#1d5f2b;white-space:pre-wrap;line-height:1.45}
.result-error{padding:14px;border-radius:12px;background:#fff0f0;color:#8d2525;white-space:pre-wrap;line-height:1.45}
.result-img{width:100%;border-radius:14px;margin-top:14px}
.note{font-size:13px;color:#6a6255;line-height:1.4}
</style>
</head>
<body><main>
<h1>Teste de imagem IA</h1>
<p class="sub">Envie uma foto, escolha a edição e gere uma prévia. A foto original é preservada.</p>
${resultHtml}
<form id="imageForm" method="POST" action="/test-image-run" enctype="multipart/form-data">
<div class="card">
<label>Foto do ambiente</label>
<input name="image" type="file" accept="image/jpeg,image/png,image/webp" required>
<label>Tipo de edição</label>
<select name="operation"><option value="empty">Esvaziar ambiente</option><option value="furnish">Mobiliar ambiente</option></select>
<label>Tipo de cômodo (opcional)</label>
<input name="roomType" placeholder="Ex.: sala, quarto, cozinha">
<label>Estilo (opcional)</label>
<input name="style" placeholder="Ex.: moderno, clean, madeira clara">
<label>Pedido adicional (opcional)</label>
<textarea name="prompt" placeholder="Ex.: manter portas, janelas e iluminação exatamente como estão"></textarea>
<button id="go" type="submit">Gerar imagem</button>
<div id="loading">Enviando a foto e pedindo a edição para a IA. Esta página pode levar alguns segundos.</div>
<p class="note">Imagem ilustrativa editada por IA. Revise antes de anunciar ou publicar.</p>
</div>
</form>
<script>
document.getElementById("imageForm").addEventListener("submit", function(){
 const b=document.getElementById("go"); b.disabled=true; b.textContent="Enviando...";
 document.getElementById("loading").style.display="block";
});
</script>
</main></body></html>`
}

function escapeHtml(value) {
  return String(value || "").replace(/[&<>"']/g, char => ({
    "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#039;"
  }[char]))
}

function toNumber(value, fallback = 0) {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

function roundUsd(value) {
  return Math.round(value * 1000) / 1000
}

function getGrokPricePerSecond(env, resolution) {
  const key = resolution === "720p"
    ? "GROK_720P_PER_SECOND"
    : "GROK_480P_PER_SECOND"

  const value = Number(env[key])
  return Number.isFinite(value) && value > 0 ? value : null
}

function estimatePVideoCost(duration, resolution, draft) {
  const prices = draft
    ? {
        "720p": 0.005,
        "1080p": 0.01
      }
    : {
        "720p": 0.02,
        "1080p": 0.04
      }

  const perSecond = prices[resolution]
  return perSecond ? roundUsd(duration * perSecond) : null
}

function estimateGrokCost(env, duration, resolution) {
  const perSecond = getGrokPricePerSecond(env, resolution)
  return perSecond ? roundUsd(duration * perSecond) : null
}

function supportsGrok(duration, resolution) {
  return (
    Number.isInteger(duration) &&
    duration >= 1 &&
    duration <= 15 &&
    ["480p", "720p"].includes(resolution)
  )
}

function supportsPVideo(duration, resolution, fps) {
  return (
    Number.isInteger(duration) &&
    duration >= 1 &&
    duration <= 20 &&
    ["720p", "1080p"].includes(resolution) &&
    [24, 48].includes(fps)
  )
}

function buildComparison(env, duration, resolution, fps) {
  const grokSupported = supportsGrok(duration, resolution)
  const pvideoSupported = supportsPVideo(duration, resolution, fps)

  return {
    grok: {
      supported: grokSupported,
      estimatedCostUsd: grokSupported
        ? estimateGrokCost(env, duration, resolution)
        : null
    },
    pvideo: {
      supported: pvideoSupported,
      estimatedCostUsd: pvideoSupported
        ? estimatePVideoCost(duration, resolution, false)
        : null,
      draftEstimatedCostUsd: pvideoSupported
        ? estimatePVideoCost(duration, resolution, true)
        : null
    }
  }
}

function chooseAutoProvider(comparison) {
  if (comparison.pvideo.supported) {
    return {
      provider: "pvideo",
      reason: "P-Video foi escolhido como opção final mais barata disponível."
    }
  }

  if (comparison.grok.supported) {
    return {
      provider: "grok",
      reason: "P-Video não atende esta configuração; Grok foi escolhido."
    }
  }

  return null
}

function createVideoKey(predictionId) {
  const now = new Date()
  const year = now.getUTCFullYear()
  const month = String(now.getUTCMonth() + 1).padStart(2, "0")
  const day = String(now.getUTCDate()).padStart(2, "0")
  const safePredictionId = String(predictionId || crypto.randomUUID())
    .replace(/[^a-zA-Z0-9_-]/g, "")

  return `pvideo/${year}-${month}-${day}/${safePredictionId}-${crypto.randomUUID()}.mp4`
}

async function saveVideoToR2(env, temporaryVideoUrl, predictionId) {
  if (!env.VIDEOS) {
    throw new Error("O bucket R2 VIDEOS não está configurado no Worker.")
  }

  if (!temporaryVideoUrl) {
    throw new Error("A P-Video terminou, mas não retornou a URL temporária do MP4.")
  }

  const download = await fetch(temporaryVideoUrl)

  if (!download.ok || !download.body) {
    throw new Error(
      `Não foi possível copiar o MP4 temporário para o R2. HTTP ${download.status}.`
    )
  }

  const key = createVideoKey(predictionId)
  const contentType = download.headers.get("content-type") || "video/mp4"

  await env.VIDEOS.put(key, download.body, {
    httpMetadata: {
      contentType
    },
    customMetadata: {
      provider: "pvideo",
      predictionId: String(predictionId || "")
    }
  })

  return key
}


async function getPVideoPrediction(env, workerOrigin, predictionId) {
  if (!env.REPLICATE_API_TOKEN) {
    throw new Error("REPLICATE_API_TOKEN não está configurado no Worker.")
  }

  if (!env.CLOUDFLARE_AI_GATEWAY_TOKEN) {
    throw new Error("CLOUDFLARE_AI_GATEWAY_TOKEN não está configurado no Worker.")
  }

  const accountId = String(env.CLOUDFLARE_ACCOUNT_ID || "").trim()
  const gatewayName = "default"

  if (!accountId) {
    throw new Error("CLOUDFLARE_ACCOUNT_ID não está configurado no Worker.")
  }

  const response = await fetch(
    `https://gateway.ai.cloudflare.com/v1/${accountId}/${gatewayName}/replicate/v1/predictions/${encodeURIComponent(predictionId)}`,
    {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${env.REPLICATE_API_TOKEN}`,
        "cf-aig-authorization": `Bearer ${env.CLOUDFLARE_AI_GATEWAY_TOKEN}`
      }
    }
  )

  const result = await response.json()

  if (!response.ok) {
    throw new Error(
      result?.detail ||
      result?.error ||
      result?.message ||
      `Consulta P-Video retornou HTTP ${response.status}.`
    )
  }

  const state = String(result?.status || "starting").toLowerCase()
  const temporaryVideoUrl = Array.isArray(result?.output)
    ? result.output[0] || null
    : result?.output || null

  let permanentVideoUrl = null
  let savedR2Key = null

  if (state === "succeeded" && temporaryVideoUrl) {
    savedR2Key = await saveVideoToR2(env, temporaryVideoUrl, predictionId)
    permanentVideoUrl = `${workerOrigin}/videos/${savedR2Key}`
  }

  return {
    state,
    predictionId,
    videoUrl: permanentVideoUrl,
    temporaryVideoUrl,
    savedR2Key,
    raw: result
  }
}

async function runGrok({
  env,
  imageUrl,
  prompt,
  duration,
  resolution,
  aspectRatio
}) {
  const result = await env.AI.run(
    "xai/grok-imagine-video-1.5-preview",
    {
      image: {
        url: imageUrl
      },
      prompt,
      duration,
      resolution,
      aspect_ratio: aspectRatio
    }
  )

  return {
    state: result?.state || null,
    predictionId: null,
    videoUrl: result?.result?.video || null,
    raw: result
  }
}

async function runPVideo({
  env,
  workerOrigin,
  imageUrl,
  prompt,
  duration,
  resolution,
  aspectRatio,
  fps,
  draft
}) {
  if (!env.REPLICATE_API_TOKEN) {
    throw new Error("REPLICATE_API_TOKEN não está configurado no Worker.")
  }

  if (!env.CLOUDFLARE_AI_GATEWAY_TOKEN) {
    throw new Error("CLOUDFLARE_AI_GATEWAY_TOKEN não está configurado no Worker.")
  }

  const accountId = String(env.CLOUDFLARE_ACCOUNT_ID || "").trim()
  const gatewayName = "default"

  if (!accountId) {
    throw new Error("CLOUDFLARE_ACCOUNT_ID não está configurado no Worker.")
  }

  const response = await fetch(
    `https://gateway.ai.cloudflare.com/v1/${accountId}/${gatewayName}/replicate/v1/predictions`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Prefer": "wait",
        "Authorization": `Bearer ${env.REPLICATE_API_TOKEN}`,
        "cf-aig-authorization": `Bearer ${env.CLOUDFLARE_AI_GATEWAY_TOKEN}`
      },
      body: JSON.stringify({
        version: "prunaai/p-video",
        input: {
          prompt,
          image: imageUrl,
          duration,
          resolution,
          fps,
          aspect_ratio: aspectRatio,
          draft
        }
      })
    }
  )

  const result = await response.json()

  if (!response.ok) {
    throw new Error(
      result?.detail ||
      result?.error ||
      result?.message ||
      `P-Video retornou HTTP ${response.status}.`
    )
  }

  const state = String(result?.status || "starting").toLowerCase()

  const temporaryVideoUrl = Array.isArray(result?.output)
    ? result.output[0] || null
    : result?.output || null

  let permanentVideoUrl = null
  let savedR2Key = null

  if (state === "succeeded" && temporaryVideoUrl) {
    savedR2Key = await saveVideoToR2(
      env,
      temporaryVideoUrl,
      result?.id
    )

    permanentVideoUrl = `${workerOrigin}/videos/${savedR2Key}`
  }

  return {
    state,
    predictionId: result?.id || null,
    videoUrl: permanentVideoUrl,
    temporaryVideoUrl,
    savedR2Key,
    raw: result
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url)

    if (
      request.method === "GET" &&
      url.pathname.startsWith("/images/")
    ) {
      const key = decodeURIComponent(
        url.pathname.slice("/images/".length)
      )

      if (!key || key.includes("..")) {
        return new Response("Imagem inválida.", { status: 400 })
      }

      const object = await env.VIDEOS.get(key)

      if (!object) {
        return new Response("Imagem não encontrada.", { status: 404 })
      }

      const headers = new Headers()
      object.writeHttpMetadata(headers)
      headers.set("Cache-Control", "public, max-age=31536000, immutable")
      headers.set("Content-Disposition", "inline")

      return new Response(object.body, { headers })
    }

    if (
      request.method === "POST" &&
      url.pathname === "/upload-image"
    ) {
      try {
        const body = await request.json()
        const imageBase64 = String(body.imageBase64 || "")
        const mimeType = String(body.mimeType || "image/jpeg")

        if (!imageBase64) {
          return json(
            {
              ok: false,
              error: "imageBase64 é obrigatório."
            },
            400
          )
        }

        if (!mimeType.startsWith("image/")) {
          return json(
            {
              ok: false,
              error: "mimeType deve ser uma imagem."
            },
            400
          )
        }

        const bytes = decodeBase64ToBytes(imageBase64)

        if (bytes.byteLength < 100) {
          return json(
            {
              ok: false,
              error: "A imagem enviada está vazia ou inválida."
            },
            400
          )
        }

        if (bytes.byteLength > 15 * 1024 * 1024) {
          return json(
            {
              ok: false,
              error: "Imagem muito grande. O limite é 15 MB."
            },
            413
          )
        }

        const key = createUploadedImageKey()

        await env.VIDEOS.put(key, bytes, {
          httpMetadata: {
            contentType: mimeType
          },
          customMetadata: {
            origem: "MoviImovel APK"
          }
        })

        return json({
          ok: true,
          imageKey: key,
          imageUrl: `${url.origin}/images/${key}`
        })
      } catch (error) {
        return json(
          {
            ok: false,
            error: error?.message || "Erro ao enviar imagem."
          },
          500
        )
      }
    }


    if (
      request.method === "GET" &&
      url.pathname.startsWith("/videos/")
    ) {
      const key = decodeURIComponent(
        url.pathname.slice("/videos/".length)
      )

      if (!key || key.includes("..")) {
        return new Response("Vídeo inválido.", { status: 400 })
      }

      const object = await env.VIDEOS.get(key)

      if (!object) {
        return new Response("Vídeo não encontrado.", { status: 404 })
      }

      const headers = new Headers()
      object.writeHttpMetadata(headers)
      headers.set("Cache-Control", "public, max-age=31536000, immutable")
      headers.set("Content-Disposition", "inline")

      return new Response(object.body, {
        headers
      })
    }


    if (
      request.method === "GET" &&
      url.pathname.startsWith("/prediction-status/")
    ) {
      try {
        const predictionId = decodeURIComponent(
          url.pathname.slice("/prediction-status/".length)
        )

        if (!predictionId || predictionId.includes("..")) {
          return json({ ok: false, error: "predictionId inválido." }, 400)
        }

        const execution = await getPVideoPrediction(env, url.origin, predictionId)

        return json({
          ok: true,
          provider: "pvideo",
          state: execution.state,
          predictionId: execution.predictionId,
          videoUrl: execution.videoUrl,
          temporaryVideoUrl: execution.temporaryVideoUrl || null,
          savedR2Key: execution.savedR2Key || null
        })
      } catch (error) {
        return json(
          { ok: false, error: error?.message || "Erro ao consultar a geração." },
          500
        )
      }
    }

    if (request.method === "POST" && url.pathname === "/edit-image") {
      try {
        const body = await request.json()
        const imageUrl = String(body.imageUrl || "").trim()
        const operation = String(body.operation || "empty").trim().toLowerCase()
        const customPrompt = String(body.prompt || "").trim()
        const roomType = String(body.roomType || "").trim()
        const style = String(body.style || "").trim()

        if (!imageUrl.startsWith("https://") && !imageUrl.startsWith("http://")) {
          return json({ ok: false, error: "imageUrl deve ser uma URL pública iniciando com https:// ou http://." }, 400)
        }

        if (!["empty", "furnish"].includes(operation)) {
          return json({ ok: false, error: "operation deve ser empty ou furnish." }, 400)
        }

        const edited = await runNanoBananaEdit({
          env,
          imageUrl,
          operation,
          customPrompt,
          roomType,
          style
        })

        return json({
          ok: true,
          provider: "gemini",
          model: "gemini-2.5-flash-image",
          operation,
          imageKey: edited.imageKey,
          imageUrl: `${url.origin}/images/${edited.imageKey}`,
          promptUsed: edited.prompt,
          mimeType: edited.mimeType,
          notice: "Imagem ilustrativa editada por IA. Preserve a foto original e revise o resultado antes de publicar."
        })
      } catch (error) {
        return json({
          ok: false,
          error: friendlyImageError(error),
          technicalError: error?.message || "Erro desconhecido ao editar a imagem.",
          model: "gemini-2.5-flash-image"
        }, 500)
      }
    }

    if (request.method === "POST" && url.pathname === "/test-image-run") {
      try {
        const form = await request.formData()
        const image = form.get("image")
        const operation = String(form.get("operation") || "empty").trim().toLowerCase()
        const roomType = String(form.get("roomType") || "").trim()
        const style = String(form.get("style") || "").trim()
        const customPrompt = String(form.get("prompt") || "").trim()

        if (!(image instanceof File) || image.size < 100) {
          return new Response(imageTestPageHtml('<div class="result-error">Escolha uma foto válida antes de gerar.</div>'), {
            status: 400, headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" }
          })
        }
        if (image.size > 15 * 1024 * 1024) {
          return new Response(imageTestPageHtml('<div class="result-error">A foto ultrapassa 15 MB. Escolha uma imagem menor.</div>'), {
            status: 413, headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" }
          })
        }
        if (!["empty", "furnish"].includes(operation)) {
          throw new Error("Tipo de edição inválido.")
        }

        const mimeType = normalizeImageMimeType(image.type)
        const imageBytes = new Uint8Array(await image.arrayBuffer())
        const uploadKey = createUploadedImageKey()

        await env.VIDEOS.put(uploadKey, imageBytes, {
          httpMetadata: { contentType: mimeType },
          customMetadata: { origem: "Página de teste Movimovel" }
        })

        const edited = await runNanoBananaEdit({
          env,
          imageUrl: `${url.origin}/images/${uploadKey}`,
          operation,
          customPrompt,
          roomType,
          style
        })

        const success = `<div class="card"><div class="result-ok">Imagem gerada com sucesso.</div><img class="result-img" src="${escapeHtml(`${url.origin}/images/${edited.imageKey}`)}" alt="Imagem editada por IA"><p class="note">Foto original preservada. Resultado: imagem ilustrativa editada por IA.</p></div>`
        return new Response(imageTestPageHtml(success), {
          headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" }
        })
      } catch (error) {
        const message = friendlyImageError(error)
        const technical = escapeHtml(error?.message || "Erro desconhecido.")
        const failure = `<div class="card"><div class="result-error">${escapeHtml(message)}\n\nDetalhe técnico do teste:\n${technical}</div></div>`
        return new Response(imageTestPageHtml(failure), {
          status: 500, headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" }
        })
      }
    }

    if (request.method === "GET" && url.pathname === "/test-image") {
      return new Response(imageTestPageHtml(), {
        headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" }
      })
    }

    if (request.method === "GET" && url.pathname === "/") {
      return json({
        ok: true,
        projeto: "Movimovel Worker de Vídeo e Imagem",
        providersDisponiveis: ["grok", "pvideo", "auto", "preview", "gemini"],
        usoVideo: "POST /generate",
        usoImagem: "POST /edit-image",
        testeImagem: "GET /test-image · POST /test-image-run",
        videosPermanentes: "GET /videos/{arquivo}",
        imagensPermanentes: "GET /images/{arquivo}",
        consultaStatus: "GET /prediction-status/{id}",
        observacao: "edit-image usa Gemini 2.5 Flash Image (Nano Banana)."
      })
    }

    if (request.method !== "POST" || url.pathname !== "/generate") {
      return json(
        {
          ok: false,
          error: "Use POST /generate."
        },
        404
      )
    }

    try {
      const body = await request.json()

      const provider = String(body.provider || "auto").trim().toLowerCase()
      const imageUrl = String(body.imageUrl || "").trim()
      const prompt = String(body.prompt || "").trim()
      const duration = Math.round(toNumber(body.duration || 3, 3))
      const resolution = String(body.resolution || "720p").trim()
      const aspectRatio = String(body.aspectRatio || "16:9").trim()
      const fps = Math.round(toNumber(body.fps || 24, 24))

      if (!["grok", "pvideo", "auto", "preview"].includes(provider)) {
        return json(
          {
            ok: false,
            error: "provider deve ser grok, pvideo, auto ou preview."
          },
          400
        )
      }

      if (
        !imageUrl.startsWith("https://") &&
        !imageUrl.startsWith("http://")
      ) {
        return json(
          {
            ok: false,
            error: "imageUrl deve ser uma URL pública iniciando com https:// ou http://."
          },
          400
        )
      }

      if (!prompt) {
        return json(
          {
            ok: false,
            error: "O campo prompt é obrigatório."
          },
          400
        )
      }

      if (!Number.isInteger(duration) || duration < 1 || duration > 20) {
        return json(
          {
            ok: false,
            error: "duration deve ser um número inteiro entre 1 e 20."
          },
          400
        )
      }

      if (![24, 48].includes(fps)) {
        return json(
          {
            ok: false,
            error: "fps deve ser 24 ou 48."
          },
          400
        )
      }

      const comparison = buildComparison(env, duration, resolution, fps)

      let chosenProvider = provider
      let draft = false
      let autoReason = null

      if (provider === "preview") {
        if (!comparison.pvideo.supported) {
          return json(
            {
              ok: false,
              error: "preview exige P-Video: 720p/1080p, 1-20 segundos e fps 24/48."
            },
            400
          )
        }

        chosenProvider = "pvideo"
        draft = true
      }

      if (provider === "auto") {
        const choice = chooseAutoProvider(comparison)

        if (!choice) {
          return json(
            {
              ok: false,
              error: "Nenhum provedor atende essa configuração.",
              comparison
            },
            400
          )
        }

        chosenProvider = choice.provider
        autoReason = choice.reason
      }

      if (provider === "grok" && !comparison.grok.supported) {
        return json(
          {
            ok: false,
            error: "Grok aceita apenas 1-15 segundos em 480p ou 720p."
          },
          400
        )
      }

      if (provider === "pvideo" && !comparison.pvideo.supported) {
        return json(
          {
            ok: false,
            error: "P-Video aceita 1-20 segundos em 720p/1080p e fps 24/48."
          },
          400
        )
      }

      let execution
      let model
      let estimatedCostUsd

      if (chosenProvider === "grok") {
        model = "xai/grok-imagine-video-1.5-preview"
        estimatedCostUsd = comparison.grok.estimatedCostUsd

        execution = await runGrok({
          env,
          imageUrl,
          prompt,
          duration,
          resolution,
          aspectRatio
        })
      } else {
        model = "prunaai/p-video"
        estimatedCostUsd = estimatePVideoCost(duration, resolution, draft)

        execution = await runPVideo({
          env,
          workerOrigin: url.origin,
          imageUrl,
          prompt,
          duration,
          resolution,
          aspectRatio,
          fps,
          draft
        })
      }

      return json({
        ok: true,
        requestedProvider: provider,
        chosenProvider,
        model,
        mode: draft ? "draft" : "final",
        estimatedCostUsd,
        autoReason,
        comparison,
        state: execution.state,
        predictionId: execution.predictionId || null,
        videoUrl: execution.videoUrl,
        temporaryVideoUrl: execution.temporaryVideoUrl || null,
        savedR2Key: execution.savedR2Key || null,
        raw: execution.raw
      })
    } catch (error) {
      return json(
        {
          ok: false,
          error: error?.message || "Erro desconhecido ao gerar vídeo."
        },
        500
      )
    }
  }
}
