# ADR-0002: Borrado físico de vencidos con cron y reasignación inmediata

- **Estado:** Propuesta (🟡 pendiente de confirmar en grupo, R1)
- **Fecha:** 09/10/2026
- **Referencias:** `contexto.md` D5, D6, I6, R1, C7, C8, C14

## Contexto
La consigna dice que una URL vencida "quedará disponible y podrá ser reasignada". El Cliente indicó que no hay contador de accesos (C7), ni historial en el cliente (C8), ni requisitos de auditoría o privacidad (C14).

## Decisión
- Una tarea `@Scheduled` (cron configurable `app.cleanup.cron`, diaria por defecto) borra físicamente las filas con `expiresAt <= now`.
- Si al generar un alias este choca con una fila vencida que el cron todavía no borró, esa fila se borra en la misma transacción y el alias se reasigna. Si choca con una fila vigente, se genera otro alias.

## Alternativas descartadas
- **Soft delete con estado `EXPIRED` e historial** (propuestas de Martín y Agustín): agrega columnas e índices para un historial que hoy nadie consume.

## Consecuencias
- Tabla chica y modelo simple: el alias puede ser la clave primaria (ADR-0003).
- Un alias vencido y uno inexistente son indistinguibles (ADR-0006).
- **Riesgo:** si en una etapa futura se piden historial o estadísticas, hay que pasar a archivar en lugar de borrar (`contexto.md` §14.2).
