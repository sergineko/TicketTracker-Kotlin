# Estrategia y evidencia de pruebas

El dominio tiene pruebas JVM de importes, validación, extracción conservadora y fechas civiles. Las pruebas instrumentadas cubren Room transaccional, revisiones, aislamiento, documentos/OCR reales y el flujo Compose de alta/búsqueda/detalle/recreación. La migración 1→2 parte del esquema JSON v1 y abre Room v2, validando el esquema y conservando datos/outbox.

Seis pruebas Android adicionales verifican cifrado Keystore y adopción de inventario, separación entre cuentas, ACK concurrente, conflictos explícitos, rollback de feed ajeno, descarga bajo demanda íntegra y conservación de original/outbox ante fallo. El transporte es un adaptador de prueba; no se confunde con una conexión cloud real.

La API se ensaya con Miniflare, D1 y R2 locales. Resend y JWKS se interceptan: no se envían correos ni se usan cuentas reales. Dieciocho tests verifican auth, replay, intentos, sesiones revocables, CAS, idempotencia, feed, objetos privados, hashes y la validación criptográfica Google con RSA de prueba.

CI ejecuta formato, análisis estático, build, tests JVM/Worker y dispositivos API 29/36. Tener workflow no acredita una ejecución remota correcta; consultar DELIVERY_STATUS y GitHub Actions. Los resultados locales se limitan a API 36. No se ejecutan fixtures contra el VPS ni D1 remota.

Pendientes: Wire HTTPS real, códigos reales, Google con clientes registrados, recuperación completa en una segunda instalación, restore de backup independiente, pruebas físicas de cámara/Doze, corpus OCR, accesibilidad y benchmarks. El helper MigrationTestHelper de Room presentó un AbstractMethodError de serialización; se reemplazó por la apertura de la implementación real de Room sobre el esquema anterior, sin omitir validación.
