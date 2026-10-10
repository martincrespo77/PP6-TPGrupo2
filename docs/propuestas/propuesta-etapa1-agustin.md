# Propuesta individual – TP PP6 Link Shortener (Etapa 1)

**Alumno:** Gimenez, Agustín
**Producción:** https://paradigmas6.agustingimenez.ar/ · **Staging:** local en la máquina de cada integrante
**Entrega Etapa 1:** 12/10/2026 · **Etapa 2:** 26/10/2026 · **Etapa 3:** fin de cuatrimestre
**Objetivo de este documento:** ser mi aporte para mergear con las propuestas de mis compañeros y armar el prompt final por pasos.

> Estructura pensada para el merge: cada sección tiene un título fijo (1 a 9). Al mergear, la IA puede comparar sección contra sección entre los 3 documentos.

---

## 1. Resumen de la propuesta

Un backend Spring Boot que acorta URLs, redirige, persiste con JPA/Hibernate y genera QR. Lo consumen dos clientes: una página web y una extensión para Chrome y Firefox.

La idea central de diseño es **aislar cada cosa que el cliente puede cambiar detrás de una interfaz**. Hay que poder cambiar cómo se genera el alias, cuánto dura un enlace, cómo se genera el QR o qué base de datos se usa, y cada cambio tiene que quedar en una clase nueva más configuración, sin reescribir lo existente. Así el sistema queda preparado para los "volantazos" de las etapas 2 y 3.

---

## 2. Base tomada del proyecto de ejemplo del profesor (`SpringBootProject`)

| Elemento | En el ejemplo del profesor | Qué hago yo |
|---|---|---|
| Build | Gradle, Spring Boot 3.4.3, Java 21 | Igual |
| Base de datos | HSQLDB en modo servidor (`jdbc:hsqldb:hsql://localhost:9001/xdb`) | HSQLDB en **modo archivo** en staging, para que cualquiera del grupo lo levante sin servidor aparte. En prod, la base se define por variables de entorno (sección 4.8) |
| Configuración | Un único `application.properties` | **Perfiles de Spring** `staging` y `prod`: mismo código, distinta configuración |
| Acceso a datos | `@Service` con `EntityManager` + HQL, sin Repository | `EntityManager` + JPQL, **encerrado detrás de una interfaz** `ShortLinkRepository` (si mañana cambia, cambia solo la implementación) |
| Capas | `AppController` → `AppService` → entidades en `app.mappings` | Lo mismo, separado en paquetes por responsabilidad (sección 4) |
| Exposición | El controller devuelve la entidad JPA directamente | **DTOs**: nunca exponer entidades (el propio `Enunciado.md` del profesor lo recomienda) |
| Consola HQL | `jpql-console-starter` | La mantengo para mostrar consultas en la defensa |

---

## 3. Elucidación: preguntas para el Cliente (docente)

Son reglas que el enunciado no deja claras. Al lado de cada pregunta pongo la **suposición por defecto** que uso si no hay respuesta, y dejo esa decisión en configuración para poder cambiarla.

| # | Pregunta | Suposición por defecto |
|---|---|---|
| P1 | ¿El alias es aleatorio (`xT3se`) o numérico (`15321`)? ¿Se evitan caracteres confusos (`0/O`, `1/l/I`)? | Base62 aleatorio de 5 caracteres, sin caracteres ambiguos. La estrategia es intercambiable |
| P2 | Si se acorta 2 veces la misma URL estando activa, ¿se devuelve el mismo alias o uno nuevo? | Uno nuevo (cada acortamiento tiene sus propios 60 min) |
| P3 | Cuando un enlace expira, ¿hay que guardar historial o se puede borrar? | Se conserva el registro marcado como expirado y se libera el alias |
| P4 | ¿Qué responde un alias expirado o inexistente? ¿Una página de error? | `410 Gone` si expiró, `404` si nunca existió, con una página HTML simple |
| P5 | ¿Los 60 minutos son fijos o pueden cambiar por enlace o por usuario? | Fijos, configurables en `application.properties` (`app.link.ttl=60m`) |
| P6 | ¿Qué es una "URL válida"? ¿Solo `http/https`? ¿Se bloquea que se acorte una URL del propio dominio? | Solo `http/https`, con host, máximo 2048 caracteres y sin URLs del propio acortador |
| P7 | ¿El dominio de producción sirve, aunque el enunciado muestre una IP? | Prod: `app.base-url=https://paradigmas6.agustingimenez.ar` (HTTPS). Staging: `http://localhost:8080`. Configurable por perfil |
| P8 | ¿Hace falta usuario/login en la etapa 1? | No |
| P9 | ¿La extensión acorta la pestaña activa con un solo clic? | Sí: al abrir el popup acorta la URL de la pestaña activa y muestra enlace + QR + botón copiar |
| P10 | ¿El QR se descarga o solo se muestra? | Se muestra, y se puede descargar como PNG |
| P11 | ¿Se esperan estadísticas (clics, origen)? | No en la etapa 1, pero el modelo lo deja preparado |
| P12 | ¿Redirección 301 o 302? | **302**: un 301 queda en caché del navegador y seguiría redirigiendo después de expirar |

---

## 4. Arquitectura propuesta

### 4.1 Vista general

```text
[Página web]   [Extensión Chrome/Firefox]
       \               /
        \   HTTP/JSON /
         v           v
  ┌───────────── Backend Spring Boot ─────────────┐
  │ web        : controllers REST + redirección   │
  │ application: casos de uso (servicios)         │
  │ domain     : entidad + interfaces (puertos)   │
  │ infra      : JPA, generador alias, QR, reloj  │
  └───────────────────────┬───────────────────────┘
                          v
                   HSQLDB (JPA/Hibernate)
```

Regla de dependencias: `web → application → domain ← infrastructure`. El dominio no conoce Spring MVC, ni ZXing, ni HSQLDB.

### 4.2 Paquetes

```text
com.pp6.shortener
├── ShortenerApplication.java
├── config/            AppProperties (@ConfigurationProperties), ClockConfig, CorsConfig
├── domain/
│   ├── model/         ShortLink (@Entity)
│   ├── port/          AliasGenerator, ExpirationPolicy, QrCodeGenerator, UrlValidator, ShortLinkRepository
│   └── exception/     InvalidUrlException, LinkNotFoundException, LinkExpiredException, AliasExhaustedException
├── application/       ShortenLinkService, ResolveLinkService
├── infrastructure/
│   ├── persistence/   JpaShortLinkRepository (EntityManager + JPQL)
│   ├── alias/         RandomBase62AliasGenerator
│   ├── expiration/    FixedTtlExpirationPolicy
│   ├── qr/            ZxingQrCodeGenerator
│   └── validation/    DefaultUrlValidator
└── web/
    ├── api/           LinkApiController + dto/ (ShortenRequest, ShortLinkResponse)
    ├── redirect/      RedirectController
    └── error/         GlobalExceptionHandler (ProblemDetail RFC 7807)
src/main/resources/static/   index.html, app.js, styles.css (cliente web)
browser-extension/           manifest.json, popup.html, popup.js (Chrome + Firefox, Manifest V3)
docs/                        BITACORA.md, decisiones, minutas
```

### 4.3 Modelo de dominio

`ShortLink` (tabla `short_link`):

| Campo | Tipo | Nota |
|---|---|---|
| `id` | Long | PK autoincremental |
| `alias` | String(16) | Índice. Único **entre enlaces activos** |
| `originalUrl` | String(2048) | URL destino |
| `createdAt` | Instant | Fecha de alta |
| `expiresAt` | Instant | `createdAt + TTL`, calculado por `ExpirationPolicy` |
| `status` | Enum `ACTIVE / EXPIRED` | Permite liberar el alias sin perder el historial |

Métodos de dominio: `isExpired(Instant now)` y `expire()`. La regla "¿está vencido?" vive en la entidad, no en el controller.

> **Liberación del alias:** al generar un alias, solo se buscan colisiones contra enlaces `ACTIVE` y no vencidos. Si el alias pertenece a un enlace vencido, ese enlace se marca `EXPIRED` en la misma transacción y el alias se reasigna. Un `@Scheduled` opcional marca vencidos en lote, pero **la corrección no depende del scheduler**: la expiración se evalúa siempre al leer.

### 4.4 Patrones y por qué (para defender ante el profesor)

| Patrón / principio | Dónde | Qué cambio futuro absorbe |
|---|---|---|
| **Strategy** | `AliasGenerator` | Alias numérico, personalizado o secuencial: una clase nueva y una línea de config |
| **Strategy** | `ExpirationPolicy` | TTL por usuario, "nunca expira" o expiración por cantidad de clics |
| **Puerto/Adaptador** (hexagonal liviano) | `ShortLinkRepository`, `QrCodeGenerator` | Cambiar de base de datos o de librería de QR sin tocar los servicios |
| **DTO** | `web/api/dto` | Cambiar el JSON sin tocar la entidad, y viceversa |
| **Inyección de `Clock`** | Servicios y política de expiración | Testear "pasaron 61 minutos" sin esperar |
| **Configuración externa** | `AppProperties` | Dominio, TTL, largo del alias y alfabeto sin recompilar |
| **Manejador global de errores** | `GlobalExceptionHandler` | Errores HTTP uniformes; una excepción nueva es un `@ExceptionHandler` más |
| SRP / OCP / DIP (SOLID) | Todo el diseño | Extender agregando clases, no modificando las existentes |

### 4.5 Contrato de la API (se documenta con springdoc-openapi / Swagger)

| Método y ruta | Qué hace | Respuestas |
|---|---|---|
| `POST /api/links` body `{ "url": "https://..." }` | Acorta | `201` + `ShortLinkResponse` · `400` URL inválida · `503` sin alias disponibles |
| `GET /api/links/{alias}` | Datos del enlace | `200` · `404` · `410` |
| `GET /api/links/{alias}/qr?size=256` | PNG del QR de la URL corta | `200 image/png` · `404` · `410` |
| `GET /{alias}` | Redirección | `302 Location: originalUrl` · `404` · `410` |
| `GET /` | Página web (static) | `200` |

`ShortLinkResponse`:

```json
{
  "alias": "xT3se",
  "shortUrl": "https://paradigmas6.agustingimenez.ar/xT3se",
  "originalUrl": "https://drive.google.com/drive/folders/...",
  "createdAt": "2026-10-05T12:00:00Z",
  "expiresAt": "2026-10-05T13:00:00Z",
  "qrUrl": "https://paradigmas6.agustingimenez.ar/api/links/xT3se/qr"
}
```

Decisiones de la API:
- **El QR se genera en el backend**, así la web y la extensión reutilizan el mismo endpoint y no se duplica la lógica.
- `GET /{alias}` restringe el alias con la regex `[A-Za-z0-9]{1,16}` para no chocar con `/api/**` ni con archivos estáticos (`/app.js` tiene punto).
- La extensión necesita CORS habilitado para `/api/**`.

### 4.6 Clientes

- **Web:** HTML + JS sin framework, servida por Spring Boot desde `static/`. Tiene un campo "dirección a acortar", el botón **ACORTAR**, y muestra el enlace corto, el botón copiar, el QR y la hora de vencimiento.
- **Extensión (Manifest V3, un solo código para Chrome y Firefox):** al abrir el popup toma la URL de la pestaña activa (`tabs.query`), llama a `POST /api/links` y muestra el enlace, el QR y el botón copiar. Por defecto apunta a producción (`https://paradigmas6.agustingimenez.ar`). Desde la página de opciones se cambia a `http://localhost:8080` para probar en staging.
- **Web:** usa rutas relativas (`/api/links`), así el mismo HTML funciona en staging y en prod sin cambios.

### 4.7 Calidad (QA)

- **Unitarias (JUnit 5 + Mockito):** generador de alias, validador de URL, política de expiración y servicios (con `Clock` fijo).
- **Integración:** `@DataJpaTest` para el repositorio y `@SpringBootTest` + `MockMvc` para la API y la redirección.
- **Cobertura:** JaCoCo, con objetivo de ≥ 80 % en `domain` + `application`.
- **Criterios de aceptación mínimos:**
  1. Una URL válida devuelve 201 con un alias único y `expiresAt = createdAt + 60 min`.
  2. Una URL inválida (`ftp://`, texto suelto, vacía, del propio dominio) devuelve 400 con un mensaje claro.
  3. `GET /{alias}` activo devuelve 302 a la URL original.
  4. `GET /{alias}` a los 61 minutos devuelve 410 y el alias vuelve a estar disponible.
  5. Un alias inexistente devuelve 404.
  6. El QR decodificado (se verifica con ZXing en el test) es igual a `shortUrl`.
  7. La API nunca devuelve entidades JPA.
  8. En prod, `shortUrl` empieza con `https://paradigmas6.agustingimenez.ar/` y el QR abre la URL original desde un celular.

### 4.8 Entornos y despliegue

| | Staging (cada integrante) | Producción |
|---|---|---|
| URL | `http://localhost:8080` | `https://paradigmas6.agustingimenez.ar` |
| Perfil Spring | `staging` (`application-staging.properties`) | `prod` (`application-prod.properties`) |
| `app.base-url` | `http://localhost:8080` | `https://paradigmas6.agustingimenez.ar` |
| Base de datos | HSQLDB en modo archivo (`./data/shortener`) | Base relacional persistente. URL, usuario y contraseña por **variables de entorno**, nunca en el repo |
| HTTPS | No | Sí. Lo resuelve un reverse proxy (Nginx/Caddy) delante de Spring Boot |
| Logs SQL / Swagger | Activados | SQL desactivado. Swagger se decide con el grupo |
| Datos | Descartables | Reales: no se borran entre deploys (`ddl-auto=update` o migraciones) |

Puntos técnicos a tener en cuenta:

- **Detrás del proxy**, Spring tiene que saber que la petición original fue HTTPS: `server.forward-headers-strategy=framework`. Igual, la `shortUrl` se arma siempre desde `app.base-url` y no desde la petición, así que no depende del proxy.
- **CORS:** la extensión llama desde un origen `chrome-extension://...` o `moz-extension://...`. `CorsConfig` habilita `/api/**` para esos orígenes, y para `localhost` en staging.
- **Validación de URL:** se rechaza acortar URLs del propio `app.base-url`, distinto en cada perfil.
- **Empaquetado:** `gradlew bootJar` genera un JAR único. Se puede agregar un `Dockerfile` opcional para que prod y staging corran igual.
- **Deploy temprano:** conviene subir a prod apenas funcione la redirección (Paso 4) para detectar temprano los problemas de infraestructura (HTTPS, proxy, base de datos), en lugar de descubrirlos el 11/10.

Preguntas para el equipo (no son para el cliente):

| # | Pregunta | Suposición por defecto |
|---|---|---|
| E1 | ¿Qué hay en el servidor de prod (VPS, Docker, panel)? ¿Quién tiene acceso? | VPS Linux con Java 21 o Docker, y yo (Agustín) como responsable del deploy |
| E2 | ¿Qué base de datos usamos en prod? | HSQLDB en modo archivo en un volumen persistente; se puede migrar a PostgreSQL cambiando solo la configuración |
| E3 | ¿El deploy es manual o automático (GitHub Actions)? | Manual con script en la etapa 1, y CI/CD como mejora en la etapa 2 |
| E4 | ¿Los alias quedan en la raíz del dominio (`/xT3se`) o el dominio comparte otras rutas? | Raíz del dominio, dedicado al TP |

---

## 5. Anticipación de "volantazos" (etapas 2 y 3)

| Cambio probable | Dónde impacta | Qué hay que hacer |
|---|---|---|
| Alias personalizado elegido por el usuario | `AliasGenerator` + DTO | Campo opcional en el request + validación de disponibilidad |
| Usuarios / login / "mis enlaces" | Nueva entidad `User`, relación en `ShortLink` | Spring Security; los servicios actuales no cambian su lógica |
| Estadísticas de clics | Nueva entidad `ClickEvent`, se registra en `ResolveLinkService` | Un evento por redirección; endpoint de reportes |
| TTL distinto por enlace, o enlaces sin vencimiento | `ExpirationPolicy` | Nueva implementación + campo opcional en el request |
| Expirar por cantidad de usos | `ExpirationPolicy` + contador en `ShortLink` | Sin tocar los controllers |
| Cambiar a MySQL/PostgreSQL | `application.properties` + driver | Nada de código (JPA) |
| Eliminar o editar un enlace | Nuevos endpoints `DELETE/PATCH` | Solo agregar |
| Rate limiting / anti-abuso / lista negra de dominios | `UrlValidator` o un filtro web | Una implementación nueva o un decorador |
| Otro cliente (app móvil, bot) | Ninguno | La API REST ya es el contrato |

---

## 6. Plan por pasos (Etapa 1: 05/10 al 12/10)

Cada paso termina **compilando, con tests en verde y con su entrada en `docs/BITACORA.md`**. No se arranca el siguiente sin validar el anterior.

| Paso | Fecha | Entregable | Cómo se verifica |
|---|---|---|---|
| 0 | 05/10 | Proyecto base: Gradle, dependencias, paquetes, perfiles `staging`/`prod`, `AppProperties`, `Clock`, README, `BITACORA.md` | `gradlew bootRun --args='--spring.profiles.active=staging'` levanta y el test de contexto pasa |
| 1 | 06/10 | Dominio + persistencia: `ShortLink`, puerto `ShortLinkRepository`, implementación JPA | `@DataJpaTest` de guardar, buscar activo por alias y marcar expirado |
| 2 | 07/10 | Validación de URL + generación de alias + política de expiración | Tests unitarios de cada estrategia |
| 3 | 07-08/10 | Caso de uso de acortamiento + API `POST/GET /api/links` + manejo de errores + Swagger | Tests MockMvc 201/400/404/410 |
| 4 | 08/10 | Redirección `GET /{alias}` (302/404/410) y reasignación de alias vencidos. **Primer deploy de prueba a prod** | Test con `Clock` adelantado 61 min + `curl -I https://paradigmas6.agustingimenez.ar/{alias}` |
| 5 | 09/10 | QR (ZXing) `GET /api/links/{alias}/qr` | Test que decodifica el PNG |
| 6 | 09-10/10 | Cliente web | Prueba manual + capturas |
| 7 | 10-11/10 | Extensión Chrome/Firefox + CORS | Carga en modo desarrollador en ambos navegadores |
| 8 | 10-11/10 | Despliegue a prod: JAR o Docker, variables de entorno, reverse proxy HTTPS, script de deploy | La web, la redirección y el QR funcionan en `https://paradigmas6.agustingimenez.ar` |
| 9 | 11/10 | QA final: JaCoCo, scheduler de limpieza, README, guion de demo, smoke test en prod | Reporte de cobertura + checklist de criterios en staging y prod |
| — | 12/10 | **Entrega** | |

---

## 7. Formato de documentación por paso (`docs/BITACORA.md`)

Lo pido explícitamente para poder explicar cada decisión cuando el profesor pregunte. Cada paso agrega una entrada con esta plantilla:

```markdown
## Paso N – <título> (fecha)

### Objetivo
Qué problema resuelve este paso y qué requerimiento del enunciado cubre.

### Estado ANTERIOR
Qué existía antes de este paso (o "nada" si es nuevo).

### Qué es NUEVO
| Archivo | Acción (nuevo/modificado/eliminado) | Para qué sirve |
|---|---|---|

### Qué se MODIFICÓ y por qué
Fragmentos antes/después de lo que cambió en archivos existentes.

### Cómo funciona (explicado simple)
Recorrido de una petición o flujo, paso a paso, en lenguaje llano.

### Decisiones de diseño
Patrón o principio aplicado, alternativas descartadas y por qué.

### Cómo probarlo
Comandos, requests de ejemplo (curl/HTTP) y tests que lo cubren.

### Preguntas probables del profesor (con respuesta)
- ¿Por qué ...? → ...

### Preparado para cambios
Qué cambio futuro absorbe este paso y dónde se haría.
```

---

## 8. Prompt para la IA (por pasos)

### 8.1 Prompt maestro (se envía una sola vez, al inicio)

```text
Actuá como un desarrollador senior Java/Spring Boot y como tutor. Vamos a construir,
POR PASOS, un "Link Shortener" para un TP universitario (Paradigmas de Programación VI,
5to año Ing. Informática). Yo debo poder defender cada línea ante el profesor.

CONTEXTO
- Enunciado: [pegar TP_PP6_v1.0.md]
- Diseño acordado: [pegar secciones 3, 4 y 5 de este documento]
- Proyecto de referencia del profesor: Spring Boot 3.4.3, Java 21, Gradle, HSQLDB,
  servicios con EntityManager + HQL/JPQL. Respetar ese stack y ese estilo.
- Entornos: PRODUCCIÓN en https://paradigmas6.agustingimenez.ar (HTTPS detrás de reverse
  proxy) y STAGING local en la máquina de cada integrante (http://localhost:8080).
  Mismo código, distinta configuración vía perfiles de Spring (staging / prod).
  Ningún secreto en el repositorio: credenciales de prod por variables de entorno.

REQUISITO NO FUNCIONAL CLAVE
El cliente (docente) va a cambiar requerimientos en etapas 2 y 3 sin aviso. El código
debe extenderse agregando clases, no reescribiendo: aplicar SOLID, Strategy para
AliasGenerator y ExpirationPolicy, puertos/adaptadores para persistencia y QR, DTOs,
Clock inyectable, configuración en application.properties via @ConfigurationProperties.

REGLAS DE TRABAJO
1. Hacé SOLO el paso que te pida. No adelantes pasos ni agregues funcionalidades no pedidas.
2. Antes de escribir código, listá en 5-10 líneas qué vas a crear/modificar y por qué.
3. Entregá archivos completos (no fragmentos con "..."), con la ruta de cada uno.
4. Cada paso incluye sus tests (JUnit 5, Mockito, @DataJpaTest, MockMvc según corresponda)
   y debe compilar con `gradlew build`.
5. Al final de cada paso generá la entrada de docs/BITACORA.md con esta plantilla:
   [pegar plantilla de la sección 7]
   Incluí siempre: estado ANTERIOR, qué es NUEVO, qué se MODIFICÓ (antes/después),
   explicación simple, decisiones de diseño, cómo probarlo y preguntas probables del
   profesor con su respuesta.
6. Si una regla de negocio es ambigua, NO inventes: usá la suposición por defecto de la
   tabla de preguntas al cliente y dejala configurable; mencionala en la bitácora.
7. Comentarios en el código solo cuando expliquen una restricción no obvia. Nombres en
   inglés para código, documentación en español.
8. Terminá cada respuesta con: "Paso N completo. ¿Avanzo al paso N+1?" y esperá mi OK.

Confirmá que entendiste resumiendo la arquitectura en 10 líneas. No escribas código todavía.
```

### 8.2 Prompts de cada paso

```text
PASO 0 – Proyecto base
Creá el proyecto Gradle (Spring Boot 3.4.3, Java 21) con: spring-boot-starter-web,
data-jpa, validation, hsqldb, springdoc-openapi-starter-webmvc-ui, com.google.zxing:core
y javase, spring-boot-starter-test, jacoco. Estructura de paquetes de la sección 4.2 (vacía
salvo lo necesario). Configuración en 3 archivos:
- application.properties (común): app.link.ttl=60m, app.alias.length=5,
  app.alias.alphabet (sin caracteres ambiguos), perfil por defecto staging.
- application-staging.properties: HSQLDB modo archivo, app.base-url=http://localhost:8080,
  show-sql activado.
- application-prod.properties: datasource desde variables de entorno (${DB_URL},
  ${DB_USER}, ${DB_PASSWORD}), app.base-url=https://paradigmas6.agustingimenez.ar,
  server.forward-headers-strategy=framework, show-sql desactivado.
Clase AppProperties (@ConfigurationProperties + @Validated), bean Clock
(Clock.systemUTC()). Test de carga de contexto. README con cómo compilar/ejecutar en cada
entorno. .gitignore que excluya ./data y archivos .env. Crear docs/BITACORA.md con la
entrada del Paso 0, explicando qué es un perfil de Spring y por qué separar entornos.
```

```text
PASO 1 – Dominio y persistencia
Entidad ShortLink (id, alias, originalUrl, createdAt, expiresAt, status ACTIVE/EXPIRED)
con métodos isExpired(Instant now) y expire(). Puerto ShortLinkRepository (save,
findActiveByAlias, existsActiveByAlias, markExpiredBefore(Instant)). Implementación
JpaShortLinkRepository con EntityManager + JPQL parametrizado (estilo del profesor).
Tests @DataJpaTest. Bitácora del Paso 1.
```

```text
PASO 2 – Validación, alias y expiración
Interfaces UrlValidator, AliasGenerator, ExpirationPolicy en domain/port.
Implementaciones: DefaultUrlValidator (http/https, host, <=2048, rechaza el propio
base-url), RandomBase62AliasGenerator (SecureRandom, largo y alfabeto desde config),
FixedTtlExpirationPolicy (usa Clock + TTL de config). Tests unitarios de cada una.
Explicá en la bitácora cómo se agregaría otra estrategia sin tocar código existente.
```

```text
PASO 3 – Caso de uso de acortamiento + API REST
ShortenLinkService (transaccional): valida URL, genera alias reintentando ante colisión
con activos (máx. N intentos, luego AliasExhaustedException), si el alias pertenece a un
enlace vencido lo marca EXPIRED y lo reutiliza; contempla DataIntegrityViolationException
por concurrencia. ResolveLinkService para consultar. LinkApiController: POST /api/links,
GET /api/links/{alias}. DTOs ShortenRequest (con @Valid) y ShortLinkResponse.
GlobalExceptionHandler con ProblemDetail (400/404/410/503). Swagger UI habilitado.
Tests unitarios del servicio (Clock fijo) y MockMvc del controller. Bitácora del Paso 3.
```

```text
PASO 4 – Redirección
RedirectController GET /{alias:[A-Za-z0-9]{1,16}} -> 302 a originalUrl; 404 si no existe;
410 si venció (página HTML simple para el navegador). Justificar 302 vs 301 en la
bitácora. Tests MockMvc, incluido el caso "Clock adelantado 61 minutos" y el de
reasignación del alias vencido a una URL nueva.
Al final, dame instrucciones mínimas para un primer deploy de prueba en
https://paradigmas6.agustingimenez.ar con perfil prod, y cómo verificarlo con curl.
```

```text
PASO 5 – Código QR
Puerto QrCodeGenerator (byte[] generatePng(String content, int size)) e implementación
ZxingQrCodeGenerator. Endpoint GET /api/links/{alias}/qr?size=256 (image/png, size
acotado 128..1024). Test que decodifica el PNG con ZXing y verifica que contiene shortUrl.
Bitácora del Paso 5.
```

```text
PASO 6 – Cliente web
static/index.html, app.js, styles.css sin frameworks: campo "Dirección a acortar", botón
ACORTAR, muestra shortUrl (link + botón copiar), QR (img al endpoint qrUrl, botón
descargar) y hora de vencimiento en hora local. Manejo visible de errores 400/503.
Bitácora con capturas sugeridas y explicación del flujo fetch -> API -> render.
```

```text
PASO 7 – Extensión Chrome y Firefox
Carpeta browser-extension/ con Manifest V3 compatible con ambos navegadores (incluir
browser_specific_settings.gecko para Firefox): popup que al abrirse toma la URL de la
pestaña activa, llama POST /api/links y muestra enlace, QR y botón copiar. Página de
opciones para configurar la URL del backend (storage), con valor por defecto
https://paradigmas6.agustingimenez.ar y opción http://localhost:8080 para staging.
host_permissions para ambos. CorsConfig en el backend para /api/** que acepte orígenes
chrome-extension:// y moz-extension:// (y localhost en staging). Bitácora con
instrucciones para cargarla en modo desarrollador en Chrome y en Firefox.
```

```text
PASO 8 – Despliegue a producción
Servidor: [describir: VPS/Docker/panel, SO, si ya hay Nginx/Caddy]. Generar:
- Empaquetado: gradlew bootJar y Dockerfile opcional (multi-stage, JRE 21).
- Ejecución con perfil prod y variables de entorno (archivo .env de ejemplo SIN valores
  reales: .env.example).
- Configuración de reverse proxy con HTTPS para paradigmas6.agustingimenez.ar hacia el
  puerto de la app, reenviando X-Forwarded-Proto/Host.
- Persistencia de la base en un volumen/directorio que sobreviva a los redeploys.
- Script de deploy (build, copiar, reiniciar) y cómo ver logs.
Bitácora: diagrama staging vs prod, qué cambia entre entornos y por qué el código no
cambia; checklist de verificación post-deploy.
```

```text
PASO 9 – QA y cierre de la Etapa 1
Configurar JaCoCo (reporte HTML, umbral 80% en domain+application). @Scheduled opcional
que marca vencidos (activable por config). Revisar que la API no exponga entidades.
Checklist de criterios de aceptación con el test que cubre cada uno, verificado en
staging y smoke test en prod (acortar, redirigir, escanear QR con el celular, extensión
apuntando a prod). README final y guion de demo de 5 minutos usando prod. Resumen en la
bitácora: arquitectura final y tabla "volantazo probable -> dónde se implementaría".
```

### 8.3 Prompt para cuando llegue un "volantazo" (etapas 2 y 3)

```text
El cliente cambió/agregó estos requerimientos: [pegar].
Antes de programar:
1. Analizá el impacto: qué clases se agregan, cuáles se modifican y por qué.
   Si hay que modificar mucho código existente, señalalo como deuda de diseño.
2. Proponé preguntas de elucidación para el cliente y suposiciones por defecto.
3. Dividí el cambio en pasos numerados (continuando la numeración de la bitácora).
Esperá mi OK antes de implementar. Luego seguí las mismas reglas del prompt maestro.
```

---

## 9. Puntos para discutir con mis compañeros en el merge

1. **Repository:** ¿`EntityManager` detrás de una interfaz (mi propuesta, alineada al profesor) o Spring Data JPA?
2. **Base de datos:** ¿HSQLDB en modo servidor como el ejemplo, o en modo archivo para simplificar?
3. **QR:** ¿en el backend (mi propuesta, una sola implementación) o en el cliente con una librería JS?
4. **Historial:** ¿marcar `EXPIRED` (mi propuesta) o borrar los enlaces vencidos?
5. **Frontend:** ¿JS sin framework (mi propuesta, menos complejidad) o React/Vue?
6. **Reparto de pasos** entre los 3 integrantes, y quién consolida la bitácora.
7. **Producción:** responder las preguntas E1 a E4 de la sección 4.8 (servidor, base de datos, tipo de deploy, uso del dominio).
