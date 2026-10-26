# Genera los iconos PNG de la extension en browser-extension/icons/ (16, 24, 32, 48 y 128 px).
# Dibujo: el mismo eslabon de cadena del campo de la web (static/index.html), en blanco sobre un
# cuadrado redondeado con los violetas de styles.css (--accent / --accent-dark).
#
# Uso:  powershell -ExecutionPolicy Bypass -File scripts\generate-extension-icons.ps1

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'browser-extension\icons'
New-Item -ItemType Directory -Force $outDir | Out-Null

function New-RoundedSquare([float]$size, [float]$radius) {
  $path = New-Object System.Drawing.Drawing2D.GraphicsPath
  $d = 2 * $radius
  $path.AddArc(0, 0, $d, $d, 180, 90)
  $path.AddArc($size - $d, 0, $d, $d, 270, 90)
  $path.AddArc($size - $d, $size - $d, $d, $d, 0, 90)
  $path.AddArc(0, $size - $d, $d, $d, 90, 90)
  $path.CloseFigure()
  return $path
}

# Arco de radio 5 centrado en (cx, cy), en las coordenadas 24x24 del SVG de la web.
function Add-Arc($path, [float]$cx, [float]$cy, [float]$start, [float]$sweep) {
  $path.AddArc($cx - 5, $cy - 5, 10, 10, $start, $sweep)
}

# Traduccion del path SVG: M10 13a5 5 0 0 0 7.07 0l3-3a5 5 0 0 0-7.07-7.07l-1.5 1.5
#                          M14 11a5 5 0 0 0-7.07 0l-3 3a5 5 0 0 0 7.07 7.07l1.5-1.5
function New-LinkGlyph {
  $path = New-Object System.Drawing.Drawing2D.GraphicsPath
  Add-Arc $path 13.535 9.465 135 -90
  $path.AddLine(17.07, 13, 20.07, 10)
  Add-Arc $path 16.535 6.465 45 -180
  $path.AddLine(13, 2.93, 11.5, 4.43)
  $path.StartFigure()
  Add-Arc $path 10.465 14.535 -45 -90
  $path.AddLine(6.93, 11, 3.93, 14)
  Add-Arc $path 7.465 17.535 225 -180
  $path.AddLine(11, 21.07, 12.5, 19.57)
  return $path
}

function Save-Icon([int]$size) {
  $bitmap = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bitmap)
  try {
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)

    $background = New-RoundedSquare $size ($size * 0.22)
    $gradient = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
      (New-Object System.Drawing.PointF(0, 0)), (New-Object System.Drawing.PointF($size, $size)),
      [System.Drawing.ColorTranslator]::FromHtml('#9a88ec'), [System.Drawing.ColorTranslator]::FromHtml('#5b4bc4'))
    $g.FillPath($gradient, $background)

    # En 16 y 24 px el trazo es mas grueso y el eslabon ocupa mas lugar, para que se lea en la barra.
    $small = $size -lt 32
    $glyphSize = if ($small) { $size * 0.84 } else { $size * 0.66 }
    $strokeWidth = if ($small) { 3.0 } else { 2.4 }
    $scale = $glyphSize / 24
    $offset = ($size - $glyphSize) / 2
    $g.TranslateTransform($offset, $offset)
    $g.ScaleTransform($scale, $scale)

    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::White, $strokeWidth)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $g.DrawPath($pen, (New-LinkGlyph))

    $file = Join-Path $outDir "icon-$size.png"
    $bitmap.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
    "  + icons/icon-$size.png"
  } finally {
    $g.Dispose()
    $bitmap.Dispose()
  }
}

foreach ($size in 16, 24, 32, 48, 128) { Save-Icon $size }
"OK: $outDir"
