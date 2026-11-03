<#
.SYNOPSIS
  Builds the UCI engine jar and deploys it to the chessbot VM with no downtime.

.DESCRIPTION
  1. Runs the test suite (unless -SkipTests) and builds desktop/build/libs/uci-engine.jar.
  2. Picks the first reachable host from VM_HOSTS (LAN, then Tailscale).
  3. Uploads the jar plus remote-install-engine.sh, which verifies the checksum, checks the jar
     answers the UCI handshake on the VM, backs up the live jar and swaps the new one in with an
     atomic rename. lichess-bot starts a new engine per game, so nothing is restarted: a game in
     progress finishes on the old engine, the next game uses the new one.

  Private settings live in deploy/deploy.env (copy deploy.env.example).

.EXAMPLE
  .\deploy\deploy-engine.ps1
  .\deploy\deploy-engine.ps1 -SkipTests
  .\deploy\deploy-engine.ps1 -SkipBuild      # redeploy the jar already built
  .\deploy\deploy-engine.ps1 -Rollback       # restore the most recent backup on the VM
#>
param(
    [switch]$SkipTests,
    [switch]$SkipBuild,
    [switch]$Rollback,
    [string]$EnvFile = (Join-Path $PSScriptRoot 'deploy.env')
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent

function Read-EnvFile([string]$path) {
    if (-not (Test-Path $path)) {
        throw "Missing $path. Copy deploy.env.example to deploy.env and fill it in."
    }
    $settings = @{}
    foreach ($line in Get-Content $path) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
        $eq = $trimmed.IndexOf('=')
        if ($eq -lt 1) { continue }
        $value = $trimmed.Substring($eq + 1).Trim().Trim('"').Trim("'")
        $settings[$trimmed.Substring(0, $eq).Trim()] = $value
    }
    return $settings
}

function Get-Setting($settings, [string]$name, $default) {
    if ($settings.ContainsKey($name) -and $settings[$name] -ne '') { return $settings[$name] }
    if ($null -ne $default) { return $default }
    throw "$name is not set in $EnvFile"
}

function Test-Port([string]$hostName, [int]$port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $connect = $client.BeginConnect($hostName, $port, $null, $null)
        if (-not $connect.AsyncWaitHandle.WaitOne(2000)) { return $false }
        $client.EndConnect($connect)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Invoke-Native([string]$exe, [string[]]$arguments) {
    # Windows PowerShell turns a native program's stderr into errors when output is redirected;
    # Gradle, the JDK and ssh write warnings there. Success is judged by the exit code instead.
    $ErrorActionPreference = 'Continue'
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "$exe failed with exit code $LASTEXITCODE" }
}

$settings = Read-EnvFile $EnvFile
$vmUser = Get-Setting $settings 'VM_USER' $null
$hosts = (Get-Setting $settings 'VM_HOSTS' $null).Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ }
$sshPort = [int](Get-Setting $settings 'SSH_PORT' '22')
$sshKey = Get-Setting $settings 'SSH_KEY' ''
$engineDir = Get-Setting $settings 'ENGINE_DIR' '/opt/chessbot/lichess-bot/engines'
$engineName = Get-Setting $settings 'ENGINE_NAME' 'uci-engine.jar'
$keepBackups = Get-Setting $settings 'KEEP_BACKUPS' '5'

$jar = Join-Path $repoRoot 'desktop\build\libs\uci-engine.jar'
$remoteScript = Join-Path $PSScriptRoot 'remote-install-engine.sh'

# --- Build ---
if (-not $Rollback -and -not $SkipBuild) {
    Push-Location $repoRoot
    try {
        if ($SkipTests) {
            Write-Host '==> Building uci-engine.jar (tests skipped)' -ForegroundColor Cyan
            Invoke-Native '.\gradlew.bat' @(':desktop:uciJar', '--console=plain')
        } else {
            Write-Host '==> Running tests and building uci-engine.jar' -ForegroundColor Cyan
            Invoke-Native '.\gradlew.bat' @('test', ':desktop:uciJar', '--console=plain')
        }
    } finally {
        Pop-Location
    }
}
if (-not $Rollback -and -not (Test-Path $jar)) { throw "No jar at $jar - build it first." }

# --- Pick a reachable host ---
$target = $null
foreach ($h in $hosts) {
    Write-Host "==> Trying ${h}:$sshPort" -ForegroundColor Cyan
    if (Test-Port $h $sshPort) { $target = $h; break }
}
if (-not $target) { throw "None of $($hosts -join ', ') is reachable on port $sshPort." }
Write-Host "    Using $target"

$sshOpts = @('-p', "$sshPort")
$scpOpts = @('-P', "$sshPort")
if ($sshKey -ne '') {
    $sshOpts += @('-i', $sshKey)
    $scpOpts += @('-i', $sshKey)
}
$login = "$vmUser@$target"
$remoteTmp = "/tmp/chess-engine-deploy-$PID"

# Each scp/ssh call is a separate login (one password prompt each with password auth), so the
# deploy uses exactly two: one upload and one remote command that does everything else.
# The script is CR-stripped on the VM in case it was saved with Windows line endings. -t gives
# sudo a terminal to prompt on, should the engine dir not be writable by VM_USER.
function Invoke-Remote([string]$scriptArgs, [string]$extraCleanup, [string]$prepare = '') {
    $command = "$prepare" + "tr -d '\r' < $remoteTmp.sh > $remoteTmp.run && bash $remoteTmp.run $scriptArgs; " +
               "s=`$?; rm -f $remoteTmp.sh $remoteTmp.run $extraCleanup; exit `$s"
    Invoke-Native 'ssh' ($sshOpts + @('-t', $login, $command))
}

if ($Rollback) {
    Write-Host '==> Rolling back to the most recent backup' -ForegroundColor Cyan
    Invoke-Native 'scp' ($scpOpts + @($remoteScript, "${login}:$remoteTmp.sh"))
    Invoke-Remote "rollback '$engineDir' '$engineName'" ''
    Write-Host '==> Rolled back.' -ForegroundColor Green
    return
}

$sha = (Get-FileHash $jar -Algorithm SHA256).Hash.ToLower()
$sizeMb = [math]::Round((Get-Item $jar).Length / 1MB, 1)
Write-Host "==> Uploading uci-engine.jar ($sizeMb MB, sha256 $sha)" -ForegroundColor Cyan
Invoke-Native 'scp' ($scpOpts + @($jar, $remoteScript, "${login}:/tmp/"))

Write-Host '==> Installing on the VM' -ForegroundColor Cyan
# The uploads are moved to per-deploy names first, in the same login as the install.
$prepare = "mv /tmp/uci-engine.jar $remoteTmp.jar && mv /tmp/remote-install-engine.sh $remoteTmp.sh && "
Invoke-Remote "install $remoteTmp.jar $sha '$engineDir' '$engineName' $keepBackups" "$remoteTmp.jar" $prepare

Write-Host '==> Deployed. Watch the next game pick it up with:' -ForegroundColor Green
Write-Host "    ssh $login journalctl -fu lichess-bot"
