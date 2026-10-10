# Checklist de aceptación – Etapa 1

Criterios de `contexto.md` §15.3, cada uno con el test automático y la verificación real que lo demuestran.
Revisado el 09/10/2026 sobre `dev/agustin` (136 tests, 0 fallos; producción en https://paradigmas6.agustingimenez.ar).

Estados: ✅ demostrado · ⛔ falta evidencia (motivo).

| # | Criterio | Tests automáticos | Verificación real | Estado |
|---|---|---|---|---|
| 1 | URL válida → 201, alias de 5 caracteres del alfabeto, `expiresAt = createdAt + 60 min` | `tc61_createsALink…`, `tc60_secondsRemaining…`, `tc20_generatesAliases…`, `FixedTtlExpirationPolicyTest` | `curl` en el VPS: 201 con `secondsRemaining: 3600` (Paso 3 y Paso 8) | ✅ |
| 2 | URL inválida → 400 con mensaje claro; propio dominio, localhost e IP privada se aceptan | `RegexUrlValidatorTest` (TC-10 a TC-19), `tc13_…D18Message`, `tc14_missingUrlField…`, `tc30_invalidUrlDoesNotInsertAnyRow` | Web: "La dirección debe empezar con http:// o https://" (captura Paso 7) | ✅ |
| 3 | `GET /{alias}` vigente → 302 a la URL original | `tc01_tc32_liveLinkRedirectsWith302…`, `tc02_…`, `ResolveLinkServiceTest` | `curl -I` en el VPS: 302 + `Location` (Paso 4) | ✅ |
| 4 | A los 60:00 o más → 404 aunque la fila exista; el alias se puede reasignar | `tc03_…ExactExpirationInstant`, `tc04_tc05_…`, `tc22_expiredLinkStillStoredIsReassigned` | Web con TTL de 40 s: estado "vencido" y 404 del servidor (Paso 7) | ✅ |
| 5 | Alias inexistente → 404 con la misma página que uno vencido | `tc04_tc05_expiredAndNonExistentAliasesGetTheSameResponse`, `tc05_…` | `curl` en el VPS: 404 con `enlace-no-disponible.html` (Paso 4) | ✅ |
| 6 | La misma URL dos veces → dos alias distintos | `tc24_sameUrlTwiceGetsTwoDifferentAliases…` | — | ✅ |
| 7 | QR decodificado = `shortUrl`; escaneado con un celular abre la URL original; descarga `{alias}.png` | `tc50_…` (decodifica con ZXing), `tc51_downloadAddsAttachment…`, `ZxingQrCodeGeneratorTest` | `curl` en el VPS: `attachment; filename="uuyf6.png"` (Paso 6) | ⛔ falta escanear un QR con un celular |
| 8 | El cron borra los vencidos y no toca los vigentes | `tc40_…`, `tc41_…`, `tc42_…` (borde exacto), cron `0 0 3 * * *` registrado | Primera ejecución en el VPS: 10/10 03:00 | ✅ tests · ⛔ falta ver el log del 10/10 |
| 9 | Colisión → otro alias; superado el tope → 503 | `tc21_…`, `tc23_…` (servicio y API), `tc31_concurrentCreations…` (dos transacciones reales) | Web: mensaje del 503 (captura Paso 7) | ✅ |
| 10 | La API nunca devuelve entidades JPA ni lista enlaces | `tc61_…ExactlyTheContractFields`, `tc36_thereIsNoEndpointThatListsLinks` | `GET /api/v1/links` en el VPS → 405 | ✅ |
| 11 | Tras reiniciar, los enlaces vigentes siguen funcionando | `tc35_linksSurviveAnApplicationRestart` | Cada deploy reinicia el servicio y los enlaces previos siguen redirigiendo (`juf9p`, logs del 09/10) | ✅ |
| 12 | Extensión en Chrome y Firefox: acorta la pestaña activa con un clic; enlace, QR, Copiar, vencimiento; botón deshabilitado fuera de http/https | `BrowserExtensionFilesTest` (4), `CorsConfigTest` (8) | CORS con `curl` en el VPS (Paso 8). Prueba manual de Agustín: "Funcionó" (09/10) | ✅ funcional · ⛔ faltan capturas en los dos navegadores |
| 13 | La web muestra todos los estados de §11.1, incluido "vencido" | `WebClientStaticFilesTest` (5) | 7 capturas en `docs/evidencias/paso-7/` | ✅ |

## Pruebas de mutación (§15.4)

`scripts\mutation-test.ps1` rompe a propósito cada regla y comprueba que algún test falle. **31 mutaciones, todas detectadas** (pasos 1 a 6 y 8).

| Invariante | Mutación | Detectada por |
|---|---|---|
| I1 | `!now.isBefore(expiresAt)` → `now.isAfter(expiresAt)` | `ShortLinkTest` |
| I1 | Ignorar `isExpired` al resolver y al generar el QR | TC-03, TC-04, TC-52 |
| I2 | `persist` → `merge`; quitar el reintento ante la colisión | TC-21, TC-31 |
| I3 | 302 → 301 | TC-32 |
| I4 | `shortUrl` armada desde el request; QR con la URL original | TC-33, TC-50 |
| I6 | `expiresAt <= now` → `<`, `>=` o sin condición | TC-41, TC-42 |
| D45 | CORS abierto a `*`, en `/**`, con todos los métodos o sin headers expuestos | `CorsConfigTest` |

## Cobertura (R13)

JaCoCo: **98,1 %** de líneas en `domain` + `application` (objetivo 70 %). `gradlew check` falla si baja del mínimo.
Reporte: `build/reports/jacoco/test/html/index.html`.

## Aceptación

| Quién | Qué | Fecha |
|---|---|---|
| Sofía Ramirez | ⛔ Pendiente: revisar y aprobar el PR `dev/agustin` → `main` | |
