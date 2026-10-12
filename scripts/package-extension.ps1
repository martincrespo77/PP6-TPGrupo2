# Empaqueta browser-extension/ en build/extension/acortador-pp6-<version>.zip (contexto.md 11.2).
# Por defecto la extension llama al servidor de config.js. Con -ApiUrl se genera un zip para
# otro servidor sin tocar el repositorio; la direccion tiene que estar en host_permissions.
#
# Uso:  powershell -ExecutionPolicy Bypass -File scripts\package-extension.ps1 [-ApiUrl http://localhost:8080]

param([string]$ApiUrl = '')

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression

$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root 'browser-extension'
$enc = New-Object System.Text.UTF8Encoding($false)

$manifest = [IO.File]::ReadAllText((Join-Path $source 'manifest.json'), $enc) | ConvertFrom-Json
$config = [IO.File]::ReadAllText((Join-Path $source 'config.js'), $enc)
$suffix = ''

if ($ApiUrl) {
  $ApiUrl = $ApiUrl.TrimEnd('/')
  if ($manifest.host_permissions -notcontains "$ApiUrl/*") {
    throw "$ApiUrl/* no esta en host_permissions de manifest.json"
  }
  $config = [regex]::Replace($config, "apiBaseUrl:\s*'[^']+'", "apiBaseUrl: '$ApiUrl'")
  $suffix = '-' + ([Uri]$ApiUrl).Host
}

$outDir = Join-Path $root 'build\extension'
New-Item -ItemType Directory -Force $outDir | Out-Null
$zipPath = Join-Path $outDir "acortador-pp6-$($manifest.version)$suffix.zip"
if (Test-Path $zipPath) { Remove-Item $zipPath }

# Entradas con '/' y manifest.json en la raiz: Firefox rechaza rutas con '\'.
$stream = [IO.File]::Open($zipPath, [IO.FileMode]::CreateNew)
$zip = New-Object System.IO.Compression.ZipArchive($stream, [System.IO.Compression.ZipArchiveMode]::Create)
try {
  Get-ChildItem $source -Recurse -File | ForEach-Object {
    $name = $_.FullName.Substring($source.Length + 1).Replace('\', '/')
    $entry = $zip.CreateEntry($name, [System.IO.Compression.CompressionLevel]::Optimal)
    $writer = $entry.Open()
    try {
      $bytes = if ($name -eq 'config.js') { $enc.GetBytes($config) } else { [IO.File]::ReadAllBytes($_.FullName) }
      $writer.Write($bytes, 0, $bytes.Length)
    } finally { $writer.Dispose() }
    "  + $name"
  }
} finally {
  $zip.Dispose()
  $stream.Dispose()
}

$api = [regex]::Match($config, "apiBaseUrl:\s*'([^']+)'").Groups[1].Value
"OK: $zipPath (API: $api)"
