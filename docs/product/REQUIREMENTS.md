# Requisitos y prioridad

La especificación de origen es `SOURCE_SPECIFICATION.md`, transcrita del Word. La decisión posterior del propietario sustituye Supabase por Cloudflare D1 + Worker + R2 privado; autenticación por código de correo y Google. ADR 0002 documenta la arquitectura vigente. El aislamiento remoto se implementa mediante una base D1 exclusiva `tickettracker`.

## Requisitos de este incremento

- Proyecto compilable API 29/36, Kotlin, Compose Material 3, Hilt, Gradle DSL y catálogo de versiones.
- Navegación Inicio/Productos/Tickets/Más y botón central para registrar/escanear/importar; sin inventario simulado.
- Compras manuales con múltiples artículos, edición, borrado lógico y búsqueda.
- Room observable, transacciones, revisiones y outbox persistente; identidad local estable.
- Importación de imagen/PDF, OCR local, confirmación editable, conservación de originales e integridad.
- Fechas confirmadas de garantía y devolución independientes, alertas y preferencias. OCR propone artículos y fechas absolutas impresas, aplicables por producto con revisión; no calcula periodos ni derechos legales.
- Nuevas subidas con un solo original íntegro; miniaturas locales. Compatibilidad con outbox anterior y limpieza autenticada de derivados solo tras verificación del original y aprobación de producción.
- Cuenta integrada con identidad, estado y alcance real de sincronización; tema oscuro sin placa blanca en la marca.
- Exportación portátil y estado cloud honesto.
- Documentación y CI; pruebas unitarias e instrumentadas con errores y conflictos.
- Rediseño según la referencia aportada: tarjetas Productos y garantías, Suscripciones y Seguros, filtros y filas compactas. Contadores Garantía activa, Crítica (30 días) y Devoluciones posibles con fecha manual vigente; las categorías pueden solaparse.
- Registro manual local de suscripciones/seguros con aislamiento por cuenta, revisiones, borrado lógico y exportación. Su sincronización y avisos quedan pendientes (ADR 0003).

## Criterio del MVP comercial completo

Cuenta email/Google, captura real, OCR y revisión, políticas aplicables por artículo, notificaciones, sincronización verificada y recuperación del inventario y documentos en una segunda instalación. El MVP no se declara terminado con una app local aunque sus flujos funcionen.

## Condiciones transversales

No subir credenciales administrativas al APK. No exponer R2 o documentos a cachés compartidas. No ejecutar cambios en producción durante la auditoría. No sobrescribir revisiones concurrentes ni fusionar compras por coincidencias débiles. No borrar el último archivo válido. Las fechas jurídicas necesitan condiciones de aplicabilidad y fuentes oficiales revisadas.
