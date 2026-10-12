# ADR-0004: QR generado solo en el backend

- **Estado:** Aceptada
- **Fecha:** 09/10/2026
- **Referencias:** `contexto.md` D21, D22, D23, I4, C11

## Contexto
La web y la extensión deben mostrar un QR de la URL acortada, descargable como PNG con el alias como nombre de archivo (C11).

## Decisión
- Un único endpoint `GET /api/v1/links/{alias}/qr` genera el PNG con ZXing, detrás del puerto `QrCodeGenerator`.
- La URL codificada se arma desde `app.base-url`, nunca desde los headers de la petición.
- `?download=true` agrega `Content-Disposition: attachment; filename="{alias}.png"`.

## Alternativas descartadas
- **Generar el QR en cada cliente con una librería JS:** duplica la lógica en dos clientes y en dos lugares que probar. El atributo HTML `download` además no funciona entre orígenes distintos (caso de la extensión).

## Consecuencias
- Una sola implementación y un solo test que decodifica el PNG (TC-50).
- Cambiar de librería o de formato (SVG) es una implementación nueva del puerto.
