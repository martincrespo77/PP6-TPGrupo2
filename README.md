# PP6-TPGrupo2 - Servicio de Acortamiento y Gestión de Enlaces (Link Shortener)

Trabajo Práctico Integrador – **Paradigmas de Programación VI**  
**Cátedra:** FIE – 5to Año – Ingeniería en Informática  
**Stack Tecnológico:** Java, Spring Boot, JPA/Hibernate, API REST, Cliente Web, Extensión de Navegador.

La especificación del proyecto (fuente única de verdad) está en [`contexto.md`](contexto.md).

## 🔗 Qué hace

Pegás una dirección larga y obtenés un **enlace corto y su código QR, válidos por 60 minutos**. Después, el enlace da "Este enlace expiró o no existe" y el alias puede volver a usarse.

- **En vivo:** https://paradigmas6.agustingimenez.ar (web) · [`/swagger-ui.html`](https://paradigmas6.agustingimenez.ar/swagger-ui.html) (API)
- **Clientes:** la web (`src/main/resources/static/`) y la extensión para Chrome y Firefox ([`browser-extension/`](browser-extension/))

| Endpoint | Qué hace |
| :--- | :--- |
| `POST /api/v1/links` `{"url": "..."}` | Crea el enlace → `201` con `shortUrl`, `expiresAt`, `secondsRemaining` y `qrUrl`. `400` si la URL es inválida, `503` si no hay alias libres |
| `GET /{alias}` | `302` a la URL original mientras está vigente; `404` (misma página) si venció o no existe |
| `GET /api/v1/links/{alias}/qr?size=256&download=false` | PNG del QR (128 a 1024 px). Con `download=true` se descarga como `{alias}.png` |

**Documentación:** [`docs/BITACORA.md`](docs/BITACORA.md) (cada paso, con evidencias y prompts) · [`docs/CHECKLIST-ETAPA1.md`](docs/CHECKLIST-ETAPA1.md) (criterios de aceptación) · [`docs/DEMO.md`](docs/DEMO.md) (guion de 5 minutos) · [`docs/adr/`](docs/adr/) (decisiones de arquitectura) · [`docs/diagramas/`](docs/diagramas/) (diagramas interactivos; abrir el HTML en el navegador) · [`TP_PP6_v1.0.md`](TP_PP6_v1.0.md) (consigna)

---

## ⚙️ Compilar y ejecutar

Stack: **Java 25**, Spring Boot 4.1.1, Gradle 9.8.1 (wrapper incluido: no hace falta instalar Gradle). Si no tenés un JDK 25 instalado, Gradle lo descarga solo la primera vez.

| Acción | Linux / macOS | Windows |
| :--- | :--- | :--- |
| Compilar + tests | `./gradlew build` | `gradlew.bat build` |
| Levantar la app | `./gradlew bootRun` | `gradlew.bat bootRun` |
| Generar el JAR | `./gradlew bootJar` → `build/libs/shortener.jar` | `gradlew.bat bootJar` |
| Cobertura (JaCoCo) | `./gradlew check` → `build/reports/jacoco/test/html/index.html` | `gradlew.bat check` |

`check` falla si la cobertura de líneas de `domain` + `application` baja del 70 % (R13).

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
| `scripts\package-extension.ps1 [-ApiUrl URL]` | Genera `build/extension/acortador-pp6-<versión>.zip` con la extensión. Con `-ApiUrl http://localhost:8080` arma un zip que usa el backend local, sin tocar el código |

```powershell
powershell -ExecutionPolicy Bypass -File scripts\mutation-test.ps1 -Step 1
powershell -ExecutionPolicy Bypass -File scripts\deploy.ps1
powershell -ExecutionPolicy Bypass -File scripts\package-extension.ps1
```

## 🧩 Extensión para Chrome y Firefox

La carpeta [`browser-extension/`](browser-extension/) es la extensión (Manifest V3, el mismo código para los dos navegadores). Al abrirla acorta la pestaña activa y muestra el enlace, el QR, **Copiar**, **Descargar QR** y la hora de vencimiento. Usa el servidor de `config.js` (por defecto https://paradigmas6.agustingimenez.ar).

**Chrome / Edge**
1. Abrir `chrome://extensions` (en Edge, `edge://extensions`).
2. Activar **Modo de desarrollador** (arriba a la derecha).
3. **Cargar descomprimida** → elegir la carpeta `browser-extension`.
4. Fijar la extensión con el ícono del rompecabezas, para tenerla a mano.

**Firefox**
1. Abrir `about:debugging#/runtime/this-firefox`.
2. **Cargar complemento temporal…** → elegir `browser-extension/manifest.json` (o el `.zip`).
3. Firefox la quita al cerrarse: es una carga temporal porque la extensión no está firmada.

En páginas que no son http/https (por ejemplo `chrome://extensions` o una pestaña nueva) el botón ACORTAR aparece deshabilitado con el aviso "Esta página no se puede acortar (solo http/https)".

## 🔁 Ciclo de cada paso

Cada integrante trabaja siempre en su rama `dev/<nombre>`, un commit por paso (o varios), y lo integra a `main` con **un pull request `dev/<nombre>` → `main`**.

```text
dev/agustin ──●──●──●──●──●──▶   (paso 0, 1, 2… cada uno con sus commits)
                         │
                    PR → main     (lo aprueba otro integrante)
```

1. Construir el paso en `dev/<nombre>` (tests, mutaciones, entrada en `docs/BITACORA.md`) y hacer push.
2. Si no hay un PR abierto, abrir uno `dev/<nombre>` → `main`. Si ya hay uno abierto, los commits nuevos se suman solos.
3. Revisa y aprueba **otro integrante** (quien construye no acepta, `contexto.md` §19). Si pide cambios, se hacen en la misma rama.
4. Se mergea a `main` y se actualiza la rama con `git fetch origin` + `git merge origin/main`.

---

## 👥 Equipo de Trabajo
- **Martín Crespo** (`dev/martin`)
- **Sofía Ramirez** (`dev/sofia`)
- **Agustín Gimenez** (`dev/agustin`)

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
4. Asigná como revisor a **otro integrante**. Se mergea cuando lo aprueba: quien construye no acepta su propio trabajo (`contexto.md` §19). No se hace merge ni push directo a `main`.