# PP6-TPGrupo2 - Servicio de Acortamiento y Gestión de Enlaces (Link Shortener)

Trabajo Práctico Integrador – **Paradigmas de Programación VI**  
**Cátedra:** FIE – 5to Año – Ingeniería en Informática  
**Stack Tecnológico:** Java, Spring Boot, JPA/Hibernate, API REST, Cliente Web, Extensión de Navegador.

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
# 1. Ver qué archivos se modificaron
git status

# 2. Agregar los cambios al commit
git add .

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