// Pluggable web search for the AI research endpoint (#17). Tavily is used
// because it has a free tier and a simple REST API; swap this file for
// Bing/Serper/etc. without touching the route.
async function search(query) {
  const apiKey = process.env.TAVILY_API_KEY;
  if (!apiKey) {
    return { configured: false, results: [] };
  }

  const response = await fetch("https://api.tavily.com/search", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      api_key: apiKey,
      query,
      max_results: 5
    })
  });

  if (!response.ok) {
    throw new Error(`Tavily search failed: HTTP ${response.status}`);
  }

  const data = await response.json();
  const results = (data.results || []).map((r) => ({
    title: r.title,
    url: r.url,
    content: r.content
  }));

  return { configured: true, results };
}

module.exports = { search };
