# Inventario propuesto: original único en R2

18/10/2026. **Preparado; pendiente de aprobación y ejecución en producción.** Complementa el inventario inicial; no modifica Supabase ni infraestructura de otros proyectos.

## Observación en lectura

El inventario aumentó durante la revisión por nuevas subidas. Último SELECT en D1 `tickettracker`: **tres originales**, 6.217.491 bytes (616, 2.007.137 y 4.209.738), y **cuatro derivados**, 1.412.320 bytes: OPTIMIZED de 477.118 y 900.724, THUMBNAIL de 16.533 y 17.945. La consulta no equivale a facturación ni sustituye la verificación actual de los objetos. El original de 616 bytes es el PDF sintético anterior. No se incluyen tickets privados en pruebas o CI.

El manifiesto privado `.local/storage-inventory-before.json` delimita los siete objetos y sus hashes: SHA-256 `80e99385078755ecc901caa710420dc3432fe2fa9f42eb228ea0522d0dff0544`. Solo sus cuatro filas no ORIGINAL forman parte de esta limpieza; cualquier otra subida queda fuera de ella.

## Cambios concretos a aprobar

1. Publicar el Worker `tickettracker-api` con los bindings existentes: D1 exclusiva `tickettracker` (`b8a13f81-ee19-49bb-8618-bc4dfd3cd94e`) y R2 privado `tickettracker-documents`, jurisdicción UE. Conservar secretos, audiencia Google, cuotas y cron actuales. Sin nuevas tablas, migraciones ni permisos.
2. Normalizar nuevas reservas/subidas a un solo ORIGINAL y aceptar como no-op derivados de APK antiguas. Mantener sesiones, aislamiento, idempotencia y validación de integridad.
3. Añadir `GET /v1/storage` por cuenta y `POST /v1/documents/{id}/optimize` autenticado, con verificación SHA-256 del original antes de retirar derivados. Publicar una nueva revisión de metadatos en el feed existente.
4. Con sesión propia mediante el endpoint o con mantenimiento administrativo acotado D1/R2 (sin impersonar otra cuenta), limpiar únicamente los cuatro derivados del manifiesto (477.118, 900.724, 16.533 y 17.945 bytes), conservando los tres originales. Verificar tamaño/hash de los originales antes y después, resultado en R2/D1 y que una repetición no vuelve a cambiar la revisión. Si cambia alguno de los objetos inventariados o un original no verifica, detener esa limpieza y revisar. Otras subidas no amplían el conjunto autorizado.
5. Validar HTTPS y transporte desde el emulador aislado con la cuenta del propietario, sin adoptar fixtures anónimos. Los códigos de acceso necesarios están autorizados por el propietario; solicitar su contenido cuando se envíen. No registrar tokens ni códigos. Revocar la sesión de operación al acabar.

No activar planes de pago, hacer público el bucket, reducir la calidad del original ni borrar compras/documentos. No se solicita acceso a Gmail o publicación en Play.

## Validación y reversión

Pruebas locales: Miniflare con D1/R2 aislados, dominio y suite Android; evidencia final en DELIVERY_STATUS.md. Empaquetar y revisar el Worker antes de publicarlo. Conservar la versión anterior para revertir código; no reponer referencias a derivados ya retirados. Los clientes antiguos admiten documentos con un único original. Si hay un fallo parcial de limpieza, las filas derivadas pendientes permiten reintentar sin tocar el original. Las exportaciones ZIP y archivos locales existentes se conservan.

Paquete revisado: `.local/worker-bundle/worker.js`, SHA-256 `476166299b107fb1812f3e6c102658b8c7a148b6ef422153f207fbd590ba479b`. Dry-run sin publicación correcto. La limpieza administrativa seguirá el mismo orden: preservar/verificar originales, publicar metadatos canónicos con revisión/feed, retirar solo los cuatro derivados y después sus filas, y verificar de nuevo.
