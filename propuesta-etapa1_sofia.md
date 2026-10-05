# Propuesta individual – TP PP6 Link Shortener (Etapa 1)

**Alumna:** Ramirez, Sofia
**Ejecución:** local en la máquina de cada integrante (esta entrega) · **Despliegue futuro:** VPS de un compañero
**Entregas:** Etapa 1: 12/10/2026 · Etapa 2 (con nuevos requerimientos): 26/10/2026 · Etapa 3 (con nuevos requerimientos): 27/11/2026
**Contenido de la entrega Etapa 1:** repositorio, paquete de la extensión y registro de prompts (formato a confirmar con el docente)
**Objetivo de este documento:** ser mi aporte para mergear con las propuestas de mis compañeros y armar el prompt final por pasos.

> Estructura pensada para el merge: cada sección tiene un título fijo (1 a 9). Al mergear, la IA puede comparar sección contra sección entre los documentos.

---

## 1. Resumen de la propuesta

Un backend Spring Boot que acorta URLs, redirige, persiste con JPA/Hibernate y genera el QR en PNG. Lo consumen dos clientes: una página web y una extensión para Chrome y Firefox, ambos contra la misma API.

El principio rector es **hacer lo mínimo que pide el enunciado, pero aislando detrás de una interfaz todo lo que el cliente podría cambiar**: cómo se genera el alias, cuánto dura un enlace, cómo se valida la URL, cómo se genera el QR y qué base de datos se usa. Así, un requerimiento nuevo de las etapas 2 y 3 se resuelve con una clase nueva más configuración, sin reescribir lo existente.

Dos decisiones simplifican mucho el diseño: los enlaces vencidos se **borran con un cron** (sin historial ni reutilización de alias), y un alias vencido o inexistente responde siempre **404** con la misma página.

---

## 2. Base tomada del proyecto de ejemplo del profesor (`SpringBootProject`)

| Elemento | En el ejemplo del profesor | Qué hacemos nosotros |
|---|---|---|
| Build | Gradle, Spring Boot 3.4.3, Java 21 | Gradle y **Java 25** (confirmado). Hay que verificar qué versión de Spring Boot y de Gradle soporta Java 25, porque la 3.4.3 del ejemplo probablemente no lo soporta oficialmente |
| Base de datos | HSQLDB en modo servidor (`jdbc:hsqldb:hsql://localhost:9001/xdb`) | HSQLDB en **modo archivo**: los enlaces vigentes sobreviven a un reinicio y no hace falta un servidor aparte. El motor lo definimos nosotros y es intercambiable por configuración |
| Configuración | Un único `application.properties` | Un `application.properties` con todo parametrizado (TTL, alias, cron, `app.base-url`). Un perfil `vps` solo cuando se despliegue |
| Acceso a datos | `@Service` con `EntityManager` + HQL, sin Repository | `EntityManager` + JPQL **detrás de una interfaz** `ShortLinkRepository` (a discutir en el merge) |
| Capas | `AppController` → `AppService` → entidades en `app.mappings` | Lo mismo, separado en paquetes por responsabilidad (sección 4) |
| Exposición | El controller devuelve la entidad JPA directamente | **DTOs**: nunca exponer entidades |
| Consola HQL | `jpql-console-starter` | La mantengo para mostrar consultas en la defensa |

---

## 3. Elucidación: preguntas para el Cliente (docente)

Esta sección tiene cuatro partes: lo que el cliente ya respondió, lo que definimos nosotros, lo que queda abierto, y las dudas pendientes.

### 3.1 Respuestas ya dadas por el cliente (cerradas)

| # | Tema | Respuesta del cliente |
|---|---|---|
| C1 | URL válida | Solo formato (regex), solo `http`/`https`. No se verifica que el sitio responda. Se aceptan URLs al propio dominio del acortador, IPs privadas y localhost |
| C2 | Alias | Automático por ahora, dejando abierta la posibilidad de uno personalizado. Los caracteres los definimos nosotros, y debe ser fácil de recordar |
| C3 | Misma URL acortada dos veces | Alias distintos (distinto momento de creación y vencimiento) |
| C4 | Palabras reservadas | Las definimos nosotros |
| C5 | Vigencia | 60 minutos desde la creación, exactos, sin posibilidad de extender ni período de gracia |
| C6 | Alias vencido | Mostrar una página informativa, con redirección a nuestra página para crear un enlace nuevo |
| C7 | Accesos | Sin límite de accesos por enlace y sin contador |
| C8 | Cliente web | Muestra solo el enlace recién generado, sin historial |
| C9 | Usuarios | Sin usuarios por ahora. A futuro podría haberlos para que cada uno gestione sus URLs |
| C10 | Extensión | Acorta la pestaña activa, usa la misma API, se instala solo en local (sin tiendas). En pestañas no http/https: botón deshabilitado con aviso. La URL de la API no es configurable por ahora |
| C11 | QR | PNG descargable, con el alias como nombre de archivo. Se genera donde sea mejor. Sin copia automática al portapapeles |
| C12 | Despliegue | Local por ahora, luego un VPS de un compañero. Para esta entrega alcanza en local |
| C13 | Disponibilidad | 24/7 mientras haya enlaces vigentes. Los enlaces se conservan si el servicio se reinicia |
| C14 | Seguridad y privacidad | Sin HTTPS, sin autenticación (API pública), sin requisitos de privacidad. Uso similar al de notes.io en cuanto a privacidad. Si el usuario cierra la pantalla sin copiar el enlace o descargar el QR, no puede recuperarlos |
| C15 | Tráfico | Bajo: app interna por el momento |
| C16 | Tecnología | Java 25 (confirmado). Motor de base de datos y navegadores soportados: los definimos nosotros. Sin restricciones de infraestructura |
| C17 | Entrega | Repositorio, paquete de la extensión y registro de prompts. Fecha: 12/10/2026. Formato: desconocido |
| C18 | Calidad | Documentación básica de código. Versionado de API (`/api/v1`) deseable. Logging deseable si no complica. Registro de prompts obligatorio. Cobertura mínima: no se sabe |
| C19 | Evaluación y continuidad | Se evalúan la minuta de elucidación y las decisiones de diseño. Las etapas 2 y 3 parten del código de la etapa 1 (no hay código oficial del docente). Se puede volver a consultar decisiones previas. Idioma: solo español |
| C20 | Evolución | El cliente no adelantará qué va a cambiar: lo que se evalúa es qué tan escalable es nuestro diseño |

### 3.2 Decisiones definidas por el equipo

| # | Tema | Decisión | Motivo |
|---|---|---|---|
| D1 | Alias | 5 caracteres, minúsculas y dígitos sin caracteres ambiguos (`0`, `o`, `1`, `l`, `i`): 31 caracteres, unos 28,6 millones de combinaciones. Largo y alfabeto configurables | Corto y fácil de recordar; sobra espacio para el volumen esperado |
| D2 | Palabras reservadas | `api`, `admin`, `static`, `assets`, `error`, `expired`, `health`, `actuator`, `swagger`, `favicon.ico`, `index`. Lista configurable | Evitar choques con rutas del sistema |
| D3 | Colisión de alias | Se genera otro. Tope de 10 reintentos, luego `503` con mensaje claro | A este volumen es casi imposible, pero evita un bucle infinito |
| D4 | Enlaces vencidos | Un cron los **borra**. Frecuencia diaria por defecto, expresión configurable. Sin historial y sin reutilización intencional de alias | Base de datos chica, sin riesgo de que alguien llegue a un destino que no buscaba, menos complejidad |
| D5 | Vencimiento exacto | En cada acceso se compara `expiresAt` con la hora actual del servidor. El cron solo limpia | La exactitud no depende de cuándo corre el cron |
| D6 | Alias vencido o inexistente | **404** siempre, con la misma página: "Este enlace expiró o no existe", más un botón a la web para crear uno nuevo | Una vez borrada la fila, ambos casos son indistinguibles |
| D7 | Redirección | **302** | Un 301 queda en caché del navegador y seguiría redirigiendo después de expirar |
| D8 | URL sin esquema | Se rechaza con `400` ("La dirección debe empezar con http:// o https://"). Largo máximo 2048 caracteres. Sin normalización: se guarda tal cual | Coherente con la regex del cliente y lo más simple |
| D9 | Enlace que apunta a sí mismo | Si la `shortUrl` a generar es igual a la URL destino, se genera otro alias | Evita el loop infinito en el caso (muy raro) en que el destino sea un alias ya borrado |
| D10 | Cadena A → B con B vencido | Se acepta; el destino final deja de ser alcanzable. Se documenta | Comportamiento esperado, consecuencia de aceptar URLs propias |
| D11 | Hora de vencimiento | Web y extensión muestran la hora de vencimiento. Si el enlace vence con la pantalla abierta, pasa a estado "vencido" (enlace y QR deshabilitados). La API devuelve `expiresAt` y `secondsRemaining` | Dejar claro que el enlace ya no sirve. Los segundos restantes evitan el desfase de relojes |
| D12 | Armado de la URL corta | Siempre desde `app.base-url`, sin puerto fijo | Sirve igual para local y para el VPS |
| D13 | Base de datos | HSQLDB en modo archivo | Persiste reinicios y es la del ejemplo del profesor |
| D14 | Versionado | Prefijo `/api/v1` | Costo mínimo y es un punto de extensión |
| D15 | Logging | SLF4J básico: creación, redirección, 404 y cantidad de enlaces borrados por el cron | Aporta sin complicar. Monitoreo completo: descartado |
| D16 | Navegadores | Chrome y Firefox actuales de escritorio. La web también debe verse bien en móvil | Es lo que pide el enunciado |
| D17 | Cobertura de tests | Objetivo ≥ 70 % en `domain` + `application` (a confirmar) | El cliente no definió mínimo |
| D18 | Botón "copiar" | Manual en web y extensión (la copia automática fue descartada por el cliente) | Supuesto: sin él el usuario no podría guardar el enlace fácilmente |

### 3.3 Preguntas abiertas para el Cliente (con suposición por defecto)

| # | Pregunta | Suposición por defecto |
|---|---|---|
| Q1 | ¿Cuál es el formato de entrega de esta etapa? (la fecha es el 12/10/2026) | Repositorio git, `.zip` de la extensión y carpeta `docs/` con el registro de prompts |
| Q2 | ¿Hay una cobertura mínima de pruebas esperada? | ≥ 70 % en `domain` + `application` |
| Q3 | ¿Se espera un formato específico para el registro de prompts? | `docs/BITACORA.md` por paso, con los prompts utilizados |
| Q4 | ¿Alcanza con entregar la extensión sin firmar, cargada en modo desarrollador? En Firefox la carga es temporal y se pierde al reiniciar el navegador | Sí. El README explica cómo cargarla en ambos navegadores |
| Q5 | ¿Valida las decisiones propias D4 (cron de borrado), D6 (404 único) y D7 (302)? | Se presentan como supuestos en la minuta y se pueden revisar |

### 3.4 Dudas pendientes (no bloquean la Etapa 1)

- **Rendimiento y escalabilidad:** el cliente no tiene datos. Se asume tráfico bajo (app interna).
- **Frecuencia ideal del cron:** diaria por defecto. Si el volumen creciera, es un cambio de configuración.
- **Hosting definitivo:** datos del VPS (SO, acceso, dominio o IP, puerto). Ver preguntas E1 a E5 en la sección 4.8.

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
  │ infra      : JPA, alias, QR, reloj, cron      │
  └───────────────────────┬───────────────────────┘
                          v
                   HSQLDB (JPA/Hibernate)
```

Regla de dependencias: `web → application → domain ← infrastructure`. El dominio no conoce Spring MVC, ni ZXing, ni HSQLDB.

### 4.2 Paquetes

```text
com.pp6.shortener                      (nombre de paquete a acordar en el merge)
├── ShortenerApplication.java
├── config/            AppProperties (@ConfigurationProperties), ClockConfig, CorsConfig
├── domain/
│   ├── model/         ShortLink (@Entity)
│   ├── port/          AliasGenerator, ExpirationPolicy, QrCodeGenerator, UrlValidator, ShortLinkRepository
│   └── exception/     InvalidUrlException, AliasExhaustedException
├── application/       ShortenLinkService, ResolveLinkService, ExpiredLinksCleanupService
├── infrastructure/
│   ├── persistence/   JpaShortLinkRepository (EntityManager + JPQL)
│   ├── alias/         RandomAliasGenerator
│   ├── expiration/    FixedTtlExpirationPolicy
│   ├── qr/            ZxingQrCodeGenerator
│   ├── validation/    RegexUrlValidator
│   └── scheduling/    ExpiredLinksCleanupJob (@Scheduled)
└── web/
    ├── api/           LinkApiController + dto/ (ShortenRequest, ShortLinkResponse)
    ├── redirect/      RedirectController
    └── error/         GlobalExceptionHandler (ProblemDetail RFC 7807)
src/main/resources/static/   index.html, app.js, styles.css, enlace-no-disponible.html
browser-extension/           manifest.json, popup.html, popup.js, config.js (Chrome + Firefox, Manifest V3)
docs/                        BITACORA.md, decisiones, minutas
```

### 4.3 Modelo de dominio

`ShortLink` (tabla `short_link`):

| Campo | Tipo | Nota |
|---|---|---|
| `alias` | String(16) | **Clave primaria**: la base garantiza la unicidad |
| `originalUrl` | String(2048) | URL destino |
| `createdAt` | Instant | Fecha de alta |
| `expiresAt` | Instant | `createdAt + TTL`, calculado por `ExpirationPolicy`. Con índice, para el borrado del cron |

Método de dominio: `isExpired(Instant now)`. La regla "¿está vencido?" vive en la entidad, no en el controller.

> **Ciclo de vida del enlace:** al acortar, se genera un alias al azar y se inserta. Si ya existe (vigente o vencido pero todavía no borrado), se genera otro. Al acceder, se busca el alias y se compara `expiresAt` con la hora actual: si no existe o está vencido, responde 404. Un cron diario borra las filas vencidas. **La corrección no depende del cron**: el vencimiento se evalúa siempre al leer.

> **Privacidad:** el modelo es similar al de notes.io, según nuestro conocimiento (a verificar): el enlace es el único "secreto", no hay cuentas y no existe ningún endpoint que liste los enlaces vigentes. Nadie debería acortar información sensible.

### 4.4 Patrones y por qué (para defender ante el profesor)

| Patrón / principio | Dónde | Qué cambio futuro absorbe |
|---|---|---|
| **Strategy** | `AliasGenerator` | Alias personalizado, numérico o secuencial: una clase nueva y una línea de config |
| **Strategy** | `ExpirationPolicy` | TTL por enlace o por usuario, enlaces permanentes, expiración por cantidad de usos |
| **Strategy** | `UrlValidator` | Blacklist de dominios, más esquemas, filtros anti-abuso (con un decorador) |
| **Puerto/Adaptador** (hexagonal liviano) | `ShortLinkRepository`, `QrCodeGenerator` | Cambiar de base de datos o de librería de QR sin tocar los servicios |
| **DTO** | `web/api/dto` | Cambiar el JSON sin tocar la entidad, y viceversa |
| **Inyección de `Clock`** | Servicios, política de expiración y cron | Testear "pasaron 61 minutos" sin esperar |
| **Configuración externa** | `AppProperties` | Dominio, TTL, largo y alfabeto del alias, palabras reservadas y cron sin recompilar |
| **Manejador global de errores** | `GlobalExceptionHandler` | Errores HTTP uniformes; una excepción nueva es un `@ExceptionHandler` más |
| SRP / OCP / DIP (SOLID) | Todo el diseño | Extender agregando clases, no modificando las existentes |

### 4.5 Contrato de la API (se documenta con springdoc-openapi / Swagger)

| Método y ruta | Qué hace | Respuestas |
|---|---|---|
| `POST /api/v1/links` body `{ "url": "https://..." }` | Acorta | `201` + `ShortLinkResponse` · `400` URL inválida · `503` sin alias disponibles |
| `GET /api/v1/links/{alias}/qr?size=256&download=false` | PNG del QR de la URL corta. Con `download=true` agrega `Content-Disposition: attachment; filename="{alias}.png"` | `200 image/png` · `404` |
| `GET /{alias}` | Redirección | `302 Location: originalUrl` · `404` (página "Este enlace expiró o no existe") |
| `GET /` | Página web (static) | `200` |

`ShortLinkResponse`:

```json
{
  "alias": "xt3se",
  "shortUrl": "http://localhost:8080/xt3se",
  "originalUrl": "https://drive.google.com/drive/folders/...",
  "createdAt": "2026-10-05T12:00:00Z",
  "expiresAt": "2026-10-05T13:00:00Z",
  "secondsRemaining": 3600,
  "qrUrl": "http://localhost:8080/api/v1/links/xt3se/qr"
}
```

Decisiones de la API:
- **El QR se genera en el backend**: la web y la extensión reutilizan el mismo endpoint, y si en el futuro hay gestión de enlaces el QR queda asociado a su URL.
- **Descarga con el nombre del alias** vía `Content-Disposition`, porque el atributo `download` de HTML no funciona entre orígenes distintos (caso de la extensión).
- `GET /{alias}` restringe el alias con la regex `[A-Za-z0-9]{1,16}` para no chocar con `/api/**` ni con archivos estáticos (`/app.js` tiene punto).
- La página de 404 es un HTML estático servido con status 404 por `RedirectController`.
- La extensión necesita CORS habilitado para `/api/**`.
- No hay `GET /api/v1/links/{alias}`: los clientes solo necesitan el resultado de la creación.

### 4.6 Clientes

- **Web:** HTML + JS sin framework, servida por Spring Boot desde `static/`. Campo "dirección a acortar", botón **ACORTAR**, y resultado con enlace corto, botón copiar, QR, botón descargar QR (`{alias}.png`) y hora de vencimiento en hora local. Muestra solo el último enlace generado. Si el enlace vence con la pantalla abierta, un `setTimeout` (calculado con `secondsRemaining`) cambia el estado a "vencido": avisa y deshabilita enlace y QR. Usa rutas relativas (`/api/v1/links`). Todo en español.
- **Extensión (Manifest V3, un solo código para Chrome y Firefox):** al abrir el popup toma la URL de la pestaña activa (`tabs.query`). Si es `http`/`https`, llama a `POST /api/v1/links` y muestra enlace, QR, hora de vencimiento y botón copiar, con el mismo estado "vencido" que la web. Si no lo es (`chrome://`, `about:`, nueva pestaña), muestra el botón **ACORTAR** deshabilitado y el aviso "Esta página no se puede acortar (solo http/https)". La URL de la API es una constante en `config.js` (hoy `http://localhost:8080`; al pasar al VPS hay que cambiarla y reempaquetar). Se instala solo en local, en modo desarrollador.

### 4.7 Calidad (QA)

- **Unitarias (JUnit 5 + Mockito):** generador de alias, validador de URL, política de expiración, servicios y cron (con `Clock` fijo).
- **Integración:** `@DataJpaTest` para el repositorio y `@SpringBootTest` + `MockMvc` para la API y la redirección.
- **Cobertura:** JaCoCo, con objetivo de ≥ 70 % en `domain` + `application` (a confirmar con el cliente).
- **Criterios de aceptación mínimos:**
  1. Una URL válida devuelve 201 con un alias único de 5 caracteres del alfabeto definido y `expiresAt = createdAt + 60 min`.
  2. Una URL inválida (`ftp://`, sin esquema, texto suelto, vacía, de más de 2048 caracteres) devuelve 400 con un mensaje claro. Una URL al propio dominio, a localhost o a una IP privada se acepta.
  3. `GET /{alias}` vigente devuelve 302 a la URL original.
  4. `GET /{alias}` a los 61 minutos devuelve 404, aunque la fila todavía no se haya borrado.
  5. Un alias inexistente devuelve 404, con la misma página que un alias vencido.
  6. Acortar dos veces la misma URL devuelve dos alias distintos.
  7. El QR decodificado (se verifica con ZXing en el test) es igual a `shortUrl`, y la descarga se llama `{alias}.png`.
  8. El cron borra los enlaces vencidos y no toca los vigentes.
  9. Ante una colisión de alias se genera otro; superado el tope de reintentos, responde 503.
  10. La API nunca devuelve entidades JPA.
  11. Tras reiniciar el servicio, los enlaces vigentes siguen funcionando.
  12. En la extensión, una pestaña no http/https muestra el botón deshabilitado con el aviso.

### 4.8 Entornos y despliegue

| | Local (esta entrega) | VPS (futuro) |
|---|---|---|
| URL | `http://localhost:8080` | `http://{ip_o_dominio_del_vps}` (a definir) |
| Configuración | `application.properties` | Mismo archivo; `app.base-url` sobreescrito por variable de entorno `APP_BASE_URL` (o perfil `vps`) |
| Base de datos | HSQLDB en modo archivo (`./data/shortener`) | Igual, en un directorio que sobreviva a los redeploys |
| HTTPS | No | No (por requerimiento del cliente) |
| Logs SQL / Swagger | Activados | A decidir con el grupo |
| Datos | Descartables | No se deben perder entre deploys |

Puntos técnicos a tener en cuenta:

- **`app.base-url`:** la `shortUrl` se arma siempre desde esta propiedad, no desde la petición. Cambiar de local a VPS no toca código.
- **CORS:** la extensión llama desde un origen `chrome-extension://...` o `moz-extension://...`. `CorsConfig` habilita `/api/**` para esos orígenes.
- **Validación de URL:** se aceptan URLs del propio dominio, localhost e IPs privadas (decisión del cliente).
- **Empaquetado:** `gradlew bootJar` genera un JAR único. La extensión se entrega como carpeta y `.zip`.
- **Cron:** requiere `@EnableScheduling`; la expresión se lee de `app.cleanup.cron`.

Preguntas para el equipo (no son para el cliente):

| # | Pregunta | Suposición por defecto |
|---|---|---|
| E1 | ¿Qué hay en el VPS (SO, Java 25 instalado, Docker)? ¿Quién tiene acceso y quién hace el deploy? | Linux con Java 25; el dueño del VPS hace el deploy |
| E2 | ¿Qué versión de Spring Boot y de Gradle soporta Java 25? | Verificar antes del Paso 0 |
| E3 | ¿El VPS usa dominio o IP? ¿Corre en el puerto 80 o en 8080? | IP o dominio sin HTTPS; puerto 8080 salvo que haya un proxy |
| E4 | ¿Los alias quedan en la raíz del dominio (`/xt3se`)? | Sí, dominio o IP dedicado al TP |
| E5 | ¿El deploy es manual o automático? | Manual con script, fuera del alcance de la Etapa 1 |

---

## 5. Anticipación de "volantazos" (etapas 2 y 3)

| Cambio probable | Dónde impacta | Qué hay que hacer |
|---|---|---|
| Alias personalizado elegido por el usuario | `AliasGenerator` + DTO | Campo opcional en el request, validación de disponibilidad y de palabras reservadas |
| TTL distinto por enlace, o enlaces permanentes | `ExpirationPolicy` + DTO | Nueva implementación y campo opcional en el request. `expiresAt` nulo = permanente (el cron solo borra los no nulos) |
| Extender o renovar un enlace | Nuevo caso de uso | Endpoint `PATCH` que actualiza `expiresAt` |
| Eliminar un enlace manualmente | Nuevo endpoint | `DELETE /api/v1/links/{alias}` |
| Usuarios / login / "mis enlaces" | Columna `ownerId` (nullable) en `ShortLink`, entidad `User` | Spring Security; los servicios actuales no cambian su lógica. Ojo: el cron borra los vencidos, así que un historial por usuario exige archivar en vez de borrar |
| Estadísticas de clics | Nueva entidad `ClickEvent`, se registra en `ResolveLinkService` | Un evento por redirección; endpoint de reportes. Definir qué pasa con los eventos cuando el cron borra el enlace |
| Expirar por cantidad de usos / enlace de un solo uso | `ExpirationPolicy` + contador en `ShortLink` | Sin tocar los controllers |
| Enlaces protegidos con contraseña | Campo en `ShortLink` y página intermedia | Cambio acotado en `ResolveLinkService` |
| Rate limiting / anti-abuso / lista negra de dominios | `UrlValidator` o un filtro web | Implementación nueva o decorador |
| Previsualizar el destino antes de redirigir | `RedirectController` | Página intermedia opcional |
| QR personalizado (colores, logo) o en SVG | `QrCodeGenerator` | Nueva implementación y parámetro en el endpoint |
| Dominio propio y HTTPS | `app.base-url` + reverse proxy | Configuración, sin código |
| Cambiar a MySQL/PostgreSQL | `application.properties` + driver | Nada de código (JPA) |
| Varias instancias del servicio | Cron y generación de alias | La clave primaria ya evita alias duplicados; el cron debería correr en una sola instancia (por ejemplo con un lock) |
| Otro cliente (app móvil, bot) | Ninguno | La API REST ya es el contrato |
| Autenticación de la API con tokens | Filtro de seguridad | Spring Security, sin tocar los servicios |
| Otros idiomas en la web | Cliente web | Archivo de textos por idioma |

---

## 6. Plan por pasos (Etapa 1)

Cada paso termina **compilando, con tests en verde y con su entrada en `docs/BITACORA.md`**. No se arranca el siguiente sin validar el anterior. La entrega de la Etapa 1 es el 12/10/2026; las fechas intermedias de cada paso se reparten entre los integrantes en el merge.

| Paso | Entregable | Cómo se verifica |
|---|---|---|
| 0 | Proyecto base: Gradle, dependencias, paquetes, `AppProperties`, `Clock`, README, `BITACORA.md` | `gradlew bootRun` levanta y el test de contexto pasa |
| 1 | Dominio + persistencia: `ShortLink`, puerto `ShortLinkRepository`, implementación JPA | `@DataJpaTest` de guardar, buscar por alias y borrar vencidos |
| 2 | Validación de URL + generación de alias + política de expiración | Tests unitarios de cada estrategia |
| 3 | Caso de uso de acortamiento + `POST /api/v1/links` + manejo de errores + Swagger | Tests MockMvc 201/400/503 |
| 4 | Redirección `GET /{alias}` (302/404) y página "expiró o no existe" | Test con `Clock` adelantado 61 min y misma página para vencido e inexistente |
| 5 | Cron de borrado de enlaces vencidos | Test que verifica que borra vencidos y no vigentes |
| 6 | QR (ZXing) `GET /api/v1/links/{alias}/qr` y descarga como `{alias}.png` | Test que decodifica el PNG |
| 7 | Cliente web | Prueba manual + capturas, incluido el estado "vencido" |
| 8 | Extensión Chrome/Firefox + CORS | Carga en modo desarrollador en ambos navegadores, y botón deshabilitado en pestañas no http/https |
| 9 | QA final: JaCoCo, revisión de logs, README, guion de demo en local | Reporte de cobertura + checklist de criterios de la sección 4.7 |
| — | **Entrega (12/10/2026)** | |

---

## 7. Formato de documentación por paso (`docs/BITACORA.md`)

Se pide explícitamente porque el cliente evalúa las decisiones de diseño y exige el registro de prompts. Cada paso agrega una entrada con esta plantilla:

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

### Prompts utilizados (registro de IA)
Prompt enviado, resumen de la respuesta, qué se validó o corrigió manualmente.
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
- Proyecto de referencia del profesor: Gradle, HSQLDB, servicios con EntityManager +
  HQL/JPQL. Respetar ese estilo. Versión de Java: 25 (confirmada). Usar una versión de
  Spring Boot y de Gradle compatible con Java 25.
- Entorno: LOCAL (http://localhost:8080) en esta entrega; luego un VPS sin HTTPS.
  Todo lo que cambie entre entornos va en configuración (app.base-url), nunca en código.
- Alcance: hacé lo mínimo que pide el enunciado y lo definido en el diseño. No agregues
  funcionalidades no pedidas.

REQUISITO NO FUNCIONAL CLAVE
El cliente (docente) va a cambiar requerimientos en etapas 2 y 3 sin aviso. El código
debe extenderse agregando clases, no reescribiendo: aplicar SOLID, Strategy para
AliasGenerator, ExpirationPolicy y UrlValidator, puertos/adaptadores para persistencia y
QR, DTOs, Clock inyectable, configuración vía @ConfigurationProperties.

REGLAS DE TRABAJO
1. Hacé SOLO el paso que te pida. No adelantes pasos.
2. Antes de escribir código, listá en 5-10 líneas qué vas a crear/modificar y por qué.
3. Entregá archivos completos (no fragmentos con "..."), con la ruta de cada uno.
4. Cada paso incluye sus tests (JUnit 5, Mockito, @DataJpaTest, MockMvc según corresponda)
   y debe compilar con `gradlew build`.
5. Al final de cada paso generá la entrada de docs/BITACORA.md con esta plantilla:
   [pegar plantilla de la sección 7]
   Incluí siempre: estado ANTERIOR, qué es NUEVO, qué se MODIFICÓ (antes/después),
   explicación simple, decisiones de diseño, cómo probarlo, preguntas probables del
   profesor con su respuesta y el registro de prompts utilizados.
6. Si una regla de negocio es ambigua, NO inventes: usá la decisión o suposición de las
   tablas 3.2 y 3.3, dejala configurable y mencionala en la bitácora.
7. Comentarios en el código solo cuando expliquen una restricción no obvia. Nombres en
   inglés para código, documentación y textos de interfaz en español.
8. Terminá cada respuesta con: "Paso N completo. ¿Avanzo al paso N+1?" y esperá mi OK.

Confirmá que entendiste resumiendo la arquitectura en 10 líneas. No escribas código todavía.
```

### 8.2 Prompts de cada paso

```text
PASO 0 – Proyecto base
Creá el proyecto Gradle (Java 25, versión de Spring Boot compatible) con:
spring-boot-starter-web, data-jpa, validation, hsqldb,
springdoc-openapi-starter-webmvc-ui, com.google.zxing:core y javase,
spring-boot-starter-test, jacoco. Estructura de paquetes de la sección 4.2 (vacía salvo
lo necesario). Un único application.properties con: app.base-url=http://localhost:8080,
app.link.ttl=60m, app.alias.length=5, app.alias.alphabet (31 caracteres sin ambiguos),
app.alias.reserved (lista de la decisión D2), app.alias.max-attempts=10,
app.cleanup.cron (diario), HSQLDB en modo archivo ./data/shortener, show-sql activado.
Documentá en el README cómo sobreescribir app.base-url con la variable APP_BASE_URL para
el VPS. Clase AppProperties (@ConfigurationProperties + @Validated), bean Clock
(Clock.systemUTC()). Test de carga de contexto. README con cómo compilar y ejecutar.
.gitignore que excluya ./data y archivos .env. Crear docs/BITACORA.md con la entrada del
Paso 0.
```

```text
PASO 1 – Dominio y persistencia
Entidad ShortLink (alias como clave primaria, originalUrl, createdAt, expiresAt con
índice) con método isExpired(Instant now). Puerto ShortLinkRepository (save,
findByAlias, existsByAlias, deleteExpiredBefore(Instant) que devuelve la cantidad
borrada). Implementación JpaShortLinkRepository con EntityManager + JPQL parametrizado
(estilo del profesor). Tests @DataJpaTest. Bitácora del Paso 1.
```

```text
PASO 2 – Validación, alias y expiración
Interfaces UrlValidator, AliasGenerator, ExpirationPolicy en domain/port.
Implementaciones: RegexUrlValidator (solo http/https con host, <=2048 caracteres,
rechaza URLs sin esquema; ACEPTA URLs del propio dominio, localhost e IPs privadas),
RandomAliasGenerator (SecureRandom, largo, alfabeto y palabras reservadas desde config),
FixedTtlExpirationPolicy (usa Clock + TTL de config). Tests unitarios de cada una.
Explicá en la bitácora cómo se agregaría otra estrategia (por ejemplo alias
personalizado) sin tocar código existente.
```

```text
PASO 3 – Caso de uso de acortamiento + API REST
ShortenLinkService (transaccional): valida la URL, genera el alias reintentando ante
colisión (máx. app.alias.max-attempts, luego AliasExhaustedException), descarta el alias
si la shortUrl resultante es igual a la URL destino, y contempla
DataIntegrityViolationException por concurrencia. Cada acortamiento genera un alias
nuevo, aunque la URL ya se haya acortado antes. LinkApiController: POST /api/v1/links.
DTOs ShortenRequest (con @Valid) y ShortLinkResponse (incluye secondsRemaining y qrUrl).
GlobalExceptionHandler con ProblemDetail (400/503). Swagger UI habilitado.
Tests unitarios del servicio (Clock fijo) y MockMvc del controller. Bitácora del Paso 3.
```

```text
PASO 4 – Redirección y página de enlace no disponible
RedirectController GET /{alias:[A-Za-z0-9]{1,16}} -> 302 a originalUrl si el alias
existe y no venció (comparando expiresAt con el Clock). Si no existe o venció: 404 con la
página estática enlace-no-disponible.html ("Este enlace expiró o no existe" y un botón
para crear un enlace nuevo), SIN distinguir entre ambos casos. Justificar 302 vs 301 en
la bitácora. Tests MockMvc, incluido el caso "Clock adelantado 61 minutos" con la fila
todavía presente.
```

```text
PASO 5 – Limpieza de enlaces vencidos
ExpiredLinksCleanupService y ExpiredLinksCleanupJob con @Scheduled(cron =
"${app.cleanup.cron}") que borra los enlaces con expiresAt anterior a la hora actual
(Clock) y registra con SLF4J la cantidad borrada. Habilitar @EnableScheduling.
Tests: borra vencidos, no borra vigentes. Explicá en la bitácora por qué la corrección
del vencimiento no depende del cron. Bitácora del Paso 5.
```

```text
PASO 6 – Código QR
Puerto QrCodeGenerator (byte[] generatePng(String content, int size)) e implementación
ZxingQrCodeGenerator. Endpoint GET /api/v1/links/{alias}/qr?size=256&download=false
(image/png, size acotado a 128..1024). Con download=true agregar
Content-Disposition: attachment; filename="{alias}.png". 404 si el alias no existe o
venció. Test que decodifica el PNG con ZXing y verifica que contiene la shortUrl.
Bitácora del Paso 6.
```

```text
PASO 7 – Cliente web
static/index.html, app.js, styles.css sin frameworks y en español: campo "Dirección a
acortar", botón ACORTAR, y resultado con shortUrl (link + botón copiar), QR (img al
endpoint qrUrl, botón descargar como {alias}.png) y hora de vencimiento en hora local.
Muestra solo el último enlace generado. Un setTimeout calculado con secondsRemaining
cambia el estado a "vencido": avisa que el enlace venció y deshabilita enlace y QR.
Manejo visible de errores 400/503. Bitácora con capturas sugeridas y explicación del
flujo fetch -> API -> render.
```

```text
PASO 8 – Extensión Chrome y Firefox
Carpeta browser-extension/ con Manifest V3 compatible con ambos navegadores (incluir
browser_specific_settings.gecko para Firefox). El popup, al abrirse, toma la URL de la
pestaña activa. Si es http/https llama a POST /api/v1/links y muestra enlace, QR, hora de
vencimiento y botón copiar, con el mismo estado "vencido" que la web. Si no es http/https
muestra el botón ACORTAR deshabilitado con el aviso "Esta página no se puede acortar
(solo http/https)". La URL de la API es una constante en config.js (http://localhost:8080),
sin página de opciones. host_permissions para ambos. CorsConfig en el backend para
/api/** que acepte orígenes chrome-extension:// y moz-extension://. Bitácora con
instrucciones para cargarla en modo desarrollador en Chrome y de forma temporal en
Firefox, y para empaquetarla en .zip.
```

```text
PASO 9 – QA y cierre de la Etapa 1
Configurar JaCoCo (reporte HTML, umbral 70% en domain+application). Revisar el logging
SLF4J (creación, redirección, 404, cron). Revisar que la API no exponga entidades.
Checklist de los criterios de aceptación de la sección 4.7 con el test que cubre cada
uno, y verificación de que los enlaces sobreviven a un reinicio. README final y guion de
demo de 5 minutos en local (web, extensión, QR, vencimiento). Resumen en la bitácora:
arquitectura final y tabla "volantazo probable -> dónde se implementaría".
```

### 8.3 Prompt para cuando llegue un "volantazo" (etapas 2 y 3)

```text
El cliente cambió/agregó estos requerimientos: [pegar].
Antes de programar:
1. Analizá el impacto: qué clases se agregan, cuáles se modifican y por qué.
   Si hay que modificar mucho código existente, señalalo como deuda de diseño.
2. Revisá si alguna decisión de la etapa anterior (tabla 3.2) se ve afectada y
   marcala como pregunta para consultar de nuevo al cliente.
3. Proponé preguntas de elucidación para el cliente y suposiciones por defecto.
4. Dividí el cambio en pasos numerados (continuando la numeración de la bitácora).
Esperá mi OK antes de implementar. Luego seguí las mismas reglas del prompt maestro,
incluido el registro de prompts en la bitácora.
```

---

## 9. Puntos para discutir con mis compañeros en el merge

1. **Repository:** ¿`EntityManager` detrás de una interfaz (mi propuesta, alineada al profesor) o Spring Data JPA?
2. **Base de datos:** ¿HSQLDB en modo archivo (mi propuesta) o en modo servidor como el ejemplo?
3. **QR:** ¿en el backend (mi propuesta, una sola implementación y descarga con el nombre del alias) o en el cliente con una librería JS?
4. **Enlaces vencidos:** ¿borrar con un cron (mi propuesta, 404 único) o marcar `EXPIRED` y conservar el historial?
5. **Frontend:** ¿JS sin framework (mi propuesta) o React/Vue?
6. **Versión de Spring Boot y de Gradle:** Java 25 está confirmado; falta definir qué versión de Spring Boot y de Gradle lo soporta.
7. **Perfiles de Spring:** ¿alcanza con `app.base-url` por variable de entorno o armamos un perfil `vps`?
8. **Reparto de pasos** entre los integrantes, y quién consolida la bitácora y el registro de prompts.
9. **Cobertura mínima de tests** y **frecuencia del cron**: valores por defecto a acordar.
10. **VPS:** responder las preguntas E1 a E5 de la sección 4.8.
