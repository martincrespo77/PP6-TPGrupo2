# ADR-0001: El vencimiento se evalúa al leer, con intervalo semiabierto

- **Estado:** Aceptada
- **Fecha:** 09/10/2026
- **Referencias:** `contexto.md` D2, D3, D4, I1

## Contexto
La consigna exige que la URL acortada deje de redirigir a los 60 minutos. Si eso dependiera de una tarea programada, entre dos corridas un enlace vencido seguiría redirigiendo. Además, "60 minutos" admite dos lecturas en el instante exacto del borde.

## Decisión
- Cada lectura compara `expiresAt` con el `Clock` inyectado mediante `ShortLink.isExpired(now)`, que devuelve `!now.isBefore(expiresAt)`.
- El enlace es válido en el intervalo semiabierto `[createdAt, expiresAt)`: en `createdAt + 60:00.000` ya venció.
- Los instantes son `Instant` en UTC. Nunca se usa `LocalDateTime.now()`.

## Alternativas descartadas
- **Solo una tarea programada que marca o borra vencidos:** deja una ventana en la que un enlace vencido redirige.
- **Comparar en la consulta SQL con la hora de la base:** acopla la regla al motor y a su reloj, y no se puede testear con un `Clock` fijo.

## Consecuencias
- La corrección no depende del cron (ADR-0002); el cron solo ordena la base.
- Los tests simulan el paso del tiempo con un `Clock` fijo (TC-01 a TC-04).
- Depende de que el reloj del servidor esté sincronizado (NTP).
