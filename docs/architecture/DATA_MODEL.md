# Modelo de datos

| Entidad | Clave y contenido |
| --- | --- |
| Purchase | UUID, ownerId, comercio, fechas civiles, céntimos enteros, canal, revisión local/remota, tombstone |
| Product | UUID independiente, purchaseId, nombre, fechas manuales de garantía/devolución |
| PurchaseLine | UUID independiente, purchaseId, productId único, orden, cantidad y precio opcional |
| Document / Variant | UUID, propietario, asociación opcional, original/optimizado/miniatura, MIME, bytes, SHA-256 |
| Outbox | entityId, operationId, propietario, tipo, revisión remota base y JSON versionado |
| Cursor / Conflict | Cursor por propietario; versión remota pendiente de decisión por agregado |
| Profile / Identity | UUID interno y enlaces email/Google; independiente del proveedor de login |
| Session / Challenge | HMAC de token o código, expiración y consumo; no se guarda el secreto en claro en D1 |

Room v2 tiene migración 1→2 no destructiva y esquemas JSON exportados. Añade revisión remota documental, cursores y conflictos. Se valida abriendo una base creada con el esquema v1 mediante la implementación real de Room v2.

D1 usa una base exclusiva y once tablas `tt_`. `tt_records` conserva agregados de compra/documento con sus revisiones, `tt_changes` el feed ordenado por commits serializados de SQLite, `tt_mutations` la idempotencia y `tt_variants` el propietario, clave R2 opaca e integridad. Las líneas y productos siguen siendo entidades distintas dentro del agregado. El cliente no accede directamente a D1.

El total de compra es independiente de la suma de sus líneas. Las asociaciones documentales se actualizan sin borrar originales; un documento no se reasigna silenciosamente a otra compra. Los documentos sobreviven al borrado lógico. No se habilita limpieza destructiva.

Pendientes: devoluciones parciales, reclamaciones, reglas jurídicas versionadas, comercios normalizados y políticas con evidencias. Las fechas manuales no constituyen un motor legal.
