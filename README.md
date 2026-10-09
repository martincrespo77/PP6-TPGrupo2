# PP6-TPGrupo2 - Servicio de Acortamiento y Gestión de Enlaces (Link Shortener)

Trabajo Práctico Integrador – **Paradigmas de Programación VI**  
**Cátedra:** FIE – 5to Año – Ingeniería en Informática  
**Stack Tecnológico:** Java, Spring Boot, JPA/Hibernate, API REST, Cliente Web, Extensión de Navegador.

La especificación del proyecto (fuente única de verdad) está en [`contexto.md`](contexto.md).

---

## ⚙️ Compilar y ejecutar

Stack: **Java 25**, Spring Boot 4.1.1, Gradle 9.8.1 (wrapper incluido: no hace falta instalar Gradle). Si no tenés un JDK 25 instalado, Gradle lo descarga solo la primera vez.

| Acción | Linux / macOS | Windows |
| :--- | :--- | :--- |
| Compilar + tests | `./gradlew build` | `gradlew.bat build` |
| Levantar la app | `./gradlew bootRun` | `gradlew.bat bootRun` |
| Generar el JAR | `./gradlew bootJar` → `build/libs/shortener.jar` | `gradlew.bat bootJar` |

La app queda en http://localhost:8080, con HSQLDB en modo archivo (`./data/`, ignorado por git) y Swagger en http://localhost:8080/swagger-ui.html.

`java -jar build/libs/shortener.jar` requiere que el `java` del PATH sea **25**. Si tu PATH tiene otra versión, usá `bootRun` o la ruta completa al JDK 25.

Toda la configuración está en un único `src/main/resources/application.properties` (ver `contexto.md` §12.2). Para correr en otra máquina (VPS) solo se cambia el dominio con una variable de entorno (ver `.env.example`):

```bash
APP_BASE_URL=http://ip-o-dominio:8080 java -jar shortener.jar
```

La bitácora de cada paso está en [`docs/BITACORA.md`](docs/BITACORA.md) y las decisiones de arquitectura en [`docs/adr/`](docs/adr/).

## 🧰 Scripts

| Script | Qué hace |
| :--- | :--- |
| `scripts\mutation-test.ps1 [-Step N]` | Rompe a propósito cada invariante del paso N (o de todos), corre los tests y verifica que alguno falle (`contexto.md` §15.4). Restaura los archivos al terminar |
| `scripts\deploy.ps1` | Despliega el commit actual en el VPS: build + tests, sube el JAR verificando el hash, backup, reinicio, health check con **rollback automático** y verificación pública. Requiere acceso SSH por clave al VPS |

```powershell
powershell -ExecutionPolicy Bypass -File scripts\mutation-test.ps1 -Step 1
powershell -ExecutionPolicy Bypass -File scripts\deploy.ps1
```

## 🔁 Ciclo de cada paso (ramas de entrega)

Cada integrante trabaja siempre en su rama `dev/<nombre>`. Al cerrar un paso se crea una **rama de entrega** `paso/<N>` que apunta al último commit de ese paso, y el pull request va desde esa rama a `main`. Así cada PR contiene un solo paso y no crece con el trabajo que sigue.

```text
dev/agustin ──●──●──●──●──●──●──▶ (se sigue trabajando)
                    │        │
                 paso/0-1  paso/2      ← ramas de entrega (congeladas)
                    │        │
                    ▼        ▼
main ───────────────●────────●──────▶  (solo lo aprobado)
```

1. Construir el paso en `dev/<nombre>` (tests, mutaciones, entrada en `docs/BITACORA.md`) y hacer push.
2. Crear la rama de entrega: `git branch paso/<N> <commit>` y `git push -u origin paso/<N>`.
3. Abrir el PR `paso/<N>` → `main` con el resumen, las evidencias y el checklist de la revisora.
4. Revisa y aprueba **otro integrante** (quien construye no acepta, `contexto.md` §19). Si pide cambios, se hacen en `dev/<nombre>` y se actualiza la rama de entrega.
5. Se mergea a `main` y se actualiza `dev/<nombre>` con `git fetch origin` + `git merge origin/main`.

---

## 👥 Equipo de Trabajo
- **Martín Crespo** (`dev/martin`)
- **Sofía** (`dev/sofia`)
- **Agustín** (`dev/agustin`)

---

## 🌳 Estructura de Ramas en el Repositorio

| Rama | Propósito |
| :--- | :--- |
| `main` | Rama principal estable. Solo código probado e integrado. |
| `dev/martin` | Ambiente de trabajo de Martín. |
| `dev/sofia` | Ambiente de trabajo de Sofía. |
| `dev/agustin` | Ambiente de trabajo de Agustín. |

---

## 🚀 Guía de Trabajo en Simultáneo

### 1. Clonar el repositorio (para Sofía y Agustín en sus PCs)
```bash
git clone https://github.com/martincrespo77/PP6-TPGrupo2.git
cd PP6-TPGrupo2
```

### 2. Cambiar a la rama personal propia
- **Martín:**
  ```bash
  git checkout dev/martin
  ```
- **Sofía:**
  ```bash
  git checkout dev/sofia
  ```
- **Agustín:**
  ```bash
  git checkout dev/agustin
  ```

### 3. Flujo diario de trabajo (en tu propia rama)
Cada vez que realices cambios en tu código:
```bash
# 1. Ver qué archivos se modificaron (revisar que no haya .env ni data/)
git status

# 2. Agregar los cambios al commit, archivo por archivo (nunca `git add .` ni `git add -A`)
git add ruta/al/archivo1 ruta/al/archivo2

# 3. Guardar el commit con mensaje descriptivo
git commit -m "feat: descripción del avance"

# 4. Subir a tu rama en GitHub
git push
```

### 4. Mantener tu rama actualizada con `main`
Antes de integrar o al comenzar el día, actualiza tu rama personal con los últimos cambios de `main`:
```bash
# Estando parado en tu rama personal:
git fetch origin
git merge origin/main
```
Si hay conflictos, se resuelven en tu rama personal sin afectar `main`.

### 5. Subir tus cambios a la rama `main`
La mejor práctica es hacerlo mediante **Pull Request (PR)** en GitHub:
1. Ve a https://github.com/martincrespo77/PP6-TPGrupo2/pulls
2. Haz clic en **New Pull Request**.
3. Selecciona base: `main` y compare: tu rama (`dev/martin`, `dev/sofia` o `dev/agustin`).
4. Revisa los cambios, pide el visto bueno de tus compañeros y presiona **Merge Pull Request**.

#### (Alternativa por consola si no usan PRs):
```bash
git checkout main
git pull origin main
git merge dev/tunombre
git push origin main
git checkout dev/tunombre
```