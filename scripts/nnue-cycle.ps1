<#
.SYNOPSIS
  One NNUE distillation cycle: generate -> (import Lichess games) -> train -> gate -> (promote).

.DESCRIPTION
  1. Generate: Omega self-play labelled at a fixed node budget by the -Teacher net
     -> nnue-data\<Name>-selfplay.txt
  2. Lichess (-Lichess): re-label the bot's real games with the same teacher
     -> nnue-data\lichess-tward-bot.txt
  3. Train on this cycle's self-play, the Lichess file if present, and -ExtraData (text files are
     packed to a .bin beside them first, once)
     -> nnue-data\<Name>.nnue
  4. Gate: SPRT of the candidate vs the bundled default.nnue on Omega
     -> nnue-data\<Name>-gate.log
  5. Promote (-Promote): only if the gate PASSED (or -Force). Keeps the old default as
     default-vN.nnue and installs the candidate as default.nnue.

  Every step can be skipped, so a cycle can be resumed or re-run with different training settings.
  Long-running: a full cycle is many hours. Steps print as they start, and the gate log is saved.

.EXAMPLE
  .\scripts\nnue-cycle.ps1 -Name gen3
  .\scripts\nnue-cycle.ps1 -Name gen3 -Lichess -ExtraData nnue-data\data-gen2-a.txt,nnue-data\data-gen2-b.txt
  .\scripts\nnue-cycle.ps1 -Name gen3b -SkipGenerate -SelfPlay nnue-data\gen3-selfplay.txt -Blend 0.1
  .\scripts\nnue-cycle.ps1 -Name gen3 -SkipGenerate -SkipTrain -SkipGate -Promote
#>
param(
    [Parameter(Mandatory = $true)][string]$Name,
    [int]$Games = 20000,
    [int]$Nodes = 20000,
    [string]$Teacher = 'default',
    [int]$Threads = 0,
    [switch]$Lichess,
    [int]$LichessMax = 2000,
    [string]$SelfPlay = '',
    [string[]]$ExtraData = @(),
    [int]$Epochs = 12,
    [int]$Hidden = 128,
    [double]$Blend = 0.3,
    [double]$LearningRate = 0.002,
    [int]$Batch = 1024,
    [switch]$SkipGenerate,
    [switch]$SkipTrain,
    [switch]$SkipGate,
    [switch]$Promote,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$dataDir = Join-Path $repoRoot 'nnue-data'
$resources = Join-Path $repoRoot 'engine\src\main\resources\nnue'
$inv = [System.Globalization.CultureInfo]::InvariantCulture
if ($Threads -le 0) { $Threads = [Math]::Max(1, [Environment]::ProcessorCount - 1) }
if ($Name -notmatch '^[A-Za-z0-9._-]+$') { throw "Name must be letters, digits, '.', '_' or '-'" }
New-Item -ItemType Directory -Force $dataDir | Out-Null

# Gradle runs the tools from engine\, so every path handed to them is made absolute. The tools
# split their arguments on spaces, so paths must not contain any.
function Resolve-RepoPath([string]$path) {
    if ([System.IO.Path]::IsPathRooted($path)) { $full = $path } else { $full = Join-Path $repoRoot $path }
    $full = [System.IO.Path]::GetFullPath($full)
    if ($full.Contains(' ')) { throw "Path contains a space, which the NNUE tools can't take: $full" }
    return $full
}

function Invoke-Gradle([string[]]$arguments) {
    # Windows PowerShell turns a native program's stderr into errors when output is redirected;
    # Gradle and the JDK write warnings there. Success is judged by the exit code instead.
    $ErrorActionPreference = 'Continue'
    Push-Location $repoRoot
    try {
        & .\gradlew.bat @arguments --console=plain
        if ($LASTEXITCODE -ne 0) { throw "gradle $($arguments[0]) failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

function Step([string]$text) {
    Write-Host ''
    Write-Host "==> [$((Get-Date).ToString('HH:mm'))] $text" -ForegroundColor Cyan
}

# A teacher is a bundled net name (default, default-v0) or a .nnue file.
if ($Teacher.EndsWith('.nnue') -or $Teacher.Contains('\') -or $Teacher.Contains('/')) {
    $teacherArg = Resolve-RepoPath $Teacher
} else {
    $teacherArg = $Teacher
}

if ($SelfPlay -ne '') { $selfPlayFile = Resolve-RepoPath $SelfPlay } else { $selfPlayFile = Resolve-RepoPath "nnue-data\$Name-selfplay.txt" }
$lichessFile = Resolve-RepoPath 'nnue-data\lichess-tward-bot.txt'
$lichessGames = Resolve-RepoPath 'nnue-data\lichess-tward-bot.ndjson'
$candidate = Resolve-RepoPath "nnue-data\$Name.nnue"
$gateLog = Resolve-RepoPath "nnue-data\$Name-gate.log"

# --- 1. Generate ---
if (-not $SkipGenerate) {
    Step "Generating $Games self-play games (omega, $Nodes nodes, teacher $Teacher, $Threads threads)"
    # A new cycle name gets new games; the same name reproduces them.
    $seed = 0
    foreach ($c in $Name.ToCharArray()) { $seed = ($seed * 31 + [int]$c) % 2147483647 }
    Invoke-Gradle @(':engine:generateNnueData',
        "-PnnueArgs=games=$Games bot=omega nodes=$Nodes net=$teacherArg threads=$Threads seed=$seed out=$selfPlayFile")
}

# --- 2. Lichess games ---
if ($Lichess) {
    Step "Re-labelling up to $LichessMax Lichess games with teacher $Teacher"
    Invoke-Gradle @(':engine:importLichessGames',
        "-PnnueArgs=max=$LichessMax refresh=true bot=omega nodes=$Nodes net=$teacherArg threads=$Threads games=$lichessGames out=$lichessFile")
}

# --- 3. Train ---
if (-not $SkipTrain) {
    $data = @($selfPlayFile)
    if (Test-Path $lichessFile) { $data += $lichessFile }
    foreach ($extra in $ExtraData) { $data += (Resolve-RepoPath $extra) }
    foreach ($file in $data) { if (-not (Test-Path $file)) { throw "Training data not found: $file" } }

    # Text files are packed once into a .bin beside them (skipped while the .bin is up to date),
    # so re-training on the same data - a blend sweep, a later cycle - skips the FEN parsing.
    $text = @($data | Where-Object { -not $_.EndsWith('.bin') })
    if ($text.Count -gt 0) {
        Step "Packing $($text.Count) text file(s) to binary"
        Invoke-Gradle @(':engine:packNnueData', "-PnnueArgs=data=$($text -join ',')")
        $data = @($data | ForEach-Object { if ($_.EndsWith('.bin')) { $_ } else { [System.IO.Path]::ChangeExtension($_, '.bin') } })
    }

    Step "Training $Name on $($data.Count) file(s): hidden=$Hidden epochs=$Epochs blend=$Blend batch=$Batch"
    $dataArg = $data -join ','
    $blendArg = $Blend.ToString($inv)
    $lrArg = $LearningRate.ToString($inv)
    Invoke-Gradle @(':engine:trainNnue',
        "-PnnueArgs=data=$dataArg out=$candidate hidden=$Hidden epochs=$Epochs blend=$blendArg lr=$lrArg batch=$Batch threads=$([Environment]::ProcessorCount)")
}

# --- 4. Gate ---
if (-not $SkipGate) {
    if (-not (Test-Path $candidate)) { throw "No candidate net at $candidate" }
    Step "Gating $Name vs default (SPRT on Omega; this takes hours). Log: $gateLog"
    $ErrorActionPreference = 'Continue'   # see Invoke-Gradle
    Push-Location $repoRoot
    try {
        & .\gradlew.bat :engine:longTest --tests 'engine.tournament.NnueGate' "-Pnnue.gate.candidate=$candidate" --rerun --console=plain |
            Tee-Object -FilePath $gateLog
        if ($LASTEXITCODE -ne 0) { throw "gate run failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
        $ErrorActionPreference = 'Stop'
    }
}

# --- 5. Promote ---
if ($Promote) {
    $passed = (Test-Path $gateLog) -and (Select-String -Path $gateLog -Pattern 'RESULT: PASSED' -Quiet)
    if (-not $passed -and -not $Force) {
        throw "Not promoting: $gateLog does not show 'RESULT: PASSED'. Use -Force to override."
    }
    $n = 0
    while (Test-Path (Join-Path $resources "default-v$n.nnue")) { $n++ }
    $keep = Join-Path $resources "default-v$n.nnue"
    Step "Promoting $Name : default.nnue -> default-v$n.nnue, $Name.nnue -> default.nnue"
    Copy-Item (Join-Path $resources 'default.nnue') $keep
    Copy-Item $candidate (Join-Path $resources 'default.nnue') -Force
    Write-Host '    Next: update the table in engine\src\main\resources\nnue\README.md,'
    Write-Host '    then ship it with .\deploy\deploy-engine.ps1'
}

Step "Cycle $Name done."
