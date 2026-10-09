# Pruebas de mutacion de las invariantes (contexto.md 15.4).
# Rompe a proposito cada regla, corre los tests y verifica que alguno falle.
# Cada archivo se restaura desde una copia en memoria (nunca con git checkout).
#
# Uso:  powershell -ExecutionPolicy Bypass -File scripts\mutation-test.ps1 [-Step 1]

param([int]$Step = 0)

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$enc = New-Object System.Text.UTF8Encoding($false)
$src = 'src/main/java/ar/edu/undef/fie/pp6/shortener'

$mutations = @(
  @{ Step = 1; Id = 'I1'; File = "$src/domain/model/ShortLink.java"
     From = 'return !now.isBefore(expiresAt);'; To = 'return now.isAfter(expiresAt);'
     Tests = '*ShortLinkTest*' },
  @{ Step = 1; Id = 'I2'; File = "$src/infrastructure/persistence/JpaShortLinkRepository.java"
     From = 'entityManager.persist(link);'; To = 'entityManager.merge(link);'
     Tests = '*JpaShortLinkRepositoryTest*' },
  @{ Step = 1; Id = 'I6'; File = "$src/infrastructure/persistence/JpaShortLinkRepository.java"
     From = 's.expiresAt <= :now'; To = 's.expiresAt < :now'
     Tests = '*JpaShortLinkRepositoryTest*' },
  @{ Step = 1; Id = 'D6'; File = "$src/infrastructure/persistence/JpaShortLinkRepository.java"
     From = 'entityManager.remove(link);'; To = 'entityManager.remove(link); if (true) return;'
     Tests = '*JpaShortLinkRepositoryTest*' }
)

$survivors = 0
foreach ($m in $mutations | Where-Object { $Step -eq 0 -or $_.Step -eq $Step }) {
  $path = (Resolve-Path $m.File).Path
  $original = [IO.File]::ReadAllText($path)
  if (-not $original.Contains($m.From)) { "[$($m.Id)] NO APLICADA: no se encontro el texto a mutar"; $survivors++; continue }
  [IO.File]::WriteAllText($path, $original.Replace($m.From, $m.To), $enc)
  try {
    $out = cmd /c "gradlew.bat test --tests $($m.Tests) --console=plain 2>&1"
    $failed = $out | Where-Object { $_ -match '> .* FAILED$' } | ForEach-Object { $_.Trim() }
    if ($failed) { "[$($m.Id)] DETECTADA por:"; $failed | ForEach-Object { "    $_" } }
    else { "[$($m.Id)] SOBREVIVIO: ningun test fallo"; $survivors++ }
  } finally {
    [IO.File]::WriteAllText($path, $original, $enc)
  }
}

if ($survivors -gt 0) { "RESULTADO: $survivors mutacion(es) sin detectar"; exit 1 }
"RESULTADO: todas las mutaciones fueron detectadas"
