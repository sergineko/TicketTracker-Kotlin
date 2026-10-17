# Roadmap incremental

| Fase | Resultado y salida | Estado inicial |
| --- | --- | --- |
| 0 Análisis | Especificación, arquitectura y auditoría de lectura del VPS | Realizada; decisiones de producción pendientes |
| 1 Fundación | Cinco módulos, Compose, DI, navegación, temas, CI y builds | Implementada |
| 2 Persistencia | Room, identidad local, esquema exportado, contratos y outbox | Local implementada; Auth D1/Worker en validación |
| 3 Inventario | Alta/edición/detalle/borrado lógico/búsqueda y varios artículos | Implementada |
| 4 Digitalización | CameraX, SAF, compartir, original/miniatura, OCR y revisión | Base implementada; recorte, perspectiva y multipágina pendientes |
| 5 Plazos | Fechas manuales y recordatorios; motor jurídico condicionado y versionado | Base manual implementada; motor legal pendiente |
| 6 Nube | Worker HTTPS, D1, R2 privado, códigos/Google y feed | Código probado localmente; despliegue y ensayo real pendientes |
| 7 Integraciones | Sharesheet y luego Gmail OAuth verificado | Sharesheet implementado; Gmail directo pendiente |
| 8 Calidad | Accesibilidad exhaustiva, perfiles, benchmarks y dispositivos físicos | Pruebas iniciales; ampliación pendiente |
| 9 Beta | Firma, AAB, nombre, privacidad/Data Safety, backup/restore y pruebas internas | No iniciada; sin publicación autorizada |

La siguiente entrega aplica el inventario Cloudflare aprobado y verifica el transporte real, Google y la recuperación en otra instalación. La fase cloud no está terminada por haber creado recursos vacíos o pasado tests emulados. ZIP restore, eliminación de cuenta, backup independiente y compactación del feed preceden a la beta.
