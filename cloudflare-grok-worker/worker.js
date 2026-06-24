export default {
  async fetch(request, env) {
    const url = new URL(request.url)

    if (request.method === "GET" && url.pathname === "/") {
      return Response.json({
        ok: true,
        projeto: "MoviImovel Grok Worker",
        mensagem: "Worker online. Use POST /generate para solicitar um vídeo."
      })
    }

    if (request.method !== "POST" || url.pathname !== "/generate") {
      return Response.json(
        {
          ok: false,
          error: "Rota não encontrada. Use POST /generate."
        },
        { status: 404 }
      )
    }

    try {
      const body = await request.json()

      const imageUrl = String(body.imageUrl || "").trim()
      const prompt = String(body.prompt || "").trim()
      const duration = Number(body.duration || 3)
      const resolution = String(body.resolution || "480p")
      const aspectRatio = String(body.aspectRatio || "16:9")

      if (
        !imageUrl.startsWith("https://") &&
        !imageUrl.startsWith("http://")
      ) {
        return Response.json(
          {
            ok: false,
            error: "imageUrl deve ser uma URL pública iniciando com https:// ou http://."
          },
          { status: 400 }
        )
      }

      if (!prompt) {
        return Response.json(
          {
            ok: false,
            error: "O campo prompt é obrigatório."
          },
          { status: 400 }
        )
      }

      if (!Number.isInteger(duration) || duration < 1 || duration > 15) {
        return Response.json(
          {
            ok: false,
            error: "duration deve ser um número inteiro entre 1 e 15."
          },
          { status: 400 }
        )
      }

      if (!["480p", "720p"].includes(resolution)) {
        return Response.json(
          {
            ok: false,
            error: "resolution deve ser 480p ou 720p."
          },
          { status: 400 }
        )
      }

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

      return Response.json({
        ok: true,
        state: result?.state || null,
        videoUrl: result?.result?.video || null,
        raw: result
      })
    } catch (error) {
      return Response.json(
        {
          ok: false,
          error: error?.message || "Erro desconhecido ao chamar o Grok."
        },
        { status: 500 }
      )
    }
  }
}
