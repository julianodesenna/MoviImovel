function json(data, status = 200) {
  return Response.json(data, { status })
}

function toNumber(value, fallback = 0) {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

function roundUsd(value) {
  return Math.round(value * 1000) / 1000
}

function getGrokPricePerSecond(env, resolution) {
  const key = resolution === "720p"
    ? "GROK_720P_PER_SECOND"
    : "GROK_480P_PER_SECOND"

  const raw = env[key]
  const num = Number(raw)
  return Number.isFinite(num) && num > 0 ? num : null
}

function estimatePVideoCost(duration, resolution, draft) {
  const table = draft
    ? {
        "720p": 0.005,
        "1080p": 0.010
      }
    : {
        "720p": 0.020,
        "1080p": 0.040
      }

  const perSecond = table[resolution]
  if (!perSecond) return null
  return roundUsd(duration * perSecond)
}

function estimateGrokCost(env, duration, resolution) {
  const perSecond = getGrokPricePerSecond(env, resolution)
  if (!perSecond) return null
  return roundUsd(duration * perSecond)
}

function supportsGrok(duration, resolution) {
  return Number.isInteger(duration) &&
    duration >= 1 &&
    duration <= 15 &&
    ["480p", "720p"].includes(resolution)
}

function supportsPVideo(duration, resolution, fps) {
  return Number.isInteger(duration) &&
    duration >= 1 &&
    duration <= 20 &&
    ["720p", "1080p"].includes(resolution) &&
    [24, 48].includes(fps)
}

function buildComparison(env, duration, resolution, fps) {
  const grokSupported = supportsGrok(duration, resolution)
  const pvideoSupported = supportsPVideo(duration, resolution, fps)

  const grokEstimate = grokSupported
    ? estimateGrokCost(env, duration, resolution)
    : null

  const pvideoFinalEstimate = pvideoSupported
    ? estimatePVideoCost(duration, resolution, false)
    : null

  const pvideoPreviewEstimate = pvideoSupported
    ? estimatePVideoCost(duration, resolution, true)
    : null

  return {
    grok: {
      supported: grokSupported,
      estimatedCostUsd: grokEstimate,
      priceConfigured: grokEstimate !== null
    },
    pvideo: {
      supported: pvideoSupported,
      estimatedCostUsd: pvideoFinalEstimate,
      draftEstimatedCostUsd: pvideoPreviewEstimate
    }
  }
}

function chooseAutoProvider(comparison) {
  const grokReady =
    comparison.grok.supported &&
    comparison.grok.priceConfigured &&
    comparison.grok.estimatedCostUsd !== null

  const pvideoReady =
    comparison.pvideo.supported &&
    comparison.pvideo.estimatedCostUsd !== null

  if (grokReady && pvideoReady) {
    if (comparison.grok.estimatedCostUsd <= comparison.pvideo.estimatedCostUsd) {
      return {
        provider: "grok",
        reason: "Grok ficou igual ou mais barato que P-Video na comparação automática.",
        comparisonMode: "complete"
      }
    }

    return {
      provider: "pvideo",
      reason: "P-Video ficou mais barato que Grok na comparação automática.",
      comparisonMode: "complete"
    }
  }

  if (pvideoReady && !grokReady) {
    return {
      provider: "pvideo",
      reason: "Preço do Grok ainda não foi configurado; P-Video foi escolhido como opção com custo conhecido.",
      comparisonMode: "partial"
    }
  }

  if (grokReady && !pvideoReady) {
    return {
      provider: "grok",
      reason: "P-Video não atende esta combinação; Grok foi escolhido.",
      comparisonMode: "partial"
    }
  }

  if (comparison.pvideo.supported) {
    return {
      provider: "pvideo",
      reason: "Somente P-Video atende esta combinação de resolução/duração/fps.",
      comparisonMode: "fallback"
    }
  }

  if (comparison.grok.supported) {
    return {
      provider: "grok",
      reason: "Somente Grok atende esta combinação de resolução/duração.",
      comparisonMode: "fallback"
    }
  }

  return null
}

async function runGrok({ env, imageUrl, prompt, duration, resolution, aspectRatio }) {
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
    raw: result,
    videoUrl: result?.result?.video || null,
    state: result?.state || null
  }
}

async function runPVideo({
  env,
  imageUrl,
  prompt,
  duration,
  resolution,
  aspectRatio,
  fps,
  draft
}) {
  const result = await env.AI.run(
    "pruna/p-video",
    {
      prompt,
      image: imageUrl,
      duration,
      resolution,
      fps,
      aspect_ratio: aspectRatio,
      draft
    }
  )

  return {
    raw: result,
    videoUrl: result?.video || null,
    state: "completed"
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url)

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
        observacao: "auto escolhe o vídeo final mais barato; preview usa P-Video draft."
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
      let comparisonMode = null

      if (provider === "preview") {
        if (!comparison.pvideo.supported) {
          return json(
            {
              ok: false,
              error: "preview usa P-Video draft e exige resolução 720p ou 1080p, duração 1-20 e fps 24 ou 48."
            },
            400
          )
        }

        chosenProvider = "pvideo"
        draft = true
      }

      if (provider === "auto") {
        const autoChoice = chooseAutoProvider(comparison)

        if (!autoChoice) {
          return json(
            {
              ok: false,
              error: "Nenhum provedor atende esta combinação de duração, resolução e fps.",
              comparison
            },
            400
          )
        }

        chosenProvider = autoChoice.provider
        autoReason = autoChoice.reason
        comparisonMode = autoChoice.comparisonMode
      }

      if (provider === "grok" && !comparison.grok.supported) {
        return json(
          {
            ok: false,
            error: "Grok aceita apenas duração de 1 a 15 segundos e resolução 480p ou 720p."
          },
          400
        )
      }

      if (provider === "pvideo" && !comparison.pvideo.supported) {
        return json(
          {
            ok: false,
            error: "P-Video aceita duração de 1 a 20 segundos, resolução 720p ou 1080p e fps 24 ou 48."
          },
          400
        )
      }

      let execution
      let estimatedCostUsd = null
      let model = null

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
        model = "pruna/p-video"
        estimatedCostUsd = estimatePVideoCost(duration, resolution, draft)
        execution = await runPVideo({
          env,
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
        comparisonMode,
        comparison,
        state: execution.state,
        videoUrl: execution.videoUrl,
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
