# Run-PromQLQuery.ps1

Drives [PromQL Playground](https://promql-playground.vercel.app/) headfully
through the **Chrome DevTools Protocol (CDP)** over a raw WebSocket — no
Selenium/Playwright/puppeteer involved, just `System.Net.WebSockets.ClientWebSocket`
from PowerShell. Works against either **Edge** (default) or **Chrome** — both
are Chromium-based and speak the same CDP.

Given a PromQL query, it:

1. Launches Chrome with `--remote-debugging-port` and opens the page.
2. Connects to the page's CDP WebSocket endpoint.
3. Injects the query into the page's Monaco editor instance.
4. Clicks the **Run** button.
5. Listens on the CDP `Network` domain for the resulting
   `/api/prometheus?...query_range` request and pulls its response body
   via `Network.getResponseBody`.
6. Saves the raw Prometheus-style JSON response to disk.

## Usage

```powershell
.\Run-PromQLQuery.ps1
```

Runs with the default query:

```
rate(demo_api_request_duration_seconds_count{status=~"5.."}[5m])
```

Custom query / output path:

```powershell
.\Run-PromQLQuery.ps1 -Query 'sum(rate(demo_api_request_duration_seconds_count[5m])) by (path)' -OutFile .\out.json
```

Use Chrome instead of Edge:

```powershell
.\Run-PromQLQuery.ps1 -Browser Chrome
```

### Parameters

| Parameter         | Default                                      | Description |
|-------------------|-----------------------------------------------|--------------|
| `-Url`            | `https://promql-playground.vercel.app/`       | Page to open |
| `-Query`          | `rate(demo_api_request_duration_seconds_count{status=~"5.."}[5m])` | PromQL query to run |
| `-OutFile`        | `.\query_response.json`                       | Where to save the captured response body |
| `-UrlMatch`       | auto (longest identifier token in `-Query`)   | Substring used to pick out the right network request |
| `-Browser`        | `Edge`                                        | `Edge` or `Chrome` |
| `-DebugPort`      | `9222`                                        | `--remote-debugging-port` |
| `-BrowserPath`    | auto-detected                                 | Path to `msedge.exe` / `chrome.exe` |
| `-TimeoutSeconds` | `15`                                          | How long to wait for the network response after clicking Run |

## Output

A Prometheus `query_range`-shaped JSON document:

```json
{
  "status": "success",
  "data": {
    "resultType": "matrix",
    "result": [
      { "metric": { "...": "..." }, "values": [[ts, "value"], ...] },
      ...
    ]
  }
}
```

## How it works

- **No webdriver.** Chrome exposes a per-tab WebSocket (`webSocketDebuggerUrl`,
  found via `http://localhost:<port>/json`). The script talks CDP's JSON
  protocol directly over that socket (`Runtime.evaluate`, `Network.enable`,
  `Network.getResponseBody`, etc).
- **Setting the query.** The playground's editor is Monaco (VS Code's editor
  component), which exposes a global `window.monaco`. The script calls
  `window.monaco.editor.getEditors()[0].setValue(...)` via `Runtime.evaluate`
  rather than trying to simulate keystrokes into the editor's hidden
  `textarea.ime-text-area` (which doesn't reflect real content — Monaco
  renders its own DOM). The query text is base64-encoded before being spliced
  into the JS expression to dodge quoting issues with PromQL characters like
  `"`, `{`, `}`, `~`, `[`, `]`.
- **Capturing the result.** Rather than scraping rendered chart/table DOM
  (fragile, lossy), the script enables the CDP `Network` domain *before*
  clicking Run, matches the `Network.responseReceived` event whose URL
  contains a distinctive token from the query (e.g. the metric name), waits
  for `Network.loadingFinished`, then fetches the exact bytes Chrome received
  via `Network.getResponseBody`.

## Known limitations

- Tied to this page's specific DOM: a button whose text is exactly `Run`,
  and a Monaco editor exposed on `window.monaco`. If the site changes its
  editor library or button label, the injection/click steps will need
  updating.
- Launches a **new** browser instance with a throwaway profile in
  `%TEMP%\cdp-profile-<Browser>-<port>` each run (not closed automatically
  afterward).
- The script waits (up to 20s) for `window.monaco` to appear before touching
  the editor, since the CDP page target shows up as soon as navigation
  starts — well before the app has actually mounted. If the page is
  unusually slow to load, bump `-TimeoutSeconds` / edit the 20s wait in the
  script.
- `-UrlMatch` auto-detection assumes the query contains a reasonably unique
  identifier (e.g. a metric name); pass `-UrlMatch` explicitly for very short
  or generic queries (e.g. `up`).
- Requires the browser's remote-debugging backend to actually be enabled.
  Enterprise policy (`DeveloperToolsAvailability` and its Edge/Chrome ADMX
  equivalents) can disable this entirely per-browser — this script will fail
  outright (connection refused / `Wait-ForPageTarget` timeout) if the chosen
  `-Browser` has that policy applied. Different browsers on the same machine
  can be configured differently by IT, so if one is locked down, try the
  other via `-Browser`.
