# ADR-0007: Estructura de paquetes del proyecto de referencia del profesor

- **Estado:** Propuesta (🟡 a confirmar en grupo). Implementada en la rama `dev/sofia`
- **Fecha:** 11/10/2026
- **Referencias:** `contexto.md` D47, D31, D33, D9, §8, §14.1, R8, R16, C19, C20
- **Reemplaza:** la arquitectura hexagonal de `contexto.md` v0.1 (§8) y el punto "detrás del puerto `ShortLinkRepository`" de ADR-0005

## Contexto
La v0.1 organizó el backend como arquitectura hexagonal: `domain` (entidad y puertos), `application`, `infrastructure` y `web`, con una interfaz delante de cada punto que podía cambiar (alias, vencimiento, validación, QR, persistencia). Eran 46 archivos para una sola tabla.

`contexto.md` citaba el proyecto de referencia del profesor (`SpringBootProject`), pero no se había contrastado su estructura con la nuestra. Al compararlos:

- Su `docs/Enunciado.md` dice: "No se usarán `Repository` ni DAO en la primera versión. Cada `@Service` será dueño de sus consultas HQL y recibirá un `EntityManager`".
- Propone los paquetes `app.mappings`, `app.service`, `app.dto`, `app.controller` y `app.exception`.
- Su código inyecta con `@Autowired` sobre el campo y no usa interfaces intermedias.

Ese enunciado es de otro TP (sistema de ventas): no es un requisito del nuestro, pero muestra la estructura que el profesor enseña. La consigna (`TP_PP6_v1.0.md`) pide "arquitectura de capas", sin exigir una en particular. Además, el equipo tiene que poder explicar cada línea en la defensa.

## Decisión
- Paquete raíz `app`, con `app.mappings` (entidad), `app.service` (reglas de negocio y JPQL), `app.dto`, `app.controller` y `app.exception`.
- `LinkService` usa el `EntityManager` directamente. No hay `Repository` ni puertos.
- Una clase por responsabilidad dentro de `app.service`: `LinkService`, `QrService`, `UrlValidator` y `AliasGenerator`.
- Inyección con `@Autowired` sobre el campo; configuración con `@Value`.
- El reloj, el CORS y `@EnableScheduling` quedan en `Application`.
- **No cambia nada hacia afuera:** mismos endpoints, respuestas, documento OpenAPI, tabla, web y extensión. Tampoco cambian las invariantes I1 a I7 ni los casos `TC-xx`.

## Alternativas descartadas
- **Mantener la arquitectura hexagonal (v0.1, rama `dev/agustin`):** tiene una respuesta directa para los cambios de las Etapas 2 y 3 (una clase nueva detrás de una interfaz) y ya está documentada y desplegada. Se descarta en esta propuesta porque es más estructura de la que necesita una tabla, no es la que muestra el profesor y es más difícil de explicar línea por línea.
- **Intermedio: paquetes del profesor conservando el puerto `ShortLinkRepository`:** contradice de forma explícita el "sin `Repository` ni DAO" del proyecto de referencia.
- **Un único `AppService` y un único `AppController`**, como el código actual del profesor: mezcla el QR, la validación y el alias en una sola clase y pierde los tests unitarios de cada uno.

## Consecuencias
- El backend pasa de 46 archivos (unas 1100 líneas) a 15 (unas 700). El recorrido de un pedido es controller → service → entidad.
- Un cambio futuro se resuelve **modificando la clase dueña** (`AliasGenerator`, `UrlValidator`, `QrService` o `LinkService`), no agregando una implementación. Si un punto necesita dos comportamientos a la vez, se extrae la interfaz en ese momento, sin tocar los controllers (`contexto.md` §14.1).
- Se pierde la validación de la configuración al arrancar (`AppProperties` + `@Validated`): `contexto.md` D33 lo registra como costo conocido.
- Los tests de las reglas de `LinkService` necesitan el contexto de Spring y una base en memoria; ya no se prueban con un repositorio falso. Todos comparten un mismo contexto para que arranque una sola vez.
- `LinkService.shorten` sigue usando `TransactionTemplate` (una transacción por intento de alias): es la parte menos parecida al ejemplo del profesor y hay que saber explicarla (I2).
- Los ADR-0001 a 0006 siguen vigentes; los nombres de clases que mencionan son los de la v0.1.
- Si el grupo elige la v0.1, este ADR pasa a "Rechazada" y la rama `dev/sofia` no se integra.
