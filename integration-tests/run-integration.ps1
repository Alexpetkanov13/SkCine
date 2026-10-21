<#
.SYNOPSIS
  Provisions a real Paper + Skript server and runs SkCine's integration suites against it.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File integration-tests\run-integration.ps1
  powershell -ExecutionPolicy Bypass -File integration-tests\run-integration.ps1 -Paper 26.1.2 -Skript 2.17.0 -Java "C:\Program Files\Java\jdk-25\bin\java.exe" -ClientVersion 26.1

.NOTES
  Requires: JDK 21 (or 25 for Paper 26.x), Python 3, Node.js 18+. Network access for the first run.
  Downloads go to integration-tests/work/<paper>-<skript>/ (git-ignored). The server runs offline-mode on
  localhost only, with RCON bound to the configured port.
#>
param(
    [string]$Paper = "1.21.11",
    [string]$Skript = "2.16.2",
    [string]$Java = "java",
    [string]$ClientVersion = "",
    [int]$Port = 25599,
    [int]$RconPort = 25598,
    [switch]$SkipBuild,
    [switch]$WithPacketEvents,
    [switch]$Crash
)

$ErrorActionPreference = "Stop"
# Invoke-WebRequest is very slow in Windows PowerShell 5.1 when it renders a progress bar.
$ProgressPreference = "SilentlyContinue"
$root = Split-Path -Parent $PSScriptRoot
$here = $PSScriptRoot
$work = Join-Path $here "work\$Paper-$Skript"
$rconPass = "skcinetest"
if ($ClientVersion -eq "") { $ClientVersion = $Paper }
$results = [ordered]@{}

function Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }

if (-not $SkipBuild) {
    Step "Building SkCine"
    Push-Location $root
    & .\gradlew.bat shadowJar --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }
    Pop-Location
}
$jar = Join-Path $root "build\libs\SkCine-1.0.0.jar"
if (-not (Test-Path $jar)) { throw "Missing $jar" }

Step "Preparing server in $work"
New-Item -ItemType Directory -Force (Join-Path $work "plugins\Skript\scripts") | Out-Null
$paperJar = Join-Path $work "paper.jar"
if (-not (Test-Path $paperJar)) {
    $build = Invoke-RestMethod "https://fill.papermc.io/v3/projects/paper/versions/$Paper/builds/latest"
    $dl = $build.downloads.'server:default'
    Write-Host "Paper $Paper build $($build.id)"
    Invoke-WebRequest $dl.url -OutFile $paperJar
    $hash = (Get-FileHash $paperJar -Algorithm SHA256).Hash.ToLower()
    if ($hash -ne $dl.checksums.sha256) { Remove-Item $paperJar; throw "Paper checksum mismatch" }
}
$skriptJar = Join-Path $work "plugins\Skript-$Skript.jar"
if (-not (Test-Path $skriptJar)) {
    Invoke-WebRequest "https://github.com/SkriptLang/Skript/releases/download/$Skript/Skript-$Skript.jar" -OutFile $skriptJar
}
if ($WithPacketEvents) {
    $pe = Join-Path $work "plugins\packetevents-spigot-2.14.0.jar"
    if (-not (Test-Path $pe)) { Invoke-WebRequest "https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar" -OutFile $pe }
}
Copy-Item $jar (Join-Path $work "plugins\SkCine-1.0.0.jar") -Force
Set-Content -Path (Join-Path $work "eula.txt") -Value "eula=true" -Encoding ascii
@"
online-mode=false
server-ip=127.0.0.1
server-port=$Port
enable-rcon=true
rcon.port=$RconPort
rcon.password=$rconPass
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
view-distance=6
simulation-distance=4
max-players=80
spawn-monsters=false
difficulty=peaceful
"@ | Set-Content -Path (Join-Path $work "server.properties") -Encoding ascii
if (-not (Test-Path (Join-Path $work "bukkit.yml"))) {
    "settings:`n  connection-throttle: -1" | Set-Content -Path (Join-Path $work "bukkit.yml") -Encoding ascii
}
$scripts = Join-Path $work "plugins\Skript\scripts"
Copy-Item (Join-Path $here "scripts\skcine-syntax-test.sk") $scripts -Force
Copy-Item (Join-Path $here "scripts\skcine-bench.sk") $scripts -Force
Copy-Item (Join-Path $root "examples\basic\first-cinematic.sk") $scripts -Force
New-Item -ItemType Directory -Force (Join-Path $scripts "examples") | Out-Null
Get-ChildItem (Join-Path $root "examples") -Recurse -Filter *.sk | Where-Object { $_.Name -ne "first-cinematic.sk" } | ForEach-Object { Copy-Item $_.FullName (Join-Path $scripts "examples") -Force }

Step "Installing test client"
Push-Location (Join-Path $here "bot")
if (-not (Test-Path "node_modules\mineflayer")) { & npm install --no-audit --no-fund | Out-Null }
Pop-Location

Step "Starting Paper $Paper with Skript $Skript"
$log = Join-Path $work "server.log"
if (Test-Path $log) { Remove-Item $log }
$proc = Start-Process -FilePath $Java -ArgumentList "-Xms1G", "-Xmx2G", "-jar", "paper.jar", "--nogui" -WorkingDirectory $work -RedirectStandardOutput $log -RedirectStandardError (Join-Path $work "server.err") -PassThru -WindowStyle Hidden
$up = $false
for ($i = 0; $i -lt 300; $i++) {
    Start-Sleep -Seconds 1
    if ((Test-Path $log) -and (Select-String -Path $log -Pattern "Done \(" -Quiet)) { $up = $true; break }
    if ($proc.HasExited) { break }
}
if (-not $up) { throw "Server did not start; see $log" }
$text = Get-Content $log -Raw
$results["plugin enabled"] = $text -match "SkCine 1\.0\.0 by DHeaven enabled"
$results["all scripts loaded without errors"] = $text -match "All scripts loaded without errors"
$results["load assertions"] = (([regex]::Matches($text, "SKCINE-TEST PASS")).Count -eq 24) -and ($text -notmatch "SKCINE-TEST FAIL")

function Run($name, $exe, $arguments) {
    Step $name
    & $exe @arguments
    $results[$name] = ($LASTEXITCODE -eq 0)
}
$env:MC_VERSION = $ClientVersion
Run "parse/diagnostics/reload tests" "python" @((Join-Path $here "run_parse_tests.py"), $work, "$RconPort", $rconPass)
Run "in-game suite" "node" @((Join-Path $here "bot\skcine-bot-test.mjs"), $log, "127.0.0.1", "$Port", "$RconPort", $rconPass)
Run "examples smoke test" "node" @((Join-Path $here "bot\skcine-examples-test.mjs"), $log, "127.0.0.1", "$Port", "$RconPort", $rconPass)
Run "player actors (Mannequin, 1.21.9+; clear error on older servers)" "node" @((Join-Path $here "bot\skcine-player-actor-test.mjs"), $log)

if ($Crash) {
    Step "Crash recovery"
    Push-Location (Join-Path $here "bot")
    Remove-Item crash-ready, crash-start.json -ErrorAction SilentlyContinue
    $before = Start-Process node -ArgumentList "skcine-crash-test.mjs", "before", "127.0.0.1", "$Port" -PassThru -WindowStyle Hidden
    for ($i = 0; $i -lt 40 -and -not (Test-Path crash-ready); $i++) { Start-Sleep 1 }
    & python (Join-Path $here "rcon.py") 127.0.0.1 $RconPort $rconPass "save-all flush" | Out-Null
    Stop-Process -Id $proc.Id -Force
    Start-Sleep 3
    # Keep the first server log as evidence; the restarted server logs separately.
    $log2 = Join-Path $work "server-after-crash.log"
    if (Test-Path $log2) { Remove-Item $log2 }
    $proc = Start-Process -FilePath $Java -ArgumentList "-Xms1G", "-Xmx2G", "-jar", "paper.jar", "--nogui" -WorkingDirectory $work -RedirectStandardOutput $log2 -RedirectStandardError (Join-Path $work "server-after-crash.err") -PassThru -WindowStyle Hidden
    for ($i = 0; $i -lt 300; $i++) { Start-Sleep 1; if ((Test-Path $log2) -and (Select-String -Path $log2 -Pattern "Done \(" -Quiet)) { break } }
    & node skcine-crash-test.mjs after 127.0.0.1 $Port
    $results["crash recovery"] = ($LASTEXITCODE -eq 0)
    if (-not $before.HasExited) { Stop-Process -Id $before.Id -Force -ErrorAction SilentlyContinue }
    Pop-Location
}

Step "Stopping server"
& python (Join-Path $here "rcon.py") 127.0.0.1 $RconPort $rconPass "stop" | Out-Null
Start-Sleep 5
if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }

Step "Summary: Paper $Paper, Skript $Skript, client $ClientVersion"
$failed = 0
foreach ($k in $results.Keys) {
    if ($results[$k]) { Write-Host "PASS $k" -ForegroundColor Green } else { Write-Host "FAIL $k" -ForegroundColor Red; $failed++ }
}
exit $failed
