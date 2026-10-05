# Bitácora – TP PP6 Link Shortener

## Paso 0 – Proyecto base (05/10/2026)

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
