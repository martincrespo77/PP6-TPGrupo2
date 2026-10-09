# ADR-0003: Alias como clave primaria y creación con `persist`

- **Estado:** Aceptada
- **Fecha:** 09/10/2026
- **Referencias:** `contexto.md` D30, D32, I2, R7

## Contexto
Debe existir como máximo un enlace por alias, incluso con pedidos simultáneos, y una creación nunca debe sobrescribir un enlace vigente.

## Decisión
- `alias` es la clave primaria de `short_link`: la base garantiza la unicidad.
- La creación usa `EntityManager.persist`, nunca `merge`.
- Si dos transacciones toman el mismo alias, la segunda falla por la PK (`DataIntegrityViolationException`) y el caso de uso reintenta con otro alias.

## Alternativas descartadas
- **`id` autoincremental + columna `active_alias UNIQUE`** (Martín): necesaria con soft delete; con borrado físico (ADR-0002) no aporta.
- **`merge`:** con el alias como PK, sobrescribiría en silencio un enlace vigente.

## Consecuencias
- La invariante I2 la sostiene la base, no solo el código.
- Si se pasa a archivar (ADR-0002 revisado), habrá que volver a un `id` propio y revisar esta decisión.
