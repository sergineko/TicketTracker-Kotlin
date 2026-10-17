# ADR 0003 Resumen de productos, suscripciones y seguros

17/10/2026. El propietario solicita aproximar la interfaz a la imagen de `img/`, incluyendo las tarjetas Suscripciones y Seguros. Posteriormente sustituye En servicio por Devoluciones posibles. Se mantienen los datos reales y Room como fuente observable.

El resumen tiene tres contadores pulsables por registro de producto: Garantía activa (fecha manual igual o posterior a hoy), Crítica (entre hoy y hoy + 30 días, ambos incluidos) y Devoluciones posibles (fecha manual de devolución igual o posterior a hoy). Las críticas también son garantías activas y las devoluciones son independientes; no son categorías excluyentes. No se deduce derecho de desistimiento por canal/fecha de compra. Se explican las condiciones al consultar el filtro. No se añade estado de servicio técnico.

Para dar comportamiento real a las dos tarjetas secundarias se incorpora un registro manual local de suscripción/seguro: UUID, propietario, nombre, proveedor opcional, importe entero/moneda, periodo, próxima fecha confirmada, activo, revisión y borrado lógico. No genera cobros, renovaciones, fechas jurídicas ni notificaciones. Por renovar incluye los registros activos con fecha igual o anterior a hoy + 30 días, incluidos los atrasados, hasta que el usuario actualice su fecha/estado.

Room v4 añade únicamente `tracked_contracts` con índice por propietario. Se usan transacciones, revisiones compare-and-swap y aislamiento por cuenta; el alta de sesión adopta los registros anónimos junto al resto del inventario. Cerrar sesión no borra los registros de la cuenta. Exportación ZIP v2 incluye los contratos y conserva los originales y hashes.

Los contratos son **solo locales**. El aviso figura en sus pantallas. No se encolan como cambios cloud ni se modifica D1/Worker/R2: ampliar el inventario remoto necesita una propuesta y aprobación específica de producción. El esquema aprobado de ADR 0002 permanece vigente. Sincronización, recuperación entre instalaciones y avisos de estos contratos quedan pendientes.
