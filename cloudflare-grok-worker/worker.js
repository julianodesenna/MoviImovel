
function decodeBase64ToBytes(base64Text) {
  const binary = atob(base64Text)
  const bytes = new Uint8Array(binary.length)

  for (let index = 0; index < binary.length; index++) {
    bytes[index] = binary.charCodeAt(index)
  }

  return bytes
}

function decodeIncomingImageBase64(value) {
  const raw = String(value || "").trim()
  if (!raw) return new Uint8Array()

  // Aceita tanto Base64 puro quanto data URL. O APK usa Base64 puro.
  const commaIndex = raw.indexOf(",")
  const payload = raw.toLowerCase().startsWith("data:image/") && commaIndex >= 0
    ? raw.slice(commaIndex + 1)
    : raw

  return decodeBase64ToBytes(payload)
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

function getImageDimensions(bytes) {
  const b = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes)

  // PNG
  if (
    b.length >= 24 &&
    b[0] === 0x89 &&
    b[1] === 0x50 &&
    b[2] === 0x4e &&
    b[3] === 0x47
  ) {
    return {
      width: ((b[16] << 24) | (b[17] << 16) | (b[18] << 8) | b[19]) >>> 0,
      height: ((b[20] << 24) | (b[21] << 16) | (b[22] << 8) | b[23]) >>> 0
    }
  }

  // JPEG
  if (b.length >= 4 && b[0] === 0xff && b[1] === 0xd8) {
    let i = 2

    while (i + 9 < b.length) {
      if (b[i] !== 0xff) {
        i++
        continue
      }

      const marker = b[i + 1]
      i += 2

      if (marker === 0xd8 || marker === 0xd9) continue
      if (i + 1 >= b.length) break

      const length = (b[i] << 8) + b[i + 1]
      if (length < 2 || i + length > b.length) break

      const sofMarkers = [
        0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6,
        0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf
      ]

      if (sofMarkers.includes(marker) && i + 7 < b.length) {
        return {
          height: (b[i + 3] << 8) + b[i + 4],
          width: (b[i + 5] << 8) + b[i + 6]
        }
      }

      i += length
    }
  }

  // WEBP VP8X
  if (
    b.length >= 30 &&
    String.fromCharCode(...b.slice(0, 4)) === "RIFF" &&
    String.fromCharCode(...b.slice(8, 12)) === "WEBP" &&
    String.fromCharCode(...b.slice(12, 16)) === "VP8X"
  ) {
    return {
      width: 1 + b[24] + (b[25] << 8) + (b[26] << 16),
      height: 1 + b[27] + (b[28] << 8) + (b[29] << 16)
    }
  }

  return null
}

function clampFluxDimension(value) {
  const rounded = Math.round(Number(value || 0) / 32) * 32
  return Math.max(256, Math.min(1920, rounded || 1024))
}

function getFluxOutputSize(imageBytes) {
  const input = getImageDimensions(imageBytes)

  if (!input || !input.width || !input.height) {
    return {
      inputWidth: null,
      inputHeight: null,
      width: 1024,
      height: 1024,
      orientation: "unknown"
    }
  }

  const portrait = input.height > input.width
  const landscape = input.width > input.height
  const longestSide = 1024
  const scale = longestSide / Math.max(input.width, input.height)

  let width = clampFluxDimension(input.width * scale)
  let height = clampFluxDimension(input.height * scale)

  if (portrait && width >= height) {
    width = Math.max(256, height - 32)
  }

  if (landscape && height >= width) {
    height = Math.max(256, width - 32)
  }

  return {
    inputWidth: input.width,
    inputHeight: input.height,
    width,
    height,
    orientation: portrait ? "portrait" : landscape ? "landscape" : "square"
  }
}

function buildImageEditPrompt(operation, customPrompt, roomType, style) {
  const architectureRules = [
    "This is a faithful real-estate photo cleanup, not a redesign.",
    "Preserve the exact original architecture and geometry of the photographed property.",
    "Keep exactly unchanged: room width, room depth, ceiling height, wall positions, wall spacing, floor perspective, camera position, camera angle, lens feel, field of view, framing and image orientation.",
    "Preserve every fixed structural element exactly where it is: doors, door openings, windows, window size, window position, walls, ceiling, floor, baseboards, electrical outlets, switches, light fixtures and fixed appliances.",
    "The room may be narrow or elongated. Preserve its real narrow or elongated shape exactly.",
    "Do not enlarge, widen, deepen, stretch, open up, modernize, renovate or redesign the room.",
    "Do not create, remove, move, replace or modify doors, windows, walls, passages, ceiling lines, floor lines or structural openings.",
    "Do not change perspective, crop, zoom out, reframe or change the aspect ratio.",
    "Do not add people, animals, watermarks, logos or text.",
    "This is an illustrative AI edit for a real-estate presentation; do not hide structural defects."
  ].join(" ")

  if (operation === "empty") {
    const requested = customPrompt
      ? customPrompt.trim()
      : "Remove only movable furniture, loose objects, appliances, decorations, rugs, shelves, ladders and clutter."

    return [
      requested,
      "Only fill the specific areas previously occupied by the removed movable objects.",
      "Do not regenerate the whole room.",
      "Do not invent hidden architectural elements behind objects.",
      architectureRules
    ].join(" ")
  }

  const safeRoom = roomType || "room"
  const safeStyle = style || "modern, elegant and neutral"
  const requested = customPrompt
    ? customPrompt.trim()
    : `Furnish this ${safeRoom} with realistic ${safeStyle} furniture and discreet decor appropriate for a real-estate listing.`

  return [
    requested,
    "Do not block doors, windows, passages or fixed appliances.",
    architectureRules
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

const FLUX_IMAGE_MODELS = {
  klein4b: {
    id: "@cf/black-forest-labs/flux-2-klein-4b",
    name: "FLUX.2 klein 4B",
    steps: null,
    origin: "Movimovel FLUX.2 klein 4B"
  },
  dev: {
    id: "@cf/black-forest-labs/flux-2-dev",
    name: "FLUX.2 dev",
    steps: "8",
    origin: "Movimovel FLUX.2 dev (8 etapas)"
  }
}

function getFluxImageModel(modelKey) {
  const key = String(modelKey || "klein4b").trim().toLowerCase()
  const model = FLUX_IMAGE_MODELS[key]
  if (!model) {
    throw new Error("Modelo de imagem inválido. Use klein4b ou dev.")
  }
  return { key, ...model }
}

async function runFluxEditFromBytes({ env, imageBytes, mimeType, operation, customPrompt, roomType, style, modelKey = "klein4b" }) {
  if (!env.AI || typeof env.AI.run !== "function") {
    throw new Error("Workers AI (binding AI) não está configurado no Worker.")
  }

  const bytes = imageBytes instanceof Uint8Array ? imageBytes : new Uint8Array(imageBytes)
  const contentType = normalizeImageMimeType(mimeType)
  const model = getFluxImageModel(modelKey)

  if (bytes.byteLength < 100) {
    throw new Error("A imagem enviada está vazia ou inválida.")
  }
  if (bytes.byteLength > 15 * 1024 * 1024) {
    throw new Error("A imagem excede 15 MB. Reduza o tamanho antes de editar.")
  }

  const prompt = buildImageEditPrompt(operation, customPrompt, roomType, style)
  const outputSize = getFluxOutputSize(bytes)
  const guidance = "2.2"
  const editMode = "preserve_architecture"

  const form = new FormData()
  form.append("prompt", prompt)
  form.append("input_image_0", new Blob([bytes], { type: contentType }), "ambiente.jpg")

  // Mantém proporção e orientação da foto original.
  form.append("width", String(outputSize.width))
  form.append("height", String(outputSize.height))

  // Guidance mais conservador para reduzir recriação de arquitetura.
  form.append("guidance", guidance)

  if (model.steps) form.append("steps", model.steps)

  const serialized = new Response(form)
  const contentTypeHeader = serialized.headers.get("content-type")
  const result = await env.AI.run(model.id, {
    multipart: {
      body: serialized.body,
      contentType: contentTypeHeader
    }
  })

  const imageBase64 = String(result?.image || "")
  if (!imageBase64) {
    throw new Error("A FLUX respondeu, mas não retornou uma imagem editada.")
  }

  const imageOutput = decodeBase64ToBytes(imageBase64)
  const key = createEditedImageKey()
  if (!env.VIDEOS) throw new Error("O bucket R2 VIDEOS não está configurado no Worker.")
  await env.VIDEOS.put(key, imageOutput, {
    httpMetadata: { contentType: "image/jpeg" },
    customMetadata: { origem: model.origin, operacao: operation, modelo: model.id }
  })

  return {
    prompt,
    imageKey: key,
    mimeType: "image/jpeg",
    raw: result,
    model,
    outputSize,
    guidance,
    editMode
  }
}

async function runFluxEdit({ env, imageUrl, operation, customPrompt, roomType, style, modelKey = "klein4b" }) {
  const source = await fetch(imageUrl)
  if (!source.ok) throw new Error(`Não foi possível baixar a imagem de origem. HTTP ${source.status}.`)
  return runFluxEditFromBytes({
    env,
    imageBytes: new Uint8Array(await source.arrayBuffer()),
    mimeType: source.headers.get("content-type"),
    operation, customPrompt, roomType, style, modelKey
  })
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
    return "Não foi possível autorizar a edição de imagem. Verifique a vinculação Workers AI com o nome AI no Worker."
  }

  return "Não foi possível editar a imagem. Verifique sua conexão e tente novamente."
}

function escapeHtml(value) {
  return String(value || "").replace(/[&<>'"]/g, char => ({"&":"&amp;","<":"&lt;",">":"&gt;","'":"&#39;","\"":"&quot;"}[char]))
}

function imageTestPageHtml(message = "", technical = "", imageUrl = "") {
  const status = message ? `<div class="status ${imageUrl ? "ok" : "error"}"><strong>${escapeHtml(message)}</strong>${technical ? `<pre>${escapeHtml(technical)}</pre>` : ""}</div>` : ""
  const result = imageUrl ? `<div class="card"><img class="result" src="${escapeHtml(imageUrl)}" alt="Imagem editada"></div>` : ""
  return `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Movimovel · Teste FLUX duplo · Imagem IA</title><style>
body{margin:0;background:#ececef;color:#151515;font:18px Arial,sans-serif}main{max-width:680px;margin:auto;padding:24px 16px 44px}h1{font-size:30px;margin:0 0 8px}.sub{color:#5c5c5c;line-height:1.45}.card{background:#fff;border:1px solid #d7d4cd;border-radius:18px;padding:18px;margin:16px 0;box-shadow:0 5px 18px #0000000d}label{display:block;font-weight:700;margin:14px 0 8px}input,select,textarea,button{box-sizing:border-box;width:100%;font:inherit;border-radius:12px}input,select,textarea{padding:14px;border:1px solid #c8c5be;background:#fff}textarea{min-height:120px}button{margin-top:18px;padding:16px;border:1px solid #b89349;background:#141414;color:#fff;font-weight:700}.status{padding:16px;border-radius:14px;line-height:1.45;margin:16px 0}.error{background:#fff0f0;color:#842b2b}.ok{background:#edf8ef;color:#205f2f}pre{margin:12px 0 0;white-space:pre-wrap;word-break:break-word;font:14px monospace}.note{color:#6b6256;font-size:14px;line-height:1.45}.result{width:100%;border-radius:14px;display:block}</style></head><body><main><h1>Teste FLUX duplo</h1><p class="sub">Escolha entre FLUX.2 klein 4B (econômico) e FLUX.2 dev (qualidade). A foto original é preservada.</p>${status}${result}<form class="card" action="/test-image-submit" method="post" enctype="multipart/form-data"><label>Foto do ambiente</label><input name="photo" type="file" accept="image/jpeg,image/png,image/webp" required><label>Modelo de imagem</label><select name="modelKey"><option value="klein4b">FLUX.2 klein 4B — econômico</option><option value="dev">FLUX.2 dev — qualidade</option></select><label>Tipo de edição</label><select name="operation"><option value="empty">Esvaziar ambiente</option><option value="furnish">Mobiliar ambiente</option></select><label>Tipo de cômodo (opcional)</label><input name="roomType" placeholder="Ex.: sala, quarto, cozinha"><label>Estilo (opcional)</label><input name="style" placeholder="Ex.: moderno, clean, madeira clara"><label>Pedido adicional (opcional)</label><textarea name="prompt" placeholder="Ex.: manter portas, janelas, piso e iluminação. Remover apenas os móveis."></textarea><button type="submit">Gerar imagem</button><p class="note">Após tocar em Gerar imagem, o navegador abrirá a resposta. Não depende de JavaScript.</p></form></main></body></html>`
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

// MOVIMOVEL_DYNAMIC_IMAGE_LAB_V1
// Laboratório dinâmico: catálogo Cloudflare + biblioteca aprovada + API para o app.

const MOVIMOVEL_LAB = {
  catalogKey: "laboratorio-ia/catalogo-imagem-v1.json",
  libraryKey: "laboratorio-ia/biblioteca-aprovada-v1.json",
  testPrefix: "laboratorio-ia/testes",
  imagePrefix: "laboratorio-ia/resultados"
}

function labNow() {
  return new Date().toISOString()
}

function labJson(data, status = 200) {
  return new Response(JSON.stringify(data, null, 2), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store"
    }
  })
}

function labText(value) {
  if (value == null || typeof value === "object" || typeof value === "function") return ""
  return String(value).trim()
}

function labSafeModelId(value) {
  const id = labText(value)
  if (!id || id.length > 250 || /[\\\r\n]/.test(id)) {
    throw new Error("ID de modelo inválido.")
  }
  return id
}

function labSecretValid(request, env) {
  const expected = labText(env.LAB_KEY)
  const supplied = labText(request.headers.get("x-movimovel-lab-key"))
  return Boolean(expected && supplied && expected === supplied)
}

function labUnauthorized() {
  return labJson({ ok: false, error: "Senha do laboratório inválida." }, 401)
}

function labModelToken(env) {
  const token = labText(env.CF_MODEL_CATALOG_TOKEN)
  if (!token) {
    throw new Error("CF_MODEL_CATALOG_TOKEN não foi configurado no Worker.")
  }
  return token
}

async function labReadR2Json(env, key, fallback) {
  if (!env.VIDEOS) throw new Error("O bucket R2 VIDEOS não está configurado.")
  const object = await env.VIDEOS.get(key)
  if (!object) return fallback
  try {
    return await object.json()
  } catch {
    return fallback
  }
}

async function labWriteR2Json(env, key, data) {
  if (!env.VIDEOS) throw new Error("O bucket R2 VIDEOS não está configurado.")
  await env.VIDEOS.put(key, JSON.stringify(data, null, 2), {
    httpMetadata: { contentType: "application/json; charset=utf-8" },
    customMetadata: {
      origem: "Movimovel Laboratório IA Dinâmico",
      atualizadoEm: labNow()
    }
  })
}

function labArray(value) {
  if (Array.isArray(value)) return value
  if (value == null) return []
  return [value]
}

function labFirstText(...values) {
  for (const value of values) {
    const text = labText(value)
    if (text) return text
  }
  return ""
}

function labTaskNames(raw) {
  const values = [
    raw?.task,
    raw?.tasks,
    raw?.task_name,
    raw?.taskName,
    raw?.capabilities,
    raw?.capability
  ]

  const names = []
  for (const value of values) {
    for (const item of labArray(value)) {
      if (typeof item === "string") names.push(item)
      else if (item && typeof item === "object") {
        names.push(
          labFirstText(item.name, item.task, item.id, item.label, item.slug)
        )
      }
    }
  }

  return [...new Set(names.filter(Boolean))]
}

function labInferAdapter(modelId, tasks, description) {
  const all = `${modelId} ${tasks.join(" ")} ${description}`.toLowerCase()

  if (all.includes("stable-diffusion-v1-5-inpainting") || all.includes("inpainting")) {
    return "inpaint"
  }

  if (all.includes("flux-2-klein") || all.includes("flux-2-dev")) {
    return "flux_reference"
  }

  if (all.includes("image-to-image") || all.includes("img2img")) {
    return "image_to_image_json"
  }

  if (all.includes("text-to-image") || all.includes("text to image")) {
    return "text_to_image"
  }

  return "catalog_only"
}

function labIsImageRelated(model) {
  const text = JSON.stringify(model || {}).toLowerCase()
  return /text-to-image|text to image|image-to-image|image to image|inpainting|image-to-video|image to video|image generation|image edit|img2img/.test(text)
}

function labNormalizeModel(raw) {
  const modelId = labFirstText(
    raw?.model,
    raw?.model_id,
    raw?.modelId,
    raw?.id,
    raw?.slug,
    raw?.name
  )

  const name = labFirstText(
    raw?.name,
    raw?.display_name,
    raw?.displayName,
    raw?.label,
    modelId
  )

  const description = labFirstText(raw?.description, raw?.summary, raw?.details)
  const tasks = labTaskNames(raw)
  const provider = labFirstText(
    raw?.author?.name,
    raw?.author,
    raw?.provider,
    raw?.source?.name,
    raw?.source
  )

  return {
    modelId,
    name,
    description,
    tasks,
    provider,
    experimental: Boolean(raw?.experimental || raw?.is_experimental),
    deprecated: Boolean(raw?.deprecated || raw?.is_deprecated || raw?.planned_deprecation_date),
    adapter: labInferAdapter(modelId, tasks, description)
  }
}

async function labRefreshCatalog(env) {
  const token = labModelToken(env)
  const accountId = labText(env.CLOUDFLARE_ACCOUNT_ID)
  if (!accountId) throw new Error("CLOUDFLARE_ACCOUNT_ID não está configurado.")

  const models = []
  let page = 1

  while (page <= 10) {
    const endpoint = new URL(
      `https://api.cloudflare.com/client/v4/accounts/${accountId}/ai/models/search`
    )
    endpoint.searchParams.set("per_page", "100")
    endpoint.searchParams.set("page", String(page))
    endpoint.searchParams.set("hide_experimental", "false")

    const response = await fetch(endpoint.toString(), {
      headers: { Authorization: `Bearer ${token}` }
    })

    const body = await response.json().catch(() => null)
    if (!response.ok || !body?.success) {
      const details = Array.isArray(body?.errors)
        ? body.errors.map(item => item?.message || JSON.stringify(item)).join(" | ")
        : ""
      throw new Error(`Cloudflare não liberou o catálogo. HTTP ${response.status}${details ? `: ${details}` : ""}`)
    }

    const batch = Array.isArray(body.result)
      ? body.result
      : Array.isArray(body?.result?.data)
        ? body.result.data
        : []

    models.push(...batch)
    if (batch.length < 100) break
    page += 1
  }

  const filtered = models
    .filter(labIsImageRelated)
    .map(labNormalizeModel)
    .filter(item => item.modelId)
    .sort((a, b) => a.name.localeCompare(b.name, "pt-BR"))

  const catalog = {
    version: 1,
    refreshedAt: labNow(),
    totalFromCloudflare: models.length,
    imageModels: filtered
  }

  await labWriteR2Json(env, MOVIMOVEL_LAB.catalogKey, catalog)
  return catalog
}

async function labGetCatalog(env) {
  return labReadR2Json(env, MOVIMOVEL_LAB.catalogKey, {
    version: 1,
    refreshedAt: null,
    totalFromCloudflare: 0,
    imageModels: []
  })
}

function labDefaultLibrary() {
  return {
    version: 1,
    updatedAt: null,
    models: []
  }
}

async function labGetLibrary(env) {
  const library = await labReadR2Json(env, MOVIMOVEL_LAB.libraryKey, labDefaultLibrary())
  if (!Array.isArray(library.models)) library.models = []
  return library
}

function labAllowedAdapter(value) {
  const adapter = labText(value)
  const allowed = [
    "text_to_image",
    "flux_reference",
    "inpaint",
    "image_to_image_json",
    "catalog_only"
  ]
  return allowed.includes(adapter) ? adapter : "catalog_only"
}

function labAllowedSlot(value) {
  const slot = labText(value)
  const allowed = [
    "empty_property",
    "furnish_property",
    "edit_any_photo",
    "remove_with_mask",
    "create_image",
    "movement_image"
  ]
  return allowed.includes(slot) ? slot : ""
}

function labPublicLibraryEntry(entry) {
  return {
    id: entry.id,
    modelId: entry.modelId,
    name: entry.name,
    description: entry.description || "",
    tasks: Array.isArray(entry.tasks) ? entry.tasks : [],
    provider: entry.provider || "",
    adapter: entry.adapter,
    appEnabled: Boolean(entry.appEnabled),
    appSlot: entry.appSlot || "",
    settings: entry.settings && typeof entry.settings === "object" ? entry.settings : {},
    approvedAt: entry.approvedAt || null,
    lastTest: entry.lastTest || null
  }
}

async function labSaveLibraryEntry(env, payload) {
  const library = await labGetLibrary(env)
  const modelId = labSafeModelId(payload?.modelId)
  const incoming = {
    id: modelId,
    modelId,
    name: labText(payload?.name) || modelId,
    description: labText(payload?.description),
    tasks: Array.isArray(payload?.tasks) ? payload.tasks.map(labText).filter(Boolean) : [],
    provider: labText(payload?.provider),
    adapter: labAllowedAdapter(payload?.adapter),
    appEnabled: Boolean(payload?.appEnabled),
    appSlot: labAllowedSlot(payload?.appSlot),
    settings: payload?.settings && typeof payload.settings === "object" ? payload.settings : {},
    approvedAt: labNow(),
    lastTest: payload?.lastTest && typeof payload.lastTest === "object" ? payload.lastTest : null
  }

  if (incoming.appEnabled && !incoming.appSlot) {
    throw new Error("Escolha em qual função do app este modelo deve aparecer.")
  }

  const index = library.models.findIndex(item => item?.modelId === modelId)
  if (index >= 0) library.models[index] = incoming
  else library.models.push(incoming)

  library.models.sort((a, b) => a.name.localeCompare(b.name, "pt-BR"))
  library.updatedAt = labNow()

  await labWriteR2Json(env, MOVIMOVEL_LAB.libraryKey, library)
  return incoming
}

async function labDeleteLibraryEntry(env, modelId) {
  const library = await labGetLibrary(env)
  const safeId = labSafeModelId(modelId)
  library.models = library.models.filter(item => item?.modelId !== safeId)
  library.updatedAt = labNow()
  await labWriteR2Json(env, MOVIMOVEL_LAB.libraryKey, library)
}

function labImageDimensions(bytes) {
  const b = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes)

  if (b.length >= 24 && b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47) {
    return {
      width: ((b[16] << 24) | (b[17] << 16) | (b[18] << 8) | b[19]) >>> 0,
      height: ((b[20] << 24) | (b[21] << 16) | (b[22] << 8) | b[23]) >>> 0
    }
  }

  if (b.length >= 4 && b[0] === 0xff && b[1] === 0xd8) {
    const sof = new Set([0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf])
    let i = 2
    while (i + 9 < b.length) {
      if (b[i] !== 0xff) { i += 1; continue }
      const marker = b[i + 1]
      i += 2
      if (marker === 0xd8 || marker === 0xd9) continue
      if (i + 1 >= b.length) break
      const length = (b[i] << 8) + b[i + 1]
      if (length < 2 || i + length > b.length) break
      if (sof.has(marker) && i + 7 < b.length) {
        return { height: (b[i + 3] << 8) + b[i + 4], width: (b[i + 5] << 8) + b[i + 6] }
      }
      i += length
    }
  }

  if (b.length >= 30 && String.fromCharCode(...b.slice(0, 4)) === "RIFF" && String.fromCharCode(...b.slice(8, 12)) === "WEBP" && String.fromCharCode(...b.slice(12, 16)) === "VP8X") {
    return {
      width: 1 + b[24] + (b[25] << 8) + (b[26] << 16),
      height: 1 + b[27] + (b[28] << 8) + (b[29] << 16)
    }
  }

  return null
}

function labClampSize(value, fallback = 768) {
  const number = Math.round(Number(value))
  return Math.max(256, Math.min(2048, Number.isFinite(number) ? number : fallback))
}

function labOutputSize(bytes, requestedWidth, requestedHeight) {
  if (requestedWidth && requestedHeight) {
    return { width: labClampSize(requestedWidth), height: labClampSize(requestedHeight), source: null }
  }
  const source = labImageDimensions(bytes)
  if (!source?.width || !source?.height) return { width: 1024, height: 1024, source: null }
  const scale = 1024 / Math.max(source.width, source.height)
  const w = Math.max(256, Math.round((source.width * scale) / 32) * 32)
  const h = Math.max(256, Math.round((source.height * scale) / 32) * 32)
  return { width: w, height: h, source }
}

function labGetFile(form, field, required = false) {
  const item = form.get(field)
  if (!(item instanceof File)) {
    if (required) throw new Error(`Envie ${field === "photo" ? "uma foto" : "uma máscara"}.`)
    return null
  }
  if (!item.type.startsWith("image/")) throw new Error("O arquivo enviado precisa ser uma imagem.")
  return item
}

async function labBytesFromFile(file) {
  const bytes = new Uint8Array(await file.arrayBuffer())
  if (bytes.byteLength < 100) throw new Error("A imagem enviada está vazia ou inválida.")
  if (bytes.byteLength > 15 * 1024 * 1024) throw new Error("A imagem ultrapassa o limite de 15 MB.")
  return bytes
}

function labExtractBase64Image(body) {
  const candidates = [
    body?.image,
    body?.result?.image,
    body?.result?.output_image?.data,
    body?.output_image?.data,
    body?.result?.images?.[0]
  ]
  for (const value of candidates) {
    if (typeof value === "string" && value.length > 50) return value
  }
  return ""
}

async function labRunJsonModel(env, modelId, input) {
  if (!env.AI || typeof env.AI.run !== "function") {
    throw new Error("Workers AI (binding AI) não está configurado no Worker.")
  }

  const body = await env.AI.run(modelId, input)
  const imageBase64 = labExtractBase64Image(body)

  if (!imageBase64) {
    throw new Error("O modelo respondeu, mas não retornou imagem neste modo. Escolha outro adaptador ou verifique o modelo.")
  }

  return {
    bytes: decodeBase64ToBytes(imageBase64),
    mimeType: "image/jpeg"
  }
}

async function labRunFluxReference(env, modelId, photoBytes, photoMime, prompt, options) {
  const size = labOutputSize(photoBytes, options.width, options.height)
  const form = new FormData()
  form.append("prompt", prompt)
  form.append("input_image_0", new Blob([photoBytes], { type: normalizeImageMimeType(photoMime) }), "referencia.jpg")
  form.append("width", String(size.width))
  form.append("height", String(size.height))
  form.append("guidance", String(Math.min(10, Math.max(0, Number(options.guidance) || 2.4))))
  if (options.steps) form.append("steps", String(Math.max(1, Math.min(30, Math.round(Number(options.steps)) || 8))))

  const serialized = new Response(form)
  const output = await env.AI.run(modelId, {
    multipart: {
      body: serialized.body,
      contentType: serialized.headers.get("content-type")
    }
  })

  const imageBase64 = labExtractBase64Image(output)
  if (!imageBase64) throw new Error("O modelo FLUX não retornou uma imagem.")

  return { bytes: decodeBase64ToBytes(imageBase64), mimeType: "image/jpeg", size }
}

async function labRunInpainting(env, modelId, photoBytes, maskBytes, prompt, options) {
  const photoSize = labImageDimensions(photoBytes)
  const maskSize = labImageDimensions(maskBytes)
  if (!photoSize?.width || !maskSize?.width || photoSize.width !== maskSize.width || photoSize.height !== maskSize.height) {
    throw new Error("A máscara precisa ter exatamente o mesmo tamanho da foto.")
  }

  const output = await env.AI.run(modelId, {
    prompt,
    image: Array.from(photoBytes),
    mask: Array.from(maskBytes),
    negative_prompt: labText(options.negativePrompt) || undefined,
    num_steps: Math.max(1, Math.min(20, Math.round(Number(options.steps) || 20))),
    strength: Math.min(1, Math.max(0, Number(options.strength) || 0.45)),
    guidance: Math.min(20, Math.max(0, Number(options.guidance) || 6))
  })

  const bytes = new Uint8Array(await new Response(output).arrayBuffer())
  if (bytes.byteLength < 100) throw new Error("O Inpainting não retornou uma imagem válida.")
  return { bytes, mimeType: "image/png", size: { width: photoSize.width, height: photoSize.height, source: photoSize } }
}

async function labSaveGeneratedImage(env, bytes, mimeType, modelId, adapter) {
  const now = new Date()
  const key = `${MOVIMOVEL_LAB.imagePrefix}/${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, "0")}-${String(now.getUTCDate()).padStart(2, "0")}/${crypto.randomUUID()}.${mimeType.includes("png") ? "png" : "jpg"}`
  await env.VIDEOS.put(key, bytes, {
    httpMetadata: { contentType: mimeType },
    customMetadata: { origem: "Movimovel Laboratório IA", modelId, adapter }
  })
  return key
}

async function labRunTest(request, env, origin) {
  const form = await request.formData()
  const modelId = labSafeModelId(form.get("modelId"))
  const adapter = labAllowedAdapter(form.get("adapter"))
  const prompt = labText(form.get("prompt"))
  const negativePrompt = labText(form.get("negativePrompt"))
  const options = {
    width: form.get("width"),
    height: form.get("height"),
    steps: form.get("steps"),
    strength: form.get("strength"),
    guidance: form.get("guidance"),
    negativePrompt
  }

  if (!prompt) throw new Error("Escreva um pedido para testar o modelo.")
  if (adapter === "catalog_only") throw new Error("Este modelo ainda não tem adaptador de teste nesta página. Ele pode ser salvo no catálogo, mas precisa de integração específica antes de gerar.")

  let output
  if (adapter === "text_to_image") {
    const input = { prompt }
    if (negativePrompt) input.negative_prompt = negativePrompt
    if (form.get("width")) input.width = labClampSize(form.get("width"))
    if (form.get("height")) input.height = labClampSize(form.get("height"))
    if (form.get("steps")) input.num_steps = Math.max(1, Math.min(30, Math.round(Number(form.get("steps")) || 15)))
    if (form.get("guidance")) input.guidance = Math.min(20, Math.max(0, Number(form.get("guidance")) || 4))
    output = await labRunJsonModel(env, modelId, input)
  } else if (adapter === "flux_reference") {
    const photo = labGetFile(form, "photo", true)
    output = await labRunFluxReference(env, modelId, await labBytesFromFile(photo), photo.type, prompt, options)
  } else if (adapter === "inpaint") {
    const photo = labGetFile(form, "photo", true)
    const mask = labGetFile(form, "mask", true)
    output = await labRunInpainting(env, modelId, await labBytesFromFile(photo), await labBytesFromFile(mask), prompt, options)
  } else if (adapter === "image_to_image_json") {
    const photo = labGetFile(form, "photo", true)
    const bytes = await labBytesFromFile(photo)
    const input = { prompt, image: Array.from(bytes) }
    if (negativePrompt) input.negative_prompt = negativePrompt
    if (form.get("strength")) input.strength = Math.min(1, Math.max(0, Number(form.get("strength")) || 0.45))
    output = await labRunJsonModel(env, modelId, input)
  } else {
    throw new Error("Adaptador inválido.")
  }

  const imageKey = await labSaveGeneratedImage(env, output.bytes, output.mimeType, modelId, adapter)
  const test = {
    id: crypto.randomUUID(),
    at: labNow(),
    modelId,
    adapter,
    prompt,
    negativePrompt,
    imageKey,
    imageUrl: `${origin}/images/${imageKey}`
  }
  await labWriteR2Json(env, `${MOVIMOVEL_LAB.testPrefix}/${test.id}.json`, test)

  return test
}

function labPageHtml() {
  return `<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Movimovel · Laboratório Dinâmico</title><style>
:root{color-scheme:dark}*{box-sizing:border-box}body{margin:0;background:#0c1013;color:#eef3f4;font:16px Arial,sans-serif}main{max-width:940px;margin:auto;padding:18px 13px 42px}h1{margin:0 0 6px;font-size:28px}.sub{color:#b5c0c5;line-height:1.45}.card{background:#151d22;border:1px solid #32424b;border-radius:16px;padding:15px;margin-top:14px}label{display:block;font-weight:700;margin:13px 0 6px}input,select,textarea,button{width:100%;font:inherit;border-radius:10px}input,select,textarea{background:#0c1013;color:#eef3f4;border:1px solid #52636e;padding:11px}textarea{min-height:95px;resize:vertical}button{border:0;padding:13px;background:#d66a25;color:#fff;font-weight:700;margin-top:12px}.subbtn{background:#33444e}.danger{background:#8f3d3d}.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.hidden{display:none!important}.small{font-size:13px;color:#b4c0c5;line-height:1.4}.ok,.err,.warn{padding:12px;border-radius:10px;margin-top:14px;white-space:pre-wrap;word-break:break-word}.ok{background:#153120;color:#bff4cb}.err{background:#3d1919;color:#ffd1d1}.warn{background:#3a2b13;color:#ffe0a3}.model{border-top:1px solid #30404a;padding:12px 0}.model:first-child{border-top:0}.model b{display:block}.tag{display:inline-block;margin:5px 5px 0 0;padding:3px 7px;background:#26343c;border-radius:999px;font-size:12px;color:#c9e3ed}.result{display:block;width:100%;margin-top:12px;border-radius:12px;background:#000}.canvaswrap{background:#050708;border:1px dashed #627782;border-radius:10px;overflow:hidden;margin-top:8px}.canvaswrap canvas{width:100%;max-height:500px;display:block;touch-action:none;cursor:crosshair}.rowbtn{display:grid;grid-template-columns:1fr 1fr;gap:8px}.libraryitem{border-top:1px solid #30404a;padding:12px 0}.libraryitem:first-child{border-top:0}@media(max-width:650px){.grid,.rowbtn{grid-template-columns:1fr}}
</style></head><body><main>
<h1>Laboratório dinâmico de imagens</h1><p class="sub">Catálogo Cloudflare, testes reais, aprovadas salvas e preparação para aparecer no app. Atualizar catálogo não roda IA; gerar teste roda a IA escolhida.</p>
<div id="statusTop"></div>
<div class="card"><label>Senha do laboratório</label><input id="key" type="password" autocomplete="off" placeholder="Digite a senha definida no Termux"><button id="saveKey" class="subbtn" type="button">Guardar nesta aba</button><p class="small">A senha fica apenas nesta aba do navegador enquanto ela estiver aberta.</p></div>
<div class="card"><div class="rowbtn"><button id="refresh" type="button">Atualizar catálogo Cloudflare</button><button id="loadCatalog" class="subbtn" type="button">Abrir catálogo salvo</button></div><label>Buscar modelo</label><input id="search" placeholder="Ex.: flux, stable, image, leonardo"><div id="catalog" class="small">Carregue o catálogo.</div></div>
<div id="work" class="card hidden"><b id="chosenName"></b><div id="chosenMeta" class="small"></div><label>Modo de teste</label><select id="adapter"><option value="text_to_image">Texto → imagem</option><option value="flux_reference">Foto de referência → imagem (FLUX)</option><option value="inpaint">Foto + máscara (Inpainting)</option><option value="image_to_image_json">Foto → imagem (JSON experimental)</option><option value="catalog_only">Somente catálogo / sem teste ainda</option></select><label>Pedido</label><textarea id="prompt" placeholder="Descreva a geração ou edição que deseja testar."></textarea><div id="negativeBox"><label>O que evitar (opcional)</label><textarea id="negative" placeholder="Ex.: texto, marca d'água, pessoas"></textarea></div><div id="photoBox"><label>Foto de referência</label><input id="photo" type="file" accept="image/jpeg,image/png,image/webp"></div><div id="maskBox" class="hidden"><label>Máscara</label><p class="warn">Pinte somente o que pode mudar. Vermelho = área que a IA poderá alterar.</p><input id="brush" type="range" min="12" max="140" value="48"><div class="canvaswrap"><canvas id="maskCanvas"></canvas></div><button id="clearMask" type="button" class="subbtn">Limpar marcação</button></div><div class="grid"><div><label>Largura (opcional)</label><input id="width" type="number" min="256" max="2048" placeholder="Ex.: 768"></div><div><label>Altura (opcional)</label><input id="height" type="number" min="256" max="2048" placeholder="Ex.: 1024"></div><div><label>Etapas (opcional)</label><input id="steps" type="number" min="1" max="30" placeholder="Ex.: 15"></div><div><label>Guidance (opcional)</label><input id="guidance" type="number" step="0.1" min="0" max="20" placeholder="Ex.: 4"></div><div><label>Strength (opcional)</label><input id="strength" type="number" step="0.05" min="0" max="1" placeholder="Ex.: 0.45"></div></div><button id="test" type="button">Gerar teste real</button><hr style="border-color:#30404a;margin:22px 0"><label><input id="enableApp" type="checkbox" style="width:auto"> Disponibilizar no app depois da atualização do APK</label><label>Onde aparecerá no app</label><select id="slot"><option value="">Somente biblioteca</option><option value="empty_property">Esvaziar imóvel</option><option value="furnish_property">Mobiliar imóvel</option><option value="edit_any_photo">Editar qualquer foto</option><option value="remove_with_mask">Remover com máscara</option><option value="create_image">Criar imagem nova</option><option value="movement_image">Movimento de imagem</option></select><button id="approve" type="button" class="subbtn">Salvar na biblioteca aprovada</button></div>
<div class="card"><b>Biblioteca aprovada</b><div id="library" class="small">Carregando…</div></div><div id="status"></div><div id="result"></div>
</main><script>
(function(){
var key=document.getElementById('key'),catalogEl=document.getElementById('catalog'),search=document.getElementById('search'),work=document.getElementById('work'),chosenName=document.getElementById('chosenName'),chosenMeta=document.getElementById('chosenMeta'),adapter=document.getElementById('adapter'),prompt=document.getElementById('prompt'),negative=document.getElementById('negative'),photo=document.getElementById('photo'),photoBox=document.getElementById('photoBox'),maskBox=document.getElementById('maskBox'),width=document.getElementById('width'),height=document.getElementById('height'),steps=document.getElementById('steps'),guidance=document.getElementById('guidance'),strength=document.getElementById('strength'),enableApp=document.getElementById('enableApp'),slot=document.getElementById('slot'),libraryEl=document.getElementById('library'),statusTop=document.getElementById('statusTop'),status=document.getElementById('status'),result=document.getElementById('result'),canvas=document.getElementById('maskCanvas'),brush=document.getElementById('brush');var models=[],chosen=null,lastTest=null,source=document.createElement('canvas'),mask=document.createElement('canvas'),overlay=document.createElement('canvas'),draw=false,last=null,painted=false;
function esc(x){return String(x||'').replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]})}function note(t,c){var h=t?'<div class="'+c+'">'+esc(t)+'</div>':'';status.innerHTML=h;statusTop.innerHTML=h}function headers(){return {'x-movimovel-lab-key':key.value}}function api(path,opt){opt=opt||{};opt.headers=Object.assign({},headers(),opt.headers||{});return fetch(path,opt).then(async function(r){var b=await r.json().catch(function(){return {ok:false,error:'Resposta inválida'}});if(!r.ok||b.ok===false)throw new Error(b.error||'Erro');return b})}function selectedMode(){var v=adapter.value;photoBox.classList.toggle('hidden',v==='text_to_image'||v==='catalog_only');maskBox.classList.toggle('hidden',v!=='inpaint');}
function renderCatalog(){var q=search.value.toLowerCase().trim(),arr=models.filter(function(m){return !q||JSON.stringify(m).toLowerCase().includes(q)});catalogEl.innerHTML=arr.length?arr.map(function(m,i){return '<div class="model"><b>'+esc(m.name)+'</b><span class="tag">'+esc(m.adapter)+'</span><span class="tag">'+esc((m.tasks||[]).join(', ')||'tipo não informado')+'</span><div class="small">'+esc(m.modelId)+'<br>'+esc(m.description||'')+'</div><button data-i="'+models.indexOf(m)+'" class="pick subbtn" type="button">Selecionar</button></div>'}).join(''):'Nenhum modelo encontrado.';Array.prototype.forEach.call(document.querySelectorAll('.pick'),function(b){b.onclick=function(){select(models[Number(b.dataset.i)])}})}
function select(m){chosen=m;lastTest=null;work.classList.remove('hidden');chosenName.textContent=m.name;chosenMeta.textContent=m.modelId+' · '+(m.provider||'sem fornecedor informado')+' · '+(m.tasks||[]).join(', ');adapter.value=m.adapter||'catalog_only';selectedMode();window.scrollTo({top:work.offsetTop-10,behavior:'smooth'})}
async function catalog(refresh){note('Carregando catálogo…','ok');var b=await api(refresh?'/laboratorio-ia/catalogo/atualizar':'/laboratorio-ia/catalogo');models=b.catalog.imageModels||[];renderCatalog();note('Catálogo pronto: '+models.length+' modelos de imagem.','ok')}
async function library(){if(!key.value.trim()){libraryEl.textContent='Digite e guarde a senha para abrir a biblioteca.';return}var b=await api('/laboratorio-ia/biblioteca');var arr=b.library.models||[];libraryEl.innerHTML=arr.length?arr.map(function(m){return '<div class="libraryitem"><b>'+esc(m.name)+'</b><div>'+esc(m.modelId)+'</div><span class="tag">'+esc(m.adapter)+'</span>'+ (m.appEnabled?'<span class="tag">app: '+esc(m.appSlot)+'</span>':'')+'<button class="del danger" data-id="'+esc(m.modelId)+'" type="button">Remover da biblioteca</button></div>'}).join(''):'Nenhum modelo aprovado ainda.';Array.prototype.forEach.call(document.querySelectorAll('.del'),function(b){b.onclick=async function(){if(!confirm('Remover este modelo da biblioteca?'))return;try{await api('/laboratorio-ia/biblioteca',{method:'DELETE',headers:{'content-type':'application/json'},body:JSON.stringify({modelId:b.dataset.id})});await library();note('Modelo removido.','ok')}catch(e){note(e.message,'err')}}})}
function redraw(){if(!source.width)return;var c=canvas.getContext('2d');c.clearRect(0,0,canvas.width,canvas.height);c.drawImage(source,0,0);c.drawImage(overlay,0,0)}function resetMask(){if(!source.width)return;var mc=mask.getContext('2d');mc.fillStyle='#000';mc.fillRect(0,0,mask.width,mask.height);overlay.getContext('2d').clearRect(0,0,overlay.width,overlay.height);painted=false;redraw()}async function loadPhoto(){var f=photo.files[0];if(!f)return;var bm=await createImageBitmap(f);var sc=Math.min(1024/bm.width,1024/bm.height,1),w=Math.round(bm.width*sc),h=Math.round(bm.height*sc);[source,mask,overlay,canvas].forEach(function(c){c.width=w;c.height=h});source.getContext('2d').drawImage(bm,0,0,w,h);resetMask()}function point(e){var r=canvas.getBoundingClientRect();return{x:(e.clientX-r.left)*canvas.width/r.width,y:(e.clientY-r.top)*canvas.height/r.height}}function stroke(a,b){var w=Number(brush.value)*canvas.width/Math.max(1,canvas.clientWidth);[[mask.getContext('2d'),'#fff'],[overlay.getContext('2d'),'rgba(255,45,45,.55)']].forEach(function(p){p[0].strokeStyle=p[1];p[0].lineWidth=w;p[0].lineCap='round';p[0].beginPath();p[0].moveTo(a.x,a.y);p[0].lineTo(b.x,b.y);p[0].stroke()});painted=true;redraw()}
canvas.onpointerdown=function(e){if(adapter.value!=='inpaint'||!source.width)return;draw=true;last=point(e);canvas.setPointerCapture(e.pointerId);stroke(last,last)};canvas.onpointermove=function(e){if(!draw)return;var n=point(e);stroke(last,n);last=n};canvas.onpointerup=canvas.onpointercancel=function(){draw=false;last=null};photo.onchange=function(){loadPhoto().catch(function(e){note(e.message,'err')})};document.getElementById('clearMask').onclick=resetMask;adapter.onchange=selectedMode;search.oninput=renderCatalog;
function blob(c,t,q){return new Promise(function(ok){c.toBlob(ok,t,q)})}async function test(){if(!chosen)throw new Error('Selecione uma IA no catálogo.');if(adapter.value==='catalog_only')throw new Error('Este modelo não tem adaptador de teste nesta primeira versão. Salve na biblioteca apenas depois que definirmos a integração dele.');if(!prompt.value.trim())throw new Error('Escreva um pedido.');if(adapter.value!=='text_to_image'&&!photo.files[0])throw new Error('Escolha uma foto.');if(adapter.value==='inpaint'&&!painted)throw new Error('Pinte os objetos que podem ser alterados na máscara.');var d=new FormData();d.append('modelId',chosen.modelId);d.append('adapter',adapter.value);d.append('prompt',prompt.value);d.append('negativePrompt',negative.value);d.append('width',width.value);d.append('height',height.value);d.append('steps',steps.value);d.append('guidance',guidance.value);d.append('strength',strength.value);if(adapter.value==='inpaint'){d.append('photo',await blob(source,'image/jpeg',.92),'foto.jpg');d.append('mask',await blob(mask,'image/png'),'mascara.png')}else if(adapter.value!=='text_to_image'){d.append('photo',photo.files[0])}var b=await api('/laboratorio-ia/testar',{method:'POST',body:d});lastTest=b;result.innerHTML='<div class="card"><img class="result" src="'+esc(b.imageUrl)+'"><div class="small">Modelo: '+esc(b.modelId)+'\nAdaptador: '+esc(b.adapter)+'\nTeste: '+esc(b.id)+'</div></div>';note('Teste concluído. Revise o resultado e, se aprovar, salve na biblioteca.','ok')}
async function approve(){if(!chosen)throw new Error('Selecione uma IA.');var p={modelId:chosen.modelId,name:chosen.name,description:chosen.description,tasks:chosen.tasks,provider:chosen.provider,adapter:adapter.value,appEnabled:enableApp.checked,appSlot:slot.value,settings:{width:width.value,height:height.value,steps:steps.value,guidance:guidance.value,strength:strength.value},lastTest:lastTest};var b=await api('/laboratorio-ia/biblioteca',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(p)});await library();note('Salvo na biblioteca: '+b.entry.name,'ok')}
 document.getElementById('saveKey').onclick=async function(){if(!key.value.trim()){note('Digite a senha do laboratório antes de guardar.','err');return}sessionStorage.setItem('movimovelLabKey',key.value);note('Senha guardada. Verificando acesso à biblioteca…','ok');try{await library();note('Senha aceita. Agora toque em Atualizar catálogo Cloudflare.','ok')}catch(e){libraryEl.textContent='Não foi possível abrir a biblioteca.';note(e.message,'err')}};key.value=sessionStorage.getItem('movimovelLabKey')||'';document.getElementById('refresh').onclick=function(){catalog(true).catch(function(e){note(e.message,'err')})};document.getElementById('loadCatalog').onclick=function(){catalog(false).catch(function(e){note(e.message,'err')})};document.getElementById('test').onclick=function(){note('Gerando teste real…','ok');test().catch(function(e){note(e.message,'err')})};document.getElementById('approve').onclick=function(){approve().catch(function(e){note(e.message,'err')})};if(key.value.trim()){library().catch(function(e){libraryEl.textContent='Não foi possível abrir a biblioteca.';note(e.message,'err')})}else{libraryEl.textContent='Digite e guarde a senha para abrir a biblioteca.'}
})();</script></body></html>`
}

async function labHandleCatalog(request, env, refresh) {
  if (!labSecretValid(request, env)) return labUnauthorized()
  const catalog = refresh ? await labRefreshCatalog(env) : await labGetCatalog(env)
  return labJson({ ok: true, catalog })
}

async function labHandleLibrary(request, env) {
  if (!labSecretValid(request, env)) return labUnauthorized()
  if (request.method === "GET") return labJson({ ok: true, library: await labGetLibrary(env) })
  if (request.method === "POST") {
    const entry = await labSaveLibraryEntry(env, await request.json())
    return labJson({ ok: true, entry })
  }
  if (request.method === "DELETE") {
    const body = await request.json()
    await labDeleteLibraryEntry(env, body?.modelId)
    return labJson({ ok: true })
  }
  return labJson({ ok: false, error: "Método não permitido." }, 405)
}

async function labHandleTest(request, env, origin) {
  if (!labSecretValid(request, env)) return labUnauthorized()
  const test = await labRunTest(request, env, origin)
  return labJson({ ok: true, ...test })
}

async function labHandleAppModels(env, url) {
  const slot = labAllowedSlot(url.searchParams.get("slot"))
  const library = await labGetLibrary(env)
  const models = library.models
    .filter(item => item?.appEnabled && (!slot || item?.appSlot === slot))
    .map(labPublicLibraryEntry)
  return labJson({ ok: true, models, updatedAt: library.updatedAt || null })
}


export default {
  async fetch(request, env) {
    const url = new URL(request.url)


    // MOVIMOVEL_DYNAMIC_IMAGE_LAB_ROUTES_V1
    if (request.method === "GET" && url.pathname === "/laboratorio-ia") {
      return new Response(labPageHtml(), {
        headers: {
          "content-type": "text/html; charset=utf-8",
          "cache-control": "no-store"
        }
      })
    }

    if (request.method === "GET" && url.pathname === "/laboratorio-ia/catalogo") {
      try {
        return await labHandleCatalog(request, env, false)
      } catch (error) {
        return labJson({
          ok: false,
          error: String(error?.message || "Erro ao abrir o catálogo.")
        }, 500)
      }
    }

    if (request.method === "POST" && url.pathname === "/laboratorio-ia/catalogo/atualizar") {
      try {
        return await labHandleCatalog(request, env, true)
      } catch (error) {
        return labJson({
          ok: false,
          error: String(error?.message || "Erro ao atualizar o catálogo.")
        }, 500)
      }
    }

    if (
      ["GET", "POST", "DELETE"].includes(request.method) &&
      url.pathname === "/laboratorio-ia/biblioteca"
    ) {
      try {
        return await labHandleLibrary(request, env)
      } catch (error) {
        return labJson({
          ok: false,
          error: String(error?.message || "Erro na biblioteca.")
        }, 500)
      }
    }

    if (request.method === "POST" && url.pathname === "/laboratorio-ia/testar") {
      try {
        return await labHandleTest(request, env, url.origin)
      } catch (error) {
        return labJson({
          ok: false,
          error: String(error?.message || "Erro no teste.")
        }, 500)
      }
    }

    // Este endpoint já deixa pronta a lista que o APK consultará depois.
    if (request.method === "GET" && url.pathname === "/app/image-models") {
      try {
        return await labHandleAppModels(env, url)
      } catch (error) {
        return labJson({
          ok: false,
          error: String(error?.message || "Erro ao abrir modelos do app.")
        }, 500)
      }
    }

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
      const traceId = `flux-${crypto.randomUUID()}`
      let requestedModelKey = "klein4b"
      let stage = "recebendo_pedido"
      try {
        const body = await request.json()
        const imageBase64 = String(body.imageBase64 || "").trim()
        const mimeType = normalizeImageMimeType(body.mimeType || "image/jpeg")
        const imageUrl = String(body.imageUrl || "").trim()
        const operation = String(body.operation || "empty").trim().toLowerCase()
        const customPrompt = String(body.prompt || "").trim()
        const roomType = String(body.roomType || "").trim()
        const style = String(body.style || "").trim()
        const modelKey = String(body.modelKey || "klein4b").trim().toLowerCase()
        requestedModelKey = modelKey

        if (!["empty", "furnish"].includes(operation)) {
          return json({ ok: false, error: "operation deve ser empty ou furnish.", diagnostics: { traceId, stage, modelKey } }, 400)
        }

        let edited

        if (imageBase64) {
          // Caminho principal do APK: evita salvar e buscar a foto de origem no R2.
          stage = "recebendo_foto_direta_do_apk"
          if (!mimeType.startsWith("image/")) {
            return json({ ok: false, error: "mimeType deve ser uma imagem.", diagnostics: { traceId, stage, modelKey } }, 400)
          }

          const imageBytes = decodeIncomingImageBase64(imageBase64)
          if (imageBytes.byteLength < 100) {
            return json({ ok: false, error: "A imagem enviada está vazia ou inválida.", diagnostics: { traceId, stage, modelKey } }, 400)
          }
          if (imageBytes.byteLength > 15 * 1024 * 1024) {
            return json({ ok: false, error: "Imagem muito grande. O limite é 15 MB.", diagnostics: { traceId, stage, modelKey } }, 413)
          }

          stage = "enviando_foto_direta_para_flux"
          edited = await runFluxEditFromBytes({
            env,
            imageBytes,
            mimeType,
            operation,
            customPrompt,
            roomType,
            style,
            modelKey
          })
        } else {
          // Compatibilidade com chamadas antigas que ainda enviam apenas imageUrl.
          if (!imageUrl.startsWith("https://") && !imageUrl.startsWith("http://")) {
            return json({ ok: false, error: "Envie imageBase64 (preferido pelo APK) ou imageUrl público.", diagnostics: { traceId, stage, modelKey } }, 400)
          }

          stage = "baixando_foto_por_url"
          edited = await runFluxEdit({
            env,
            imageUrl,
            operation,
            customPrompt,
            roomType,
            style,
            modelKey
          })
        }

        stage = "imagem_gerada_e_salva"
        return json({
          ok: true,
          provider: "cloudflare-workers-ai",
          model: edited.model.id,
          modelKey,
          operation,
          imageKey: edited.imageKey,
          imageUrl: `${url.origin}/images/${edited.imageKey}`,
          promptUsed: edited.prompt,
          mimeType: edited.mimeType,
          diagnostics: {
            traceId,
            stage,
            version: "FLUX-PRESERVAR-ARQUITETURA-20260627",
            editMode: edited.editMode,
            guidance: edited.guidance,
            inputWidth: edited.outputSize.inputWidth,
            inputHeight: edited.outputSize.inputHeight,
            outputWidth: edited.outputSize.width,
            outputHeight: edited.outputSize.height,
            orientation: edited.outputSize.orientation
          },
          notice: "Imagem ilustrativa editada por IA. Preserve a foto original e revise o resultado antes de publicar."
        })
      } catch (error) {
        return json({
          ok: false,
          error: friendlyImageError(error),
          technicalError: String(error?.message || "Erro desconhecido ao editar a imagem."),
          modelKey: requestedModelKey,
          model: "FLUX não concluído",
          diagnostics: {
            traceId,
            stage,
            version: "FLUX-PRESERVAR-ARQUITETURA-20260627",
            editMode: "preserve_architecture"
          }
        }, 500)
      }
    }

    if (request.method === "POST" && url.pathname === "/test-image-submit") {
      try {
        const form = await request.formData()
        const photo = form.get("photo")
        const operation = String(form.get("operation") || "empty").trim().toLowerCase()
        const roomType = String(form.get("roomType") || "").trim()
        const style = String(form.get("style") || "").trim()
        const customPrompt = String(form.get("prompt") || "").trim()
        const modelKey = String(form.get("modelKey") || "klein4b").trim().toLowerCase()
        if (!(photo instanceof File)) throw new Error("Escolha uma foto antes de gerar.")
        if (!photo.type.startsWith("image/")) throw new Error("O arquivo escolhido não é uma imagem válida.")
        if (!['empty','furnish'].includes(operation)) throw new Error("Tipo de edição inválido.")
        const edited = await runFluxEditFromBytes({
          env,
          imageBytes: new Uint8Array(await photo.arrayBuffer()),
          mimeType: photo.type,
          operation, customPrompt, roomType, style, modelKey
        })
        return new Response(imageTestPageHtml("Imagem pronta.", "", `${url.origin}/images/${edited.imageKey}`), { headers: { "Content-Type":"text/html; charset=utf-8", "Cache-Control":"no-store" } })
      } catch (error) {
        const technical = error?.message || "Erro desconhecido ao editar imagem."
        return new Response(imageTestPageHtml(friendlyImageError(error), technical), { status: 400, headers: { "Content-Type":"text/html; charset=utf-8", "Cache-Control":"no-store" } })
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
        providersDisponiveis: ["grok", "pvideo", "auto", "preview", "cloudflare-workers-ai-flux"],
        usoVideo: "POST /generate",
        usoImagem: "POST /edit-image",
        testeImagem: "GET /test-image (formulário sem JavaScript)",
        videosPermanentes: "GET /videos/{arquivo}",
        imagensPermanentes: "GET /images/{arquivo}",
        consultaStatus: "GET /prediction-status/{id}",
        observacao: "edit-image aceita foto direta do APK em Base64 e envia ao FLUX sem buscar a imagem de origem no R2. O APK reduz a referência para até 512 px antes de enviar ao FLUX.",
        modelosImagem: {
          klein4b: FLUX_IMAGE_MODELS.klein4b.id,
          dev: FLUX_IMAGE_MODELS.dev.id
        },
        versaoImagem: "FLUX-PRESERVAR-ARQUITETURA-20260627"
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
