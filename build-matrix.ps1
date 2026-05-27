param(
  [string[]]$OnlyVersions = @()
)

$ErrorActionPreference = "Stop"

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackupRoot = Join-Path $ProjectDir "..\\minecraft-money-mod-backups\\matrix-1.4.0-r4"
New-Item -ItemType Directory -Force -Path $BackupRoot | Out-Null

$Versions = @(
  "1.21.1",
  "1.21.6",
  "1.21.7",
  "1.21.8",
  "1.21.9",
  "1.21.10",
  "1.21.11"
)

if ($OnlyVersions -and $OnlyVersions.Count -gt 0) {
  $flat = @()
  foreach ($v in $OnlyVersions) {
    $flat += ($v -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
  }
  if ($flat.Count -gt 0) { $Versions = $flat }
}

function Get-LatestMatchingVersion([string]$MetadataUrl, [string]$MatchSuffix) {
  $xml = [xml](Invoke-WebRequest -UseBasicParsing -Uri $MetadataUrl -TimeoutSec 30).Content
  $all = @($xml.metadata.versioning.versions.version | ForEach-Object { [string]$_ })
  $matches = $all | Where-Object { $_ -like "*$MatchSuffix" }
  if (-not $matches -or $matches.Count -eq 0) { return $null }
  $matches | Sort-Object {
    $base = ($_ -split '\+')[0]
    $base = ($base -replace '[^0-9\.].*', '')
    if ([string]::IsNullOrWhiteSpace($base)) { $base = '0.0' }
    [version]$base
  } -Descending | Select-Object -First 1
}

$FabricMeta = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"
$ModMenuMeta = "https://maven.terraformersmc.com/releases/com/terraformersmc/modmenu/maven-metadata.xml"

$Manifest = @()

foreach ($mc in $Versions) {
  $suffix = "+$mc"
  $fabricApi = Get-LatestMatchingVersion -MetadataUrl $FabricMeta -MatchSuffix $suffix
  if (-not $fabricApi) {
    $Manifest += [pscustomobject]@{
      minecraft = $mc
      fabricApi = $null
      modMenu = "13.0.2"
      loader = $null
      jar = $null
      status = "skipped"
      reason = "No fabric-api matching *$suffix"
    }
    continue
  }

  $modMenu = "13.0.2"

  $loader = "0.18.4"
  if ([version]$mc -lt [version]"1.21.4") { $loader = "0.16.9" }

  Push-Location $ProjectDir
  try {
    & .\gradlew.bat --no-daemon build "-Pminecraft_version=$mc" "-Pfabric_api_version=$fabricApi" "-Pmodmenu_version=$modMenu" "-Pfabric_loader_version=$loader" "-Pmod_version=1.4.0"
  } finally {
    Pop-Location
  }

  try {
    $builtJar = Join-Path $ProjectDir "build\\libs\\minecraft-money-mod-1.4.0.jar"
    if (-not (Test-Path $builtJar)) {
      throw "Build succeeded but jar missing"
    }

    $outJar = Join-Path $BackupRoot ("minecraft-money-mod-1.4.0-mc$mc.jar")
    Copy-Item -Force $builtJar $outJar

    $Manifest += [pscustomobject]@{
      minecraft = $mc
      fabricApi = $fabricApi
      modMenu = $modMenu
      loader = $loader
      jar = (Split-Path -Leaf $outJar)
      status = "ok"
      reason = $null
    }
  } catch {
    $Manifest += [pscustomobject]@{
      minecraft = $mc
      fabricApi = $fabricApi
      modMenu = $modMenu
      loader = $loader
      jar = $null
      status = "failed"
      reason = $_.Exception.Message
    }
  }
}

$Manifest | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -Path (Join-Path $BackupRoot "manifest.json")
