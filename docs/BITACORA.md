# Bitácora – TP PP6 Link Shortener

## Paso 0 – Proyecto base (05/10/2026)

> **Superado por "Paso 0 (revisión)" del 09/10/2026** (al final de este archivo). Esta entrada se conserva como historia: se escribió antes de conocer las respuestas del Cliente y la consolidación de `contexto.md`.

### Objetivo
Dejar un proyecto Spring Boot que compila, arranca y tiene la estructura y la configuración sobre la que se construyen los pasos siguientes. Cubre la base técnica del enunciado (Java + Spring Boot + JPA/Hibernate) y los dos entornos pedidos: staging (local) y producción (`https://paradigmas6.agustingimenez.ar`).

### Estado ANTERIOR
El repositorio solo tenía documentación (`README.md`, `TP_PP6_v1.0.md`, `propuesta-etapa1.md`). No había código.

### Qué es NUEVO
| Archivo | Acción | Para qué sirve |
|---|---|---|
| `settings.gradle`, `build.gradle` | nuevo | Proyecto Gradle: Spring Boot 3.4.3, Java 21, web, data-jpa, validation, HSQLDB, springdoc (Swagger), ZXing (QR), JaCoCo |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | nuevo | Gradle wrapper (8.14.3): nadie necesita instalar Gradle |
| `ShortenerApplication.java` | nuevo | Punto de entrada; `@ConfigurationPropertiesScan` registra `AppProperties` |
| `config/AppProperties.java` | nuevo | Configuración tipada y validada (`app.base-url`, `app.link.ttl`, `app.alias.*`) |
| `config/ClockConfig.java` | nuevo | Bean `Clock` (UTC) para inyectar el tiempo |
| `application.properties` | nuevo | Valores comunes y perfil por defecto `staging` |
| `application-staging.properties` | nuevo | Local: `localhost:8080`, HSQLDB en archivo, SQL visible |
| `application-prod.properties` | nuevo | Producción: dominio HTTPS, base por variables de entorno, detrás de proxy |
| `static/index.html` | nuevo | Página inicial provisoria (el cliente web real es el Paso 6) |
| `*/package-info.java` | nuevo | Paquetes vacíos de la arquitectura (sección 4.2 de la propuesta) |
| `ShortenerApplicationTests.java` | nuevo | Test de carga de contexto y de la configuración |
| `.env.example` | nuevo | Plantilla de variables de prod, sin valores reales |
| `.gitignore`, `README.md` | modificado | Ignorar `data/` y `.env`; instrucciones de compilación y ejecución |

### Qué se MODIFICÓ y por qué
- `.gitignore`: se agregó `.env` (secretos de prod) y `data/` (base HSQLDB local, descartable).
- `README.md`: se agregó la sección "Compilar y ejecutar".

### Cómo funciona (explicado simple)
1. `./gradlew bootRun` arranca la app. Como no se indica perfil, Spring usa `spring.profiles.default=staging`.
2. Spring carga primero `application.properties` (lo común) y después `application-staging.properties`, que pisa o completa valores.
3. Los valores `app.*` se copian a `AppProperties`. Si falta uno obligatorio o es inválido (por ejemplo, `app.alias.length=0`), la app **no arranca** y dice qué propiedad está mal.
4. En el servidor se arranca con `SPRING_PROFILES_ACTIVE=prod`: el mismo JAR toma `application-prod.properties` y lee la base de datos de las variables `DB_URL`, `DB_USER` y `DB_PASSWORD`.

**¿Qué es un perfil de Spring?** Un nombre (`staging`, `prod`) que activa un archivo `application-<perfil>.properties` extra. Permite tener **un solo código** y cambiar solo la configuración según dónde corre.

### Decisiones de diseño
- **Perfiles en lugar de ramas o `if` en el código:** separar entornos por configuración evita "funciona en mi máquina". El código no sabe en qué entorno está.
- **Secretos fuera del repo:** en prod, las credenciales llegan por variables de entorno (`${DB_URL}`). `.env.example` documenta cuáles hacen falta.
- **`@ConfigurationProperties` + `@Validated` (records):** configuración tipada (`Duration` para el TTL), validada al arrancar, y un único lugar para leerla. Se descartó `@Value` disperso por las clases.
- **`Clock` inyectable:** los tests podrán simular "pasaron 61 minutos" sin esperar (se usa desde el Paso 2).
- **HSQLDB en modo archivo en staging:** cualquiera del grupo lo levanta sin instalar un servidor de base de datos.
- **Alfabeto sin caracteres ambiguos** (`0 O o 1 l I`): suposición por defecto de la pregunta P1, configurable.
- **`server.forward-headers-strategy=framework` en prod:** detrás de Nginx, Spring reconoce que la petición original fue HTTPS.
- No se incluyó todavía `jpql-console-starter` del ejemplo del profesor: se agrega cuando haya entidades (Paso 1), con las coordenadas del proyecto del profesor.

### Cómo probarlo
```bash
./gradlew build                 # compila y corre el test de contexto
./gradlew bootRun               # staging en http://localhost:8080
curl -i http://localhost:8080/  # 200 con la página inicial
```
Test: `ShortenerApplicationTests.contextLoadsWithStagingDefaults` (usa HSQLDB en memoria para no ensuciar `./data`).

### Preguntas probables del profesor (con respuesta)
- **¿Por qué dos perfiles y no un solo `application.properties`?** → Porque staging y prod difieren en URL, base de datos y logs. Con perfiles, el código es el mismo y solo cambia la configuración.
- **¿Dónde está la contraseña de la base de prod?** → En ningún archivo del repo: es una variable de entorno del servidor.
- **¿Qué pasa si alguien configura mal el TTL?** → `@Validated` hace fallar el arranque con un mensaje claro, en lugar de fallar más tarde en runtime.
- **¿Por qué inyectar `Clock` en lugar de usar `Instant.now()`?** → Para poder testear la expiración controlando el tiempo.

### Preparado para cambios
- Cambiar TTL, largo o alfabeto del alias: solo se edita `application.properties`.
- Cambiar de base (PostgreSQL/MySQL): driver en `build.gradle` y variables `DB_*`, sin tocar código.
- Agregar otro entorno (por ejemplo `test` o `demo`): un `application-<perfil>.properties` nuevo.

---

## Paso 0 (revisión) – Alineación con `contexto.md` (09/10/2026)

**Estado:** completo en local, pendiente **A** (aceptación de un integrante que no construyó el paso: Sofía, como Probadora).

### Objetivo
Adaptar el Paso 0 del 05/10 a `contexto.md` v0.1 (07/10), que consolidó las tres propuestas y las respuestas del Cliente. El Paso 0 original se había hecho antes de esas respuestas y contradecía varias de ellas. Cubre el Paso 0 de `contexto.md` §16 y §18.4.

### Estado ANTERIOR
Proyecto que compilaba y arrancaba, pero desalineado con `contexto.md`:

| Tema | Antes (05/10) | Qué decía `contexto.md` |
|---|---|---|
| Java / Spring Boot / Gradle | 21 / 3.4.3 / 8.14.3 | Java 25 (C16), con Spring Boot y Gradle compatibles (R11) |
| Configuración | Perfiles `staging` y `prod`, prod con HTTPS en `paradigmas6.agustingimenez.ar` | Un solo `application.properties` + `APP_BASE_URL` (D34); entrega local (C12), sin HTTPS (C14) |
| Paquete raíz | `com.pp6.shortener` | Recomendación `ar.edu.undef.fie.pp6.shortener` (E6) |
| Alfabeto del alias | 56 caracteres, mayúsculas y minúsculas | 31: minúsculas y dígitos sin ambiguos (D8) |
| Propiedades | Faltaban `reserved`, `max-attempts`, `cleanup.cron` | §12.2 |
| Paquete del cron | No existía | `infrastructure/scheduling` (§8.2) |
| Consola HQL del profesor | No incluida | Incluida (D37) |
| ADR | No había | ADR-0001 a 0006 (§14.3) |
| README | Indicaba `git add .` y Java 21 | Nunca `git add .` (R18, §19.2) |

### Qué es NUEVO
| Archivo | Acción | Para qué sirve |
|---|---|---|
| `docs/adr/0001` a `0006` | nuevo | Decisiones difíciles de revertir, con contexto, alternativas y consecuencias |
| `infrastructure/scheduling/package-info.java` | nuevo | Paquete donde va a vivir la tarea de limpieza (Paso 5) |
| `build.gradle` | modificado | Java 25, Spring Boot 4.1.1, starters de test de Boot 4, springdoc 3.1.1, ZXing 3.5.4, consola HQL, JaCoCo 0.8.15 |
| `settings.gradle` | modificado | Plugin `foojay-resolver-convention`: si alguien no tiene JDK 25, Gradle lo descarga |
| `gradlew.bat`, `gradle/wrapper/*` | modificado | Wrapper actualizado a Gradle 9.8.1, la última versión (Java 25 está soportado desde Gradle 9.1) |
| `application.properties` | modificado | Único archivo con los valores de §12.2 |
| `application-staging.properties`, `application-prod.properties` | eliminado | Ya no hay perfiles (D34) |
| `AppProperties.java` | modificado | Agrega `alias.reserved`, `alias.maxAttempts` y `cleanup.cron` |
| Todos los `.java` | movido | De `com.pp6.shortener` a `ar.edu.undef.fie.pp6.shortener` (con `git mv`, se conserva el historial) |
| `ShortenerApplicationTests.java` | modificado | Verifica los valores de §12.2 y que la app no arranque sin `app.base-url` (D33) |
| `.env.example` | modificado | Solo documenta `APP_BASE_URL` |
| `README.md` | modificado | Java 25, configuración única, aviso sobre `java -jar`, regla de `git add` |

### Qué se MODIFICÓ y por qué

**`application.properties`** (antes repartido en 3 archivos):
```properties
# ANTES (application.properties + perfiles)
spring.profiles.default=staging
app.alias.alphabet=23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz
# application-staging.properties: app.base-url=http://localhost:8080
# application-prod.properties:    app.base-url=https://paradigmas6.agustingimenez.ar

# DESPUÉS (un solo archivo)
app.base-url=${APP_BASE_URL:http://localhost:8080}
app.alias.alphabet=abcdefghjkmnpqrstuvwxyz23456789
app.alias.reserved=api,admin,static,assets,error,expired,health,actuator,swagger,favicon.ico,index
app.alias.max-attempts=10
app.cleanup.cron=0 0 3 * * *
```
Por qué: el Cliente dijo que esta entrega es local (C12) y sin HTTPS (C14). Con un solo entorno, los perfiles eran complejidad sin uso. `${APP_BASE_URL:http://localhost:8080}` significa "usá la variable de entorno si existe; si no, localhost".

**`AppProperties.java`:**
```java
// ANTES
public record Alias(@Min(1) @Max(16) int length, @NotBlank String alphabet) {}

// DESPUÉS
public record Alias(
		@Min(1) @Max(16) int length,
		@NotBlank String alphabet,
		@NotNull List<String> reserved,
		@Min(1) int maxAttempts) {}
public record Cleanup(@NotBlank String cron) {}
```

**`build.gradle`:** `spring-boot-starter-web` pasó a `spring-boot-starter-webmvc`, que es el nombre en Spring Boot 4. Se agregaron `spring-boot-starter-webmvc-test` y `spring-boot-starter-data-jpa-test`, porque en Boot 4 MockMvc y `@DataJpaTest` vienen en módulos separados (se usan en los Pasos 1 y 3).

### Cómo funciona (explicado simple)
1. `gradlew bootRun` compila con Java 25 (si no está instalado, Gradle lo baja) y arranca la app.
2. Spring lee `application.properties` y copia los valores `app.*` al record `AppProperties`.
3. `@Validated` revisa las anotaciones: si `app.base-url` está vacío, o el largo del alias es 0, la app **no arranca** y dice qué propiedad está mal.
4. Para correr en el VPS, el mismo JAR se arranca con `APP_BASE_URL=http://...`, y esa variable pisa el valor por defecto. No se toca código ni se recompila.

### Decisiones de diseño
- **Un solo archivo + variable de entorno en lugar de perfiles** (D34, ADR-0005): hoy hay un solo entorno. Si el despliegue en el VPS necesita más diferencias, se agrega un perfil `vps` (R10).
- **Spring Boot 4.1.1 en lugar de 3.5.x:** la última versión estable que soporta Java 25; la línea 3.5 ya está fuera de soporte OSS.
- **Alfabeto de 31 caracteres** (D8): sin `0 o 1 l i`, para que el alias se pueda dictar o copiar a mano sin confusiones (C2). 31⁵ ≈ 28,6 millones de combinaciones.
- **Palabras reservadas desde ya** (D10): con largo 5 y este alfabeto ninguna es generable, pero la regla protege ante un cambio de largo o ante el alias personalizado.
- **Consola HQL del profesor** (D37): se verificó que el starter `v1.1.0` arranca con Spring Boot 4.

### Cómo probarlo
```bash
gradlew.bat build          # compila con Java 25 y corre los tests
gradlew.bat bootRun        # http://localhost:8080
curl -i http://localhost:8080/                       # 200
curl -i http://localhost:8080/v3/api-docs            # 200 (OpenAPI)
curl -i http://localhost:8080/swagger-ui/index.html  # 200
```
Tests:
- `contextLoadsWithDefaultConfiguration`: los valores de §12.2 y el `Clock` en UTC.
- `failsToStartWithoutBaseUrl`: con `app.base-url=` vacío, el contexto falla (D33).

### Evidencias de cierre
| Código | Evidencia |
|---|---|
| **T** | `gradlew clean build` → `BUILD SUCCESSFUL`, 2 tests, 0 fallos (Gradle 9.8.1, JVM 25) |
| **M** | Mutación: se quitó `@NotBlank` de `baseUrl` → `failsToStartWithoutBaseUrl() FAILED`. Se restauró desde una copia → verde |
| **E2E** | No aplica (todavía no hay cliente web) |
| **V** | `java -jar shortener.jar` con JDK 25: arranca en 5,8 s; `/`, `/v3/api-docs` y `/swagger-ui/index.html` responden 200; se crea `./data` (ignorado por git) |
| **A** | ⛔ Pendiente: aceptación de Sofía |
| **D** | Esta entrada + ADR-0001 a 0006 |

**Hallazgo:** con `java -jar` falla con `UnsupportedClassVersionError` si el `java` del PATH es 21 (pasa en Windows con Oracle `javapath` primero en el PATH, aunque `JAVA_HOME` apunte al 25). Queda documentado en el README.

### Preguntas probables del profesor (con respuesta)
- **¿Por qué Spring Boot 4 si su ejemplo usa 3.4.3?** → Porque usted confirmó Java 25 (C16) y Spring Boot 3.4.3 no lo soporta. 4.1.1 es la última versión estable que sí lo hace.
- **¿Por qué no hay perfiles de entorno?** → Porque esta entrega es solo local (C12). La única diferencia con un VPS futuro es el dominio, y eso se resuelve con la variable `APP_BASE_URL` sin tocar código.
- **¿Por qué el alfabeto no tiene `0`, `o`, `1`, `l` ni `i`?** → Usted pidió un alias fácil de recordar (C2). Esos caracteres se confunden al dictarlos o copiarlos a mano.
- **¿Qué es un ADR?** → Un registro corto de una decisión difícil de revertir: contexto, decisión, alternativas descartadas y consecuencias. Si la decisión cambia, se escribe un ADR nuevo que reemplaza al anterior, sin borrar el viejo.
- **¿Cómo sabe que el test de `base-url` sirve?** → Se rompió la regla a propósito (se quitó `@NotBlank`) y el test falló. Un test que nunca puede fallar no protege nada.

### Preparado para cambios
- Desplegar en el VPS: `APP_BASE_URL` por variable de entorno, sin recompilar.
- Cambiar TTL, largo, alfabeto, palabras reservadas, intentos o frecuencia del cron: solo `application.properties`.
- Alias personalizado (futuro): las palabras reservadas ya existen en la configuración.

### Prompts utilizados (registro de IA)
| Prompt | Resumen de la respuesta | Qué se validó o corrigió |
|---|---|---|
| "¿Coinciden los diagramas con lo planteado por el profesor? ¿Qué hay de nuevo en el repo?" | Los diagramas cubren la consigna y `contexto.md`; se detectaron 2 observaciones en el diagrama del QR (`shortUrl` armada en dos lugares, QR dentro de `ResolveLinkService`) y que el Paso 0 de `dev/agustin` contradecía `contexto.md` | Se revisó el HTML de los diagramas y los archivos del Paso 0 contra `contexto.md` |
| "Adaptar mi Paso 0 a `contexto.md`" + elección del paquete `ar.edu.undef.fie.pp6.shortener` (E6) | Migración a Java 25 / Boot 4.1.1 / Gradle 9.8.1, configuración única, ADR, README y esta entrada | Build y tests corridos de verdad, app levantada con JDK 25, mutación del test de `base-url` |

---

## Despliegue anticipado en el VPS (09/10/2026)

**Estado:** completo en el VPS (`https://paradigmas6.agustingimenez.ar`). Adelanta **BL-007**: el despliegue no es obligatorio en la Etapa 1 (C12), pero hacerlo temprano detecta problemas de infraestructura antes de la entrega.

### Objetivo
Que el Paso 0 revisado corra en el servidor de Agustín (responsable del despliegue según `contexto.md` §19.1), con el mismo JAR que en local y sin tocar código.

### Estado ANTERIOR
El VPS ya corría el **Paso 0 del 05/10** (Java 21, perfil `prod`):

| Pieza | Antes |
|---|---|
| Nginx | `paradigmas6.agustingimenez.ar`: HTTP → HTTPS y proxy a `127.0.0.1:8080` (en `/etc/nginx/sites-available/fie-materias.conf`), detrás de Cloudflare |
| Servicio | `pp6-shortener.service` (systemd, usuario `pp6`, `WorkingDirectory=/var/lib/pp6-shortener`) con `/opt/jdk-21` |
| Variables | `/etc/pp6-shortener/shortener.env` con `SPRING_PROFILES_ACTIVE=prod`, `SERVER_ADDRESS`, `PORT`, `DB_URL`, `DB_USER`, `DB_PASSWORD` |
| JAR | `/opt/pp6-shortener/shortener.jar` (Paso 0 viejo) |

### Qué es NUEVO / qué se MODIFICÓ
| Pieza | Acción | Detalle |
|---|---|---|
| `/opt/jdk-25` | nuevo | Temurin 25.0.4.1 (`/opt/jdk-21` se conserva) |
| `pp6-shortener.service` | modificado | `ExecStart` usa `/opt/jdk-25/bin/java` (mismos límites de memoria: `-Xmx256m`, SerialGC) |
| `shortener.env` | modificado | Ahora solo: `APP_BASE_URL=https://paradigmas6.agustingimenez.ar`, `SERVER_ADDRESS=127.0.0.1`, `SPRING_JPA_SHOW_SQL=false`, `HQLCONSOLE_ENABLED=false` |
| `shortener.jar` | modificado | JAR del commit `8ce2e8d` (SHA-256 `e4a932ad…6173047`, verificado antes y después de copiar) |
| Backups | nuevo | `*.bak-20261009-081003` junto al JAR, al `.env` y al `.service` |

Nginx no se tocó: ya hacía de reverse proxy con HTTPS.

### Cómo funciona (explicado simple)
1. Cloudflare recibe `https://paradigmas6.agustingimenez.ar` y lo manda al VPS.
2. Nginx termina el HTTPS y reenvía la petición a `127.0.0.1:8080`.
3. Spring Boot escucha **solo en 127.0.0.1** (`SERVER_ADDRESS`): desde internet no se puede llegar al 8080 salteando Nginx.
4. La URL corta se arma desde `APP_BASE_URL` (I4), no desde los headers que pone el proxy.
5. La base HSQLDB queda en `/var/lib/pp6-shortener/data` (ruta relativa `./data` + `WorkingDirectory`), fuera de la carpeta del JAR: sobrevive a los redeploys (C13).

### Decisiones de diseño
- **Mismo JAR que en local, distinta configuración por variables de entorno** (D34): el código no sabe dónde corre.
- **Variables de Spring por entorno** (relaxed binding): `SPRING_JPA_SHOW_SQL=false` pisa `spring.jpa.show-sql`, y `HQLCONSOLE_ENABLED=false` pisa `hql-console.enabled`. La consola HQL es una herramienta para la defensa en local.
- **HTTPS en el VPS:** `contexto.md` dice "sin HTTPS" (C14) porque el Cliente no lo exige, no porque lo prohíba. El servidor ya lo tenía resuelto en Nginx, así que se mantiene. ⚠️ Hay que avisarle al grupo para actualizar §13 y E3.

### Cómo probarlo
```bash
curl -I https://paradigmas6.agustingimenez.ar/                       # 200
curl -I https://paradigmas6.agustingimenez.ar/v3/api-docs            # 200
ssh VPS-DonWeb "systemctl status pp6-shortener"                      # active (running)
ssh VPS-DonWeb "journalctl -u pp6-shortener -n 50 --no-pager"        # logs
```

**Volver atrás:** restaurar los tres `*.bak-20261009-081003`, `systemctl daemon-reload` y `systemctl restart pp6-shortener`.

### Evidencias de cierre
| Código | Evidencia |
|---|---|
| **V** | Servicio `active` con `/opt/jdk-25`; log `Started ShortenerApplication in 8.71 seconds`; ~228 MB de RSS; `curl` local al 8080 → 200; desde internet `/`, `/v3/api-docs` y `/swagger-ui/index.html` → 200 por HTTPS |
| **A** | ⛔ Pendiente: verificación de otro integrante |

### Preguntas probables del profesor (con respuesta)
- **¿Qué cambió en el código para desplegar?** → Nada. El mismo JAR corre en local y en el VPS; solo cambian las variables de entorno.
- **¿Por qué el 8080 no está expuesto a internet?** → Porque la app escucha solo en `127.0.0.1`. Todo entra por Nginx, que maneja el HTTPS.
- **¿Se pierden los enlaces al redeployar?** → No: la base está en `/var/lib/pp6-shortener/data`, separada del JAR.

### Preparado para cambios
- Redeploy: copiar el JAR nuevo y `systemctl restart pp6-shortener`. Candidato a script cuando haya funcionalidad para desplegar seguido (E5).
- Cambiar a PostgreSQL (ya instalado en el VPS): `SPRING_DATASOURCE_URL` y driver, sin tocar código.

### Prompts utilizados (registro de IA)
| Prompt | Resumen de la respuesta | Qué se validó o corrigió |
|---|---|---|
| "¿Podés actualizarlo en el servidor paradigmas6.agustingimenez.ar?" | Inspección de solo lectura del VPS, plan con backups, instalación de JDK 25, ajuste del servicio y de las variables, despliegue del JAR | Hash del JAR, log de arranque, `curl` local y público |
