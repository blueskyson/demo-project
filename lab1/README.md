# Lab 1：開發一個「天氣查詢」AI Skill

本 lab 以天氣查詢為例，示範如何為 Claude Code 開發一個可以用 `/weather` 呼叫、也會在使用者問天氣時自動觸發的 Agent Skill。

## 目錄結構

```
lab1/
├── README.md                 ← 本文件：開發方式說明
└── weather/                  ← skill 本體
    ├── SKILL.md              ← 給 Claude 看的說明（frontmatter + 操作步驟）
    └── scripts/
        └── get_weather.py    ← 實際查詢天氣的腳本（只用 Python 標準函式庫）
```

專案根目錄的 `.claude/skills/weather` 是指向 `lab1/weather` 的符號連結。Claude Code 只會從 `.claude/skills/`（專案層級）或 `~/.claude/skills/`（個人層級）載入 skill，所以要建這個連結才能使用。

### Skill 的常見目錄

除了 `scripts/`，Anthropic 的 Agent Skills 規格還定義了 `references/` 和 `assets/`，其餘是社群慣例：

| 目錄 | 用途 | Claude 怎麼用 | 天氣 skill 的例子 |
|------|------|--------------|------------------|
| `scripts/` | 可執行的程式 | **執行**，不必把程式碼讀進 context | `get_weather.py` |
| `references/` | 較長的參考文件，例如 API 說明、規格、代碼表 | 需要時才**讀取** | `references/weather_codes.md` 放 WMO 天氣代碼對照表 |
| `assets/` | 用在輸出裡的素材，例如範本、圖片、字型、樣板檔 | **複製或套用**到輸出，不需要理解內容 | `assets/report_template.html` 放天氣報告的 HTML 樣板 |
| `examples/`（慣例） | 輸入輸出範例 | 參考格式 | 好的與不好的回覆範例 |
| `evals/`（慣例） | 測試案例 | 驗證 skill 品質，skill-creator 會用到 | 「高雄明天會下雨嗎」→ 預期查 Kaohsiung |

三者的差別在於 context 的用法：`scripts` 只執行，`references` 讀進 context，`assets` 只複製不讀。

`references/` 和 `assets/` 都要在 SKILL.md 裡註明何時使用，否則 Claude 不知道它們存在，例如：

```markdown
遇到不認識的天氣代碼時，讀 `references/weather_codes.md`。
```

---

## 開發步驟

### Step 1：定義 skill 要做什麼

先寫出一句話的規格：

> 輸入城市名稱（和可選的天數），回傳目前天氣與未來數日預報，並附上生活建議。

再決定哪些工作交給**腳本**、哪些交給 **Claude**：

| 工作 | 負責者 | 原因 |
|------|--------|------|
| 呼叫 API、解析 JSON、轉換天氣代碼 | 腳本 | 固定流程，寫成程式最穩定，也不會編造數據 |
| 理解使用者問法（「明天要帶傘嗎？」） | Claude | 需要理解自然語言 |
| 中文地名轉英文、判斷地點是否正確 | Claude | 需要常識判斷 |
| 整理成口語回覆、給穿衣/帶傘建議 | Claude | 需要依情境調整 |

### Step 2：選資料來源

這裡用 [Open-Meteo](https://open-meteo.com/)，因為它**不需要 API key**，適合教學：

- 地理編碼：`https://geocoding-api.open-meteo.com/v1/search?name=Taipei`，城市名稱 → 經緯度
- 天氣預報：`https://api.open-meteo.com/v1/forecast?latitude=..&longitude=..`，經緯度 → 天氣

> 如果 API 需要 key，不要把 key 寫死在 SKILL.md 或腳本裡。改讓腳本從環境變數讀取（例如 `os.environ["WEATHER_API_KEY"]`）。

### Step 3：寫腳本 `scripts/get_weather.py`

設計重點：

1. **只用標準函式庫**（`urllib`、`json`、`argparse`），使用者不必另外 `pip install`。
2. **輸出給模型看的純文字**：格式簡潔、有標籤（「地點：」、「氣溫：」），Claude 容易讀懂。
3. **錯誤訊息要可行動**：例如「找不到城市，請改用英文名稱」，Claude 看到後會知道下一步該怎麼做。
4. **用 exit code 區分結果**：0 成功、1 找不到城市、2 網路錯誤。

先單獨測試腳本：

```bash
python3 lab1/weather/scripts/get_weather.py Taipei
python3 lab1/weather/scripts/get_weather.py Tokyo --days 5
python3 lab1/weather/scripts/get_weather.py Xyzqwvbn     # 測試錯誤處理
```

> **開發中踩到的坑**：測試時發現 Open-Meteo 的地理編碼不太認得中文地名。「台北」查不到，「高雄」會對到四川省的高雄。解法分成兩層：
> - 腳本：同名結果取人口最多者（例如 `Springfield` → 密蘇里州）。
> - SKILL.md：指示 Claude **先把地名翻成英文**，並檢查輸出地點的國家是否正確。
>
> 這就是「腳本負責確定性工作、Claude 負責判斷」分工的實例。

### Step 4：寫 `SKILL.md`

```yaml
---
name: weather
description: 查詢任一城市的目前天氣與未來數日天氣預報……當使用者詢問天氣、氣溫、會不會下雨、要不要帶傘、出門穿什麼，或輸入 /weather 時使用。
argument-hint: <城市名稱> [天數]
allowed-tools: Bash(python3 *)
---
```

| 欄位 | 用途 |
|------|------|
| `name` | skill 名稱，也是斜線指令名稱（`/weather`） |
| `description` | **最重要**。Claude 平時只看得到這行，靠它決定要不要自動啟用。要寫「做什麼」以及「什麼時候用」，並列出使用者可能的說法 |
| `argument-hint` | 輸入 `/weather` 時顯示的參數提示 |
| `allowed-tools` | 這個 skill 執行時免確認的工具，這裡只開放執行 python3 |

本文部分的寫法：

- 用**編號步驟**描述流程，比寫一段散文更容易被照著執行。
- `$ARGUMENTS` 會被替換成使用者在 `/weather` 後面輸入的文字。
- 明確寫出**錯誤處理**和**禁止事項**，例如「不要編造天氣資料」。
- 附一個**輸入 → 執行 → 回覆**的範例，讓輸出格式穩定。

### Step 5：註冊 skill

```bash
mkdir -p .claude/skills
ln -s ../../lab1/weather .claude/skills/weather
```

想讓所有專案都能用的話，改放到 `~/.claude/skills/weather/`。

### Step 6：測試與迭代

重新啟動 Claude Code（或開新對話）後測試：

| 測試 | 預期 |
|------|------|
| `/weather Taipei` | 手動呼叫，顯示台北 3 天預報 |
| `/weather 東京 7` | 地名轉成 `Tokyo`，顯示 7 天預報 |
| 「高雄明天會下雨嗎？」 | **自動觸發**，查詢 `Kaohsiung` 並回答降雨機率 |
| 「這週末去台中要帶外套嗎？」 | 自動觸發，給出穿衣建議 |
| 「幫我寫一個 Python 排序函式」 | **不應**觸發 |
| `/weather` 不帶參數 | 先詢問要查哪個城市 |

如果沒有自動觸發，回頭修改 `description`，補上更多使用者的實際說法。如果回覆格式不穩定，就在 SKILL.md 補充或修正範例。

---

## 延伸練習

1. 加上 `--units imperial` 參數，支援華氏溫度。
2. 加入空氣品質查詢（Open-Meteo 提供 `air-quality-api.open-meteo.com`）。
3. 把 WMO 天氣代碼表移到 `references/weather_codes.md`，練習漸進式揭露（progressive disclosure）。
4. 使用 `skill-creator` skill 為這個 skill 撰寫評測（evals）。
