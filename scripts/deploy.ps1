# Despliegue en el VPS (paradigmas6.agustingimenez.ar).
# Compila y testea, sube el JAR, lo instala con backup, reinicia el servicio y verifica que responda.
# Si el health check falla, restaura el JAR anterior automaticamente.
#
# Requisitos: alias SSH con clave autorizada (por defecto VPS-DonWeb en ~/.ssh/config).
# Uso:  powershell -ExecutionPolicy Bypass -File scripts\deploy.ps1 [-SshHost VPS-DonWeb] [-SkipBuild] [-AllowDirty]

param(
  [string]$SshHost = 'VPS-DonWeb',
  [string]$PublicUrl = 'https://paradigmas6.agustingimenez.ar/',
  [switch]$SkipBuild,
  [switch]$AllowDirty
)

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$jar = 'build/libs/shortener.jar'

function Fail($message) { Write-Host "ERROR: $message" -ForegroundColor Red; exit 1 }

if (-not $AllowDirty -and (git status --porcelain)) {
  Fail 'Hay cambios sin commitear. Lo desplegado debe corresponder a un commit (o usar -AllowDirty).'
}
$commit = (git rev-parse --short HEAD).Trim()
Write-Host "Desplegando commit $commit en $SshHost"

if (-not $SkipBuild) {
  cmd /c "gradlew.bat clean build --console=plain"
  if ($LASTEXITCODE -ne 0) { Fail 'El build o los tests fallaron.' }
}
if (-not (Test-Path $jar)) { Fail "No existe $jar" }

$localHash = (Get-FileHash $jar -Algorithm SHA256).Hash.ToLower()
scp -o BatchMode=yes $jar "${SshHost}:/tmp/shortener.jar"
if ($LASTEXITCODE -ne 0) { Fail 'No se pudo copiar el JAR.' }
$remoteHash = (ssh -o BatchMode=yes $SshHost 'sha256sum /tmp/shortener.jar').Split(' ')[0]
if ($remoteHash -ne $localHash) { Fail "El hash remoto ($remoteHash) no coincide con el local ($localHash)." }

$remote = @"
set -euo pipefail
COMMIT='$commit'
JAR=/opt/pp6-shortener/shortener.jar
TS=`$(date +%Y%m%d-%H%M%S)
cp -a "`$JAR" "`$JAR.bak-`$TS"
install -m 644 -o root -g root /tmp/shortener.jar "`$JAR"
rm -f /tmp/shortener.jar
systemctl restart pp6-shortener
code=000
for i in `$(seq 1 30); do
  code=`$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/ || true)
  [ "`$code" = 200 ] && break
  sleep 2
done
if [ "`$code" != 200 ]; then
  echo "Health check fallido (HTTP `$code): restaurando el JAR anterior"
  cp -a "`$JAR.bak-`$TS" "`$JAR"
  systemctl restart pp6-shortener
  exit 1
fi
echo "`$COMMIT `$TS" > /opt/pp6-shortener/DEPLOYED
ls -1t "`$JAR".bak-* | tail -n +4 | xargs -r rm -f
journalctl -u pp6-shortener -n 50 --no-pager -o cat | grep 'Started ShortenerApplication' | tail -1
echo "OK: commit `$COMMIT activo"
# fin
"@
($remote -replace "`r", '') | ssh -o BatchMode=yes $SshHost 'bash -s'
if ($LASTEXITCODE -ne 0) { Fail 'El despliegue fallo en el servidor (se restauro la version anterior).' }

try {
  $status = (Invoke-WebRequest $PublicUrl -UseBasicParsing -TimeoutSec 20).StatusCode
  Write-Host "Verificacion publica: $PublicUrl -> $status" -ForegroundColor Green
} catch {
  Fail "La app responde en el servidor pero no desde $PublicUrl : $($_.Exception.Message)"
}
