
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

function json(data, status = 200) {
  return Response.json(data, { status })
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

    if (request.method === "GET" && url.pathname === "/") {
      return json({
        ok: true,
        projeto: "MoviImovel Worker de Vídeo",
        providersDisponiveis: [
          "grok",
          "pvideo",
          "auto",
          "preview"
        ],
        uso: "POST /generate",
        videosPermanentes: "GET /videos/{arquivo}",
        consultaStatus: "GET /prediction-status/{id}",
        observacao: "preview usa P-Video draft; auto usa P-Video enquanto Grok aguarda ativação."
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
