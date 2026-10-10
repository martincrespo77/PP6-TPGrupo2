# ADR-0005: HSQLDB en modo archivo, cambiable por configuración

- **Estado:** Aceptada
- **Fecha:** 09/10/2026
- **Referencias:** `contexto.md` D29, D31, D34, C12, C13, R8, R10

## Contexto
Los enlaces deben sobrevivir a un reinicio (C13), la entrega es local (C12) y el proyecto de referencia del profesor usa HSQLDB.

## Decisión
- HSQLDB en modo archivo (`jdbc:hsqldb:file:./data/shortener`), sin servidor aparte. `./data` está ignorado por git.
- Acceso con `EntityManager` + JPQL parametrizado detrás del puerto `ShortLinkRepository` (🟡 R8).
- Un único `application.properties`; lo que cambia entre máquinas (`app.base-url`) se sobrescribe con la variable de entorno `APP_BASE_URL` (🟡 R10).
- Tipos SQL estándar, para que cambiar de motor sea solo configuración y driver.

## Alternativas descartadas
- **HSQLDB en modo servidor** (como el ejemplo del profesor): obliga a cada integrante a levantar un servidor aparte.
- **Base en memoria:** pierde los enlaces al reiniciar (incumple C13).
- **Perfiles `staging`/`prod`** (primer Paso 0 de Agustín): en esta entrega hay un solo entorno (C12).

## Consecuencias
- Cualquiera del grupo clona y corre `gradlew bootRun` sin instalar nada más.
- Pasar a PostgreSQL o a un VPS no toca código.
