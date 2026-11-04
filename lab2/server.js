#!/usr/bin/env node
// Weather MCP server：透過 stdio 提供天氣查詢工具，資料來源為 Open-Meteo（免 API key）。
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import net from "node:net";

// Node 在 IPv4/IPv6 間自動切換時，每次連線只等 250ms；網路延遲較高時會誤判逾時（fetch failed），所以放寬
net.setDefaultAutoSelectFamilyAttemptTimeout(2000);

const GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search";
const FORECAST_URL = "https://api.open-meteo.com/v1/forecast";

// WMO 天氣代碼 → 中文描述
const WEATHER_CODES = {
  0: "晴朗", 1: "大致晴朗", 2: "局部多雲", 3: "陰天",
  45: "霧", 48: "霧淞",
  51: "毛毛雨（小）", 53: "毛毛雨（中）", 55: "毛毛雨（大）",
  56: "凍毛毛雨（小）", 57: "凍毛毛雨（大）",
  61: "小雨", 63: "中雨", 65: "大雨",
  66: "凍雨（小）", 67: "凍雨（大）",
  71: "小雪", 73: "中雪", 75: "大雪", 77: "雪粒",
  80: "陣雨（小）", 81: "陣雨（中）", 82: "陣雨（強）",
  85: "陣雪（小）", 86: "陣雪（大）",
  95: "雷雨", 96: "雷雨伴隨小冰雹", 99: "雷雨伴隨大冰雹",
};

const describe = (code) => WEATHER_CODES[code] ?? `未知天氣代碼 ${code}`;

async function fetchJson(url, params) {
  const res = await fetch(`${url}?${new URLSearchParams(params)}`, {
    signal: AbortSignal.timeout(15000),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
  return res.json();
}

async function searchLocations(name, countryCode, count = 5) {
  const params = { name, count: String(count), language: "zh", format: "json" };
  if (countryCode) params.countryCode = countryCode.toUpperCase();
  const data = await fetchJson(GEOCODING_URL, params);
  return data.results ?? [];
}

const formatPlace = (p) =>
  [p.name, p.admin1, p.country].filter(Boolean).join("、");

// 工具執行失敗時回傳 isError，讓模型看得到錯誤原因並自行修正，而不是讓整個 server 崩潰
const errorResult = (message) => ({
  content: [{ type: "text", text: message }],
  isError: true,
});

const server = new McpServer({ name: "weather", version: "1.0.0" });

server.registerTool(
  "search_locations",
  {
    title: "搜尋地點",
    description:
      "依名稱搜尋地點，回傳候選清單（名稱、行政區、國家、經緯度、人口）。" +
      "地名有歧義或 get_weather 查到錯誤地點時使用。名稱請用英文，中文地名常查不到或對應錯誤。",
    inputSchema: {
      name: z.string().min(1).describe("地點英文名稱，例如 Kaohsiung、Springfield"),
      country_code: z
        .string()
        .length(2)
        .optional()
        .describe("ISO 3166-1 alpha-2 國碼，用來縮小範圍，例如 TW、JP、US"),
    },
    annotations: { readOnlyHint: true, openWorldHint: true },
  },
  async ({ name, country_code }) => {
    try {
      const results = await searchLocations(name, country_code, 10);
      if (results.length === 0) return errorResult(`找不到地點「${name}」，請改用其他英文拼法。`);
      const lines = results.map(
        (p, i) =>
          `${i + 1}. ${formatPlace(p)}（${p.country_code}）` +
          ` 緯度 ${p.latitude.toFixed(4)}, 經度 ${p.longitude.toFixed(4)}` +
          (p.population ? `，人口 ${p.population.toLocaleString()}` : ""),
      );
      return { content: [{ type: "text", text: lines.join("\n") }] };
    } catch (e) {
      return errorResult(`搜尋地點失敗：${e.message}`);
    }
  },
);

server.registerTool(
  "get_weather",
  {
    title: "查詢天氣",
    description:
      "查詢城市的目前天氣與未來數日預報（天氣狀況、氣溫、體感溫度、濕度、風速、每日降雨機率）。" +
      "城市名稱請用英文（例如「台北」傳 Taipei）。同名地點會取人口最多者；" +
      "若需指定國家請加 country_code，或改傳 latitude/longitude。",
    inputSchema: {
      city: z.string().min(1).optional().describe("城市英文名稱，例如 Taipei、Tokyo"),
      country_code: z.string().length(2).optional().describe("ISO 國碼，例如 TW、JP，用於同名城市"),
      latitude: z.number().min(-90).max(90).optional().describe("緯度，與 longitude 一起使用時會略過 city"),
      longitude: z.number().min(-180).max(180).optional().describe("經度"),
      days: z.number().int().min(1).max(16).default(3).describe("預報天數 1-16，預設 3"),
    },
    annotations: { readOnlyHint: true, openWorldHint: true },
  },
  async ({ city, country_code, latitude, longitude, days }) => {
    try {
      let label;
      if (latitude !== undefined && longitude !== undefined) {
        label = city ?? "指定座標";
      } else if (city) {
        const results = await searchLocations(city, country_code);
        if (results.length === 0) {
          return errorResult(`找不到城市「${city}」，請改用英文名稱，或先用 search_locations 搜尋。`);
        }
        const place = results.reduce((a, b) => ((b.population ?? 0) > (a.population ?? 0) ? b : a));
        ({ latitude, longitude } = place);
        label = formatPlace(place);
      } else {
        return errorResult("請提供 city，或同時提供 latitude 與 longitude。");
      }

      const f = await fetchJson(FORECAST_URL, {
        latitude: String(latitude),
        longitude: String(longitude),
        current: "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m",
        daily: "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max",
        timezone: "auto",
        forecast_days: String(days),
      });

      const cur = f.current;
      const d = f.daily;
      const lines = [
        `地點：${label}（${latitude.toFixed(2)}, ${longitude.toFixed(2)}）`,
        `觀測時間：${cur.time}（${f.timezone}）`,
        `目前天氣：${describe(cur.weather_code)}`,
        `氣溫：${cur.temperature_2m}°C（體感 ${cur.apparent_temperature}°C）`,
        `濕度：${cur.relative_humidity_2m}%　風速：${cur.wind_speed_10m} km/h`,
        "",
        `未來 ${days} 天預報：`,
        ...d.time.map((date, i) => {
          const rain = d.precipitation_probability_max[i];
          return (
            `  ${date}  ${describe(d.weather_code[i])}  ` +
            `${d.temperature_2m_min[i]}~${d.temperature_2m_max[i]}°C  ` +
            `降雨機率 ${rain ?? "N/A"}${rain != null ? "%" : ""}`
          );
        }),
      ];
      return { content: [{ type: "text", text: lines.join("\n") }] };
    } catch (e) {
      return errorResult(`無法取得天氣資料：${e.message}`);
    }
  },
);

// stdio 模式下 stdout 是 MCP 協定通道，log 只能寫到 stderr
await server.connect(new StdioServerTransport());
console.error("weather MCP server running on stdio");
