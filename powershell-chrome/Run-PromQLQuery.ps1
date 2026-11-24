<#
.SYNOPSIS
    Drives https://promql-playground.vercel.app/ (or any site using the same
    Monaco-based query editor) via the Chrome DevTools Protocol over a raw
    WebSocket connection: launches a Chromium-based browser (Chrome or Edge)
    with remote debugging enabled, injects a PromQL query into the Monaco
    editor, clicks "Run", captures the resulting /api/prometheus network
    response via the CDP Network domain, and saves the response body to disk.

.PARAMETER Url
    Page to open.

.PARAMETER Query
    PromQL query to inject into the editor and execute.

.PARAMETER OutFile
    Where to save the captured JSON response body.

.PARAMETER UrlMatch
    Substring used to identify which network request is "the" query_range
    request to capture (in case several fire around the same time). Defaults
    to the longest identifier-like token found in -Query (e.g. a metric name).

.PARAMETER Browser
    Which Chromium-based browser to drive: "Edge" or "Chrome".

.PARAMETER DebugPort
    Remote-debugging port.

.PARAMETER BrowserPath
    Path to msedge.exe / chrome.exe. Auto-detected from common install
    locations if omitted.

.PARAMETER TimeoutSeconds
    How long to wait for the target network response after clicking Run.

.EXAMPLE
    .\Run-PromQLQuery.ps1 -Query 'rate(demo_api_request_duration_seconds_count{status=~"5.."}[5m])'

.EXAMPLE
    .\Run-PromQLQuery.ps1 -Browser Chrome
#>
param(
    [string]$Url = "https://promql-playground.vercel.app/",
    [string]$Query = 'rate(demo_api_request_duration_seconds_count{status=~"5.."}[5m])',
    [string]$OutFile = "$PSScriptRoot\query_response.json",
    [string]$UrlMatch,
    [ValidateSet("Edge", "Chrome")][string]$Browser = "Edge",
    [int]$DebugPort = 9222,
    [string]$BrowserPath,
    [int]$TimeoutSeconds = 15
)

$ErrorActionPreference = "Stop"
# System.Net.WebSockets.ClientWebSocket ships in the assemblies PowerShell
# Desktop loads by default, so no Add-Type is needed to use it here.

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

function Get-BrowserPath {
    param([string]$Browser, [string]$Override)
    if ($Override) { return $Override }
    $candidates = switch ($Browser) {
        "Edge" {
            @(
                "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
                "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
                "$env:LocalAppData\Microsoft\Edge\Application\msedge.exe"
            )
        }
        "Chrome" {
            @(
                "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
                "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
                "$env:LocalAppData\Google\Chrome\Application\chrome.exe"
            )
        }
    }
    $found = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $found) { throw "Could not find $Browser's executable. Pass -BrowserPath explicitly." }
    return $found
}

function Start-BrowserWithDebugging {
    param([string]$BrowserExe, [string]$Browser, [int]$Port, [string]$TargetUrl)
    $udd = Join-Path $env:TEMP "cdp-profile-$Browser-$Port"
    if (-not (Test-Path $udd)) { New-Item -ItemType Directory -Path $udd | Out-Null }
    Start-Process -FilePath $BrowserExe -ArgumentList @(
        "--remote-debugging-port=$Port",
        "--user-data-dir=$udd",
        "--no-first-run",
        "--no-default-browser-check",
        $TargetUrl
    ) | Out-Null
}

function Wait-ForPageTarget {
    param([int]$Port, [string]$UrlPrefix, [int]$TimeoutSec = 20)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $targets = Invoke-RestMethod -Uri "http://localhost:$Port/json"
            $page = $targets | Where-Object { $_.type -eq "page" -and $_.url -like "$UrlPrefix*" } | Select-Object -First 1
            if ($page) { return $page }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for page target '$UrlPrefix' on debug port $Port."
}

function Connect-Cdp {
    param([string]$WsUrl)
    $ws = New-Object System.Net.WebSockets.ClientWebSocket
    $ws.ConnectAsync((New-Object System.Uri($WsUrl)), [System.Threading.CancellationToken]::None).GetAwaiter().GetResult() | Out-Null
    return $ws
}

function Send-CdpMessage {
    param($Ws, [hashtable]$Message)
    $json = $Message | ConvertTo-Json -Depth 10 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $seg = New-Object System.ArraySegment[byte] (,$bytes)
    $Ws.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [System.Threading.CancellationToken]::None).GetAwaiter().GetResult() | Out-Null
}

function Receive-CdpMessage {
    param($Ws, [int]$TimeoutMs = 3000)
    $buffer = New-Object byte[] 65536
    $out = ""
    $cts = New-Object System.Threading.CancellationTokenSource
    $cts.CancelAfter($TimeoutMs)
    try {
        do {
            $seg = New-Object System.ArraySegment[byte] (,$buffer)
            $r = $Ws.ReceiveAsync($seg, $cts.Token).GetAwaiter().GetResult()
            $out += [System.Text.Encoding]::UTF8.GetString($buffer, 0, $r.Count)
        } while (-not $r.EndOfMessage)
        return $out
    } catch {
        return $null
    }
}

function Invoke-CdpEval {
    # One-shot Runtime.evaluate: send, read until the matching id comes back.
    param($Ws, [string]$Expression, [int]$Id = 1)
    Send-CdpMessage $Ws @{ id = $Id; method = "Runtime.evaluate"; params = @{ expression = $Expression; returnByValue = $true } }
    $deadline = (Get-Date).AddSeconds(5)
    while ((Get-Date) -lt $deadline) {
        $msg = Receive-CdpMessage $Ws 2000
        if (-not $msg) { continue }
        try { $obj = $msg | ConvertFrom-Json } catch { continue }
        if ($obj.id -eq $Id) { return $obj }
    }
    return $null
}

function ConvertTo-Base64Utf8 {
    param([string]$Text)
    [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($Text))
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

if (-not $UrlMatch) {
    $m = [regex]::Match($Query, '[a-zA-Z_][a-zA-Z0-9_]{4,}')
    $UrlMatch = if ($m.Success) { $m.Value } else { "query_range" }
}

$browserExe = Get-BrowserPath -Browser $Browser -Override $BrowserPath
Write-Host "Launching $Browser with --remote-debugging-port=$DebugPort ..."
Start-BrowserWithDebugging -BrowserExe $browserExe -Browser $Browser -Port $DebugPort -TargetUrl $Url

Write-Host "Waiting for page target matching '$Url' ..."
$target = Wait-ForPageTarget -Port $DebugPort -UrlPrefix $Url
Write-Host "Found target: $($target.title) [$($target.id)]"

$ws = Connect-Cdp -WsUrl $target.webSocketDebuggerUrl

try {
    # Capture all future network traffic so we can pull the response body later.
    Send-CdpMessage $ws @{ id = 1; method = "Network.enable" }
    Receive-CdpMessage $ws 3000 | Out-Null

    # Wait for the page's JS (React + Monaco) to finish mounting before poking
    # at window.monaco -- the CDP target shows up as soon as navigation starts,
    # well before the app is actually ready.
    $readyDeadline = (Get-Date).AddSeconds(20)
    $ready = $false
    $readyId = 100
    while ((Get-Date) -lt $readyDeadline -and -not $ready) {
        $readyId++
        $r = Invoke-CdpEval -Ws $ws -Expression "!!(window.monaco && window.monaco.editor.getEditors().length > 0)" -Id $readyId
        if ($r -and $r.result.result.value -eq $true) { $ready = $true } else { Start-Sleep -Milliseconds 500 }
    }
    if (-not $ready) { throw "Monaco editor never became available on window.monaco within 20s." }

    # Inject the query into the Monaco editor instance exposed on window.monaco.
    # Text is base64-encoded first to dodge JS/JSON quoting issues with PromQL
    # characters like " { } [ ] ~.
    $b64 = ConvertTo-Base64Utf8 $Query
    $setExpr = "(function(){var ed=window.monaco.editor.getEditors()[0]; var q=decodeURIComponent(escape(window.atob('$b64'))); ed.setValue(q); ed.focus(); return ed.getValue();})()"
    $setResult = Invoke-CdpEval -Ws $ws -Expression $setExpr -Id 2
    $editorValue = $setResult.result.result.value
    Write-Host "Editor set to: $editorValue"

    # Click the "Run" button to execute the query.
    $clickExpr = "(function(){var btns=Array.from(document.querySelectorAll('button')); var run=btns.find(b=>b.textContent.trim()==='Run'); if(!run) return 'NOT_FOUND'; run.click(); return 'clicked';})()"
    Send-CdpMessage $ws @{ id = 3; method = "Runtime.evaluate"; params = @{ expression = $clickExpr } }
    Receive-CdpMessage $ws 3000 | Out-Null

    # Watch Network domain events for the query_range request whose URL
    # contains our match token, then wait for it to finish loading.
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $requestId = $null
    $finished = $false

    while ((Get-Date) -lt $deadline -and -not $finished) {
        $msg = Receive-CdpMessage $ws 2000
        if (-not $msg) { continue }
        try { $obj = $msg | ConvertFrom-Json } catch { continue }

        if ($obj.method -eq "Network.responseReceived" -and $obj.params.response.url -like "*$UrlMatch*") {
            $requestId = $obj.params.requestId
        }
        if ($obj.method -eq "Network.loadingFinished" -and $requestId -and $obj.params.requestId -eq $requestId) {
            $finished = $true
        }
    }

    if (-not $requestId) {
        throw "No network request matching '$UrlMatch' was observed within $TimeoutSeconds s."
    }

    Send-CdpMessage $ws @{ id = 10; method = "Network.getResponseBody"; params = @{ requestId = $requestId } }
    $bodyMsg = $null
    $deadline2 = (Get-Date).AddSeconds(5)
    while ((Get-Date) -lt $deadline2) {
        $m = Receive-CdpMessage $ws 2000
        if (-not $m) { continue }
        try { $o = $m | ConvertFrom-Json } catch { continue }
        if ($o.id -eq 10) { $bodyMsg = $o; break }
    }

    if (-not $bodyMsg -or -not $bodyMsg.result) {
        throw "Could not retrieve response body for requestId=$requestId."
    }

    $content = $bodyMsg.result.body
    if ($bodyMsg.result.base64Encoded) {
        [System.IO.File]::WriteAllBytes($OutFile, [Convert]::FromBase64String($content))
    } else {
        [System.IO.File]::WriteAllText($OutFile, $content, [System.Text.Encoding]::UTF8)
    }

    Write-Host "Saved response body to $OutFile (requestId=$requestId)"
}
finally {
    $ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "done", [System.Threading.CancellationToken]::None).GetAwaiter().GetResult() | Out-Null
}
