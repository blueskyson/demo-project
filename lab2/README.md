# Lab 2：開發一個「天氣查詢」MCP Server

[Lab 1](../lab1/README.md) 用 **Skill** 教 Claude 怎麼查天氣。本 lab 改用 **MCP（Model Context Protocol）** 實作同一個功能，讓 Claude Code、Claude Desktop、Cursor 等支援 MCP 的客戶端都能直接呼叫天氣工具。

## Skill 與 MCP 的差別

| | Lab 1：Skill | Lab 2：MCP |
|---|---|---|
| 提供什麼 | **做法**：步驟、規則、回覆格式 | **能力**：可被呼叫的工具（tool） |
| 形式 | `SKILL.md` + 腳本 | 獨立執行的 server 程式 |
| Claude 怎麼用 | 讀說明後自己執行 `python3 get_weather.py` | 直接呼叫 `get_weather` 工具，拿到結果 |
| 參數驗證 | 靠 Claude 照著說明組指令 | 由 JSON Schema（zod）強制驗證 |
| 可攜性 | 支援 SKILL.md 格式的 agent | 任何 MCP 客戶端 |

兩者可以搭配：MCP 提供 `get_weather` 工具，Skill 規定拿到天氣後該怎麼回覆、給哪些建議。

## 目錄結構

```
lab2/
├── README.md        ← 本文件
├── package.json     ← 依賴與 npm scripts
├── server.js        ← MCP server 本體
├── test-client.js   ← 用 MCP 協定實際呼叫 server 的測試程式
└── .gitignore       ← 排除 node_modules/
```

專案根目錄的 `.mcp.json` 負責把這個 server 註冊給 Claude Code。

---

## MCP 的基本概念

```
┌──────────────┐   JSON-RPC over stdio   ┌──────────────────┐   HTTPS   ┌────────────┐
│ MCP Client   │ ──────────────────────▶ │ MCP Server       │ ────────▶ │ Open-Meteo │
│ (Claude Code)│ ◀────────────────────── │ (lab2/server.js) │ ◀──────── │ API        │
└──────────────┘                         └──────────────────┘           └────────────┘
```

MCP server 可以提供三種東西：

| 類型 | 說明 | 本 lab |
|------|------|--------|
| **Tools** | 讓模型呼叫的函式（有輸入 schema） | ✅ `get_weather`、`search_locations` |
| **Resources** | 讓客戶端讀取的資料（以 URI 識別） | 延伸練習 |
| **Prompts** | 預先定義的提示範本，使用者可在客戶端選用 | 延伸練習 |

傳輸方式（transport）有兩種：

- **stdio**：客戶端把 server 當子程序啟動，透過 stdin/stdout 溝通，適合本機工具。本 lab 用這種。
- **Streamable HTTP**：server 獨立部署成網路服務，適合遠端、多人共用或需要 OAuth 的情境。

---

## 開發步驟

### Step 1：設計工具介面

MCP 開發最重要的一步是**設計給模型用的工具**。重點如下：

| 工具 | 輸入 | 用途 |
|------|------|------|
| `get_weather` | `city`、`country_code`、`latitude`/`longitude`、`days` | 主要功能：查目前天氣和預報 |
| `search_locations` | `name`、`country_code` | 地名有歧義時列出候選地點，讓模型自己挑 |

設計原則：

1. **工具數量少而精**。所有工具的定義都會一直佔用模型的 context，不要把每個 API 端點都包成一個工具。
2. **description 要寫給模型看**：說明用途、何時使用、參數格式。例如「城市名稱請用英文」就是把 Lab 1 踩過的坑直接寫進工具說明。
3. **提供修正路徑**。`get_weather` 查錯地點時，模型可以改用 `country_code`、`search_locations` 或直接傳經緯度。
4. **標註 annotations**。`readOnlyHint: true` 告訴客戶端這個工具不會修改任何東西，`openWorldHint: true` 表示它會存取外部網路。

### Step 2：建立專案、安裝 SDK

```bash
mkdir lab2 && cd lab2
npm init -y
npm install @modelcontextprotocol/sdk zod
```

在 `package.json` 加上 `"type": "module"`，才能使用 ESM 的 `import` 語法。

> 官方也有 Python SDK（`pip install mcp`，使用 `FastMCP`），寫法類似。本機沒有 pip，所以這裡用 Node.js。Node 18 以上內建 `fetch`，不需要額外的 HTTP 套件。

### Step 3：撰寫 server

`server.js` 的骨架：

```js
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

const server = new McpServer({ name: "weather", version: "1.0.0" });

server.registerTool(
  "get_weather",
  {
    title: "查詢天氣",
    description: "查詢城市的目前天氣與未來數日預報……",
    inputSchema: {
      city: z.string().optional().describe("城市英文名稱"),
      days: z.number().int().min(1).max(16).default(3),
    },
    annotations: { readOnlyHint: true, openWorldHint: true },
  },
  async ({ city, days }) => {
    // ...呼叫 API...
    return { content: [{ type: "text", text: "地點：台北……" }] };
  },
);

await server.connect(new StdioServerTransport());
```

實作重點：

1. **`inputSchema` 用 zod 定義**。SDK 會自動轉成 JSON Schema 給模型，並在呼叫時驗證參數。`.describe()` 的文字會變成每個參數的說明。
2. **錯誤要回傳 `isError: true`，不要 throw**。這樣模型看得到錯誤訊息，能自己修正後重試，例如換個拼法或改用 `search_locations`。
3. **stdio 模式下絕對不能用 `console.log`**。stdout 是協定通道，印出任何東西都會破壞 JSON-RPC 訊息。除錯訊息一律用 `console.error`，它會寫到 stderr。
4. **外部請求要設逾時**（`AbortSignal.timeout`），避免工具卡住不回應。

> **開發中踩到的坑**：測試時地理編碼 API 一直回傳 `fetch failed`，但同一個網址用 curl 和 Python 都正常。追查錯誤的 `cause` 發現是 `ETIMEDOUT`。Node 在 IPv4/IPv6 之間自動切換時，每次連線只等 **250ms**，而這台機器連到該 API 要約 330ms。解法是在 server 開頭加上 `net.setDefaultAutoSelectFamilyAttemptTimeout(2000)`。
>
> 教訓：`fetch failed` 是很籠統的訊息，要看 `error.cause` 才知道真正原因。

### Step 4：測試

**方法一：用程式測試**（`test-client.js`）

用 SDK 的 `Client` 以 stdio 啟動 server，走真正的 MCP 協定列出工具並呼叫：

```bash
cd lab2
npm test
```

測試案例涵蓋：城市名稱、指定國碼、經緯度、搜尋同名地點、找不到城市（預期 `isError`）、缺少參數（預期 `isError`）。

**方法二：用 MCP Inspector 互動測試**

```bash
cd lab2
npm run inspect      # 等同 npx @modelcontextprotocol/inspector node server.js
```

瀏覽器會開啟 Inspector 介面，可以手動填參數呼叫工具，並查看原始的 JSON-RPC 訊息。

### Step 5：註冊到 Claude Code

```bash
# 在專案根目錄執行；--scope project 會寫入 .mcp.json，可以 commit 給團隊共用
claude mcp add --scope project weather -- node lab2/server.js
```

產生的 `.mcp.json`：

```json
{
  "mcpServers": {
    "weather": {
      "type": "stdio",
      "command": "node",
      "args": ["lab2/server.js"],
      "env": {}
    }
  }
}
```

| scope | 存放位置 | 適用情境 |
|-------|---------|---------|
| `local`（預設） | `~/.claude.json`，只對本專案、只有自己 | 個人試用 |
| `project` | 專案根目錄的 `.mcp.json` | 團隊共用 |
| `user` | `~/.claude.json`，所有專案都能用 | 個人常用工具 |

> 如果 API 需要 key，用 `claude mcp add ... -e WEATHER_API_KEY=xxx` 透過環境變數傳入。`.mcp.json` 也支援 `${WEATHER_API_KEY}` 這種寫法，可以引用環境變數，避免把 key commit 進版本控制。

### Step 6：在 Claude Code 中使用

1. 先在 `lab2/` 裡執行 `npm install`，`node_modules` 不會 commit。
2. 在專案根目錄啟動 `claude`。第一次會詢問是否信任 `.mcp.json` 裡的 server，選擇核准。
3. 輸入 `/mcp` 確認 `weather` 狀態為 connected。
4. 直接問問題，例如：

| 提問 | 預期行為 |
|------|---------|
| 「台北今天天氣如何？」 | 呼叫 `get_weather`，city 傳 `Taipei` |
| 「高雄這週會下雨嗎？」 | 呼叫 `get_weather`，city 傳 `Kaohsiung`、country_code 傳 `TW`、days 傳 7 |
| 「Springfield 的天氣」 | 可能先呼叫 `search_locations`，或詢問是哪一個 Springfield |

MCP 工具在 Claude Code 中的完整名稱是 `mcp__weather__get_weather`，設定權限時會用到。

---

## 延伸練習

1. **Resource**：用 `server.registerResource` 提供 `weather://codes`，讓客戶端讀取 WMO 天氣代碼對照表。
2. **Prompt**：用 `server.registerPrompt` 提供 `trip_weather` 範本，輸入城市和日期，產生旅遊天氣建議。
3. **Structured output**：為 `get_weather` 加上 `outputSchema`，同時回傳 `structuredContent`（JSON），讓程式化的客戶端也能使用。
4. **Streamable HTTP**：把 transport 改成 `StreamableHTTPServerTransport`，部署成遠端 MCP server。
5. **結合 Lab 1**：保留這個 MCP server，把 Lab 1 的 SKILL.md 改成呼叫 `get_weather` 工具，而不是執行 Python 腳本。
