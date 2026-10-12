# Guion de demo (5 minutos)

Objetivo: mostrar que el acortador cumple la consigna de punta a punta y que las reglas importantes están protegidas por tests.

## Preparación (antes de la clase)

- [ ] Abrir https://paradigmas6.agustingimenez.ar en Chrome. Si no hay red, levantar en local con `gradlew.bat bootRun` y usar http://localhost:8080 (y el zip de la extensión generado con `-ApiUrl http://localhost:8080`).
- [ ] Extensión cargada en Chrome (y Firefox si se va a mostrar) y fijada en la barra.
- [ ] Una pestaña con una página larga para acortar, por ejemplo una carpeta de Google Drive.
- [ ] El celular a mano para escanear el QR.
- [ ] Una terminal en la carpeta del proyecto, con el último `gradlew.bat build` en verde.
- [ ] Para mostrar el vencimiento sin esperar una hora: una segunda instancia local con `java -jar build\libs\shortener.jar --app.link.ttl=40s --server.port=8081 --app.base-url=http://localhost:8081 --spring.datasource.url=jdbc:hsqldb:mem:demo` (sin `app.base-url` los enlaces apuntarían al puerto 8080).

## Guion

| Min | Qué se muestra | Qué se dice |
|---|---|---|
| 0:00 | La web en el VPS | "Es un acortador: pegás una dirección larga y obtenés una corta y su QR, que duran 60 minutos." |
| 0:30 | Pegar `drive.google.com/x` (sin `https://`) y ACORTAR | "La validación está en el servidor: la web y la extensión muestran el mismo mensaje." |
| 1:00 | Pegar la URL de Drive completa y ACORTAR | Señalar el enlace, el QR, Copiar, Descargar QR y "Vence a las HH:MM". "El tiempo restante lo calcula el servidor, no el reloj de la compu." |
| 1:30 | Escanear el QR con el celular | "El QR tiene la URL **corta**, así que también respeta el vencimiento." |
| 2:00 | Abrir el enlace corto en otra pestaña | "Redirige con 302, nunca 301: un 301 lo guardaría el navegador y seguiría funcionando después de vencer." |
| 2:30 | Abrir `/zzzzz` | "Inexistente y vencido muestran la misma página: no se puede adivinar qué alias existieron." |
| 3:00 | Instancia con TTL de 40 s: crear un enlace y esperar | La tarjeta pasa a "vencido" sola y el enlace da 404. "Vence al leer, no depende del borrado nocturno." |
| 3:30 | Extensión en una página cualquiera | Un clic: enlace y QR. Después abrirla en `chrome://extensions`: ACORTAR deshabilitado con el aviso. |
| 4:00 | Terminal: `gradlew.bat build` (o la salida ya guardada) y `docs/CHECKLIST-ETAPA1.md` | "136 tests; los casos de la matriz se llaman `tcNN_…`. Además rompimos cada regla a propósito (31 mutaciones) y siempre falla algún test. Cobertura de dominio y aplicación: 98 %." |
| 4:30 | `docs/BITACORA.md` y Swagger (`/swagger-ui.html`) | "Cada paso tiene su entrada con evidencias y el registro de prompts. La API está documentada con OpenAPI." |
| 5:00 | Cierre | "Arquitectura hexagonal: cambiar la base, el generador de alias o el QR es otra clase, sin tocar los casos de uso." |

## Si algo falla

| Problema | Plan B |
|---|---|
| Sin internet | Todo en local (`bootRun`) y la extensión con el zip de localhost |
| La extensión no carga | Mostrar la misma funcionalidad en la web y las capturas de la bitácora |
| El celular no lee el QR | Mostrar el test `tc50_…`, que decodifica la imagen con ZXing |
| No da el tiempo para esperar el vencimiento | Mostrar la captura `docs/evidencias/paso-7/6-vencido.png` |

## Preguntas que conviene tener preparadas

Las respuestas están en `docs/BITACORA.md`, en la sección "Preguntas probables del profesor" de cada paso. Las más probables:
- ¿Por qué 302 y no 301? (Paso 4)
- ¿Qué pasa si dos personas generan el mismo alias al mismo tiempo? (Paso 3, TC-31)
- ¿Cómo saben que los tests sirven? (mutaciones, §15.4)
- ¿Qué es CORS y por qué solo para la extensión? (Paso 8)
- ¿Por qué el QR se genera en el servidor? (Paso 6, ADR-0004)
