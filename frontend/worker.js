// Same-origin proxy to the Spring Boot API, so the SPA's relative /api and /student
// calls work unchanged and the backend needs no CORS. Static files are served by
// Cloudflare before this runs (see run_worker_first in wrangler.jsonc).
export default {
  async fetch(request, env) {
    const url = new URL(request.url)
    return fetch(new Request(env.BACKEND_URL + url.pathname + url.search, request))
  },
}
