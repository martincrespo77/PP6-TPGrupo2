# Pruebas de mutacion de las invariantes (contexto.md 15.4).
# Rompe a proposito cada regla, corre los tests y verifica que alguno falle.
# Cada archivo se restaura desde una copia en memoria (nunca con git checkout).
#
# Uso:  powershell -ExecutionPolicy Bypass -File scripts\mutation-test.ps1 [-Step 1]

param([int]$Step = 0)

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$enc = New-Object System.Text.UTF8Encoding($false)
$src = 'src/main/java/app'

$mutations = @(
  @{ Step = 1; Id = 'I1'; File = "$src/mappings/ShortLink.java"
     From = 'return !now.isBefore(expiresAt);'; To = 'return now.isAfter(expiresAt);'
     Tests = '*ShortLinkTest*' },
  @{ Step = 1; Id = 'I2'; File = "$src/service/LinkService.java"
     From = 'em.persist(link);'; To = 'em.merge(link);'
     Tests = '*LinkServiceTest*' },
  @{ Step = 1; Id = 'I6'; File = "$src/service/LinkService.java"
     From = 's.expiresAt <= :now'; To = 's.expiresAt < :now'
     Tests = '*LinkServiceTest*' },
  @{ Step = 2; Id = 'D17'; File = "$src/service/UrlValidator.java"
     From = 'url.length() > ShortLink.MAX_URL_LENGTH'; To = 'url.length() >= ShortLink.MAX_URL_LENGTH'
     Tests = '*UrlValidatorTest*' },
  @{ Step = 2; Id = 'D16'; File = "$src/service/UrlValidator.java"
     From = '"^https?://.*"'; To = '"^(https?|ftp)://.*"'
     Tests = '*UrlValidatorTest*' },
  @{ Step = 2; Id = 'D19'; File = "$src/service/UrlValidator.java"
     From = '&& uri.getHost() != null'; To = ''
     Tests = '*UrlValidatorTest*' },
  @{ Step = 2; Id = 'D10'; File = "$src/service/AliasGenerator.java"
     From = 'if (!isReserved(candidate))'; To = 'if (true)'
     Tests = '*AliasGeneratorTest*' },
  @{ Step = 2; Id = 'TTL'; File = "$src/service/LinkService.java"
     From = 'new ShortLink(alias, url, now, now.plus(ttl))'; To = 'new ShortLink(alias, url, now, now.plusSeconds(1))'
     Tests = '*LinkServiceTest*' },
  @{ Step = 2; Id = 'CLK'; File = "$src/Application.java"
     From = 'Clock.tick(Clock.systemUTC(), Duration.ofMillis(1))'; To = 'Clock.systemUTC()'
     Tests = '*ApplicationTest*' },
  @{ Step = 3; Id = 'I2-retry'; File = "$src/service/LinkService.java"
     From = 'log.debug("Alias {} tomado por otra'; To = 'if (true) throw e; log.debug("Alias {} tomado por otra'
     Tests = '*LinkServiceTest*' },
  @{ Step = 3; Id = 'I4'; File = "$src/service/LinkService.java"
     From = 'return baseUrl() + "/" + alias;'
     To = 'return org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath().toUriString() + "/" + alias;'
     Tests = '*LinkControllerTest*' },
  @{ Step = 3; Id = 'D13'; File = "$src/service/LinkService.java"
     From = 'if (shortUrl(alias).equals(url)) {'; To = 'if (false) {'
     Tests = '*LinkServiceTest*' },
  @{ Step = 3; Id = 'D6'; File = "$src/service/LinkService.java"
     From = 'em.remove(existing);'; To = ''
     Tests = '*LinkServiceTest*' },
  @{ Step = 3; Id = 'D11'; File = "$src/service/LinkService.java"
     From = 'attempt <= maxAttempts'; To = 'attempt < maxAttempts'
     Tests = '*LinkServiceTest*' },
  @{ Step = 3; Id = 'D24'; File = "$src/dto/ShortLinkResponse.java"
     From = 'Math.ceilDiv(millis, 1000)'; To = 'Math.floorDiv(millis, 1000)'
     Tests = '*ShortLinkResponseTest*' },
  @{ Step = 4; Id = 'I1-resolve'; File = "$src/service/LinkService.java"
     From = '.filter(link -> !link.isExpired(now))'; To = '.filter(link -> true)'
     Tests = '*LinkServiceTest*' },
  @{ Step = 4; Id = 'I3'; File = "$src/controller/RedirectController.java"
     From = 'ResponseEntity.status(HttpStatus.FOUND)'; To = 'ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)'
     Tests = '*RedirectControllerTest*' },
  @{ Step = 4; Id = 'Q7'; File = "$src/service/LinkService.java"
     From = 'String normalized = alias.toLowerCase(Locale.ROOT);'; To = 'String normalized = alias;'
     Tests = '*LinkServiceTest*' },
  @{ Step = 5; Id = 'I6-gte'; File = "$src/service/LinkService.java"
     From = 's.expiresAt <= :now'; To = 's.expiresAt >= :now'
     Tests = '*LinkServiceTest*' },
  @{ Step = 5; Id = 'I6-todo'; File = "$src/service/LinkService.java"
     From = 's.expiresAt <= :now'; To = 's.expiresAt is not null or s.expiresAt = :now'
     Tests = '*LinkServiceTest*' },
  @{ Step = 5; Id = 'D5-cron'; File = "$src/service/LinkService.java"
     From = '@Scheduled(cron = "${app.cleanup.cron}")'; To = ''
     Tests = '*LinkServiceTest*' },
  @{ Step = 5; Id = 'D5-clock'; File = "$src/service/LinkService.java"
     From = '.setParameter("now", clock.instant())'; To = '.setParameter("now", clock.instant().minusSeconds(3600))'
     Tests = '*LinkServiceTest*' },
  @{ Step = 6; Id = 'I4-qr'; File = "$src/service/QrService.java"
     From = 'String content = linkService.shortUrl(link.getAlias());'; To = 'String content = link.getOriginalUrl();'
     Tests = '*LinkControllerTest*' },
  @{ Step = 6; Id = 'D22'; File = "$src/controller/LinkController.java"
     From = 'ContentDisposition.attachment()'; To = 'ContentDisposition.inline()'
     Tests = '*LinkControllerTest*' },
  @{ Step = 6; Id = 'E7'; File = "$src/service/QrService.java"
     From = 'if (size < MIN_SIZE || size > MAX_SIZE) {'; To = 'if (false) {'
     Tests = '*LinkControllerTest*' },
  @{ Step = 6; Id = 'I1-qr'; File = "$src/service/LinkService.java"
     From = '.filter(link -> !link.isExpired(now))'; To = '.filter(link -> true)'
     Tests = '*LinkControllerTest*' },
  @{ Step = 8; Id = 'D45-origen'; File = "$src/Application.java"
     From = '.allowedOriginPatterns(EXTENSION_ORIGINS)'; To = '.allowedOriginPatterns("*")'
     Tests = '*CorsTest*' },
  @{ Step = 8; Id = 'D45-ruta'; File = "$src/Application.java"
     From = 'registry.addMapping("/api/**")'; To = 'registry.addMapping("/**")'
     Tests = '*CorsTest*' },
  @{ Step = 8; Id = 'D45-metodos'; File = "$src/Application.java"
     From = '.allowedMethods("GET", "POST")'; To = '.allowedMethods("*")'
     Tests = '*CorsTest*' },
  @{ Step = 8; Id = 'D45-expuestos'; File = "$src/Application.java"
     From = '.exposedHeaders(HttpHeaders.LOCATION, HttpHeaders.RETRY_AFTER)'; To = ''
     Tests = '*CorsTest*' }
)

$survivors = 0
foreach ($m in $mutations | Where-Object { $Step -eq 0 -or $_.Step -eq $Step }) {
  $path = (Resolve-Path $m.File).Path
  $original = [IO.File]::ReadAllText($path)
  if (-not $original.Contains($m.From)) { "[$($m.Id)] NO APLICADA: no se encontro el texto a mutar"; $survivors++; continue }
  [IO.File]::WriteAllText($path, $original.Replace($m.From, $m.To), $enc)
  try {
    $out = cmd /c ".\gradlew.bat test --tests $($m.Tests) --console=plain 2>&1"
    $failed = $out | Where-Object { $_ -match '> .* FAILED$' } | ForEach-Object { $_.Trim() }
    if ($failed) { "[$($m.Id)] DETECTADA por:"; $failed | ForEach-Object { "    $_" } }
    else { "[$($m.Id)] SOBREVIVIO: ningun test fallo"; $survivors++ }
  } finally {
    [IO.File]::WriteAllText($path, $original, $enc)
  }
}

if ($survivors -gt 0) { "RESULTADO: $survivors mutacion(es) sin detectar"; exit 1 }
"RESULTADO: todas las mutaciones fueron detectadas"
