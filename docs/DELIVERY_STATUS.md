# Estado de entrega

Actualizado el 17/10/2026. Versión 0.1.0. **El MVP cloud no está terminado.** La decisión vigente es D1 + Worker + R2 privado y acceso por código de correo + Google (ADR 0002).

## Evidencia de implementación

| Área | Estado | Comprobación y límite |
| --- | --- | --- |
| Proyecto Android modular | VERIFIED | Cinco módulos, Kotlin/Compose/Material 3/Hilt; debug y release optimizada compiladas, release sin firma de distribución |
| Dominio | VERIFIED | 15 pruebas JVM; importes, validación, OCR conservador y vencimientos civiles |
| Room/repositorio | VERIFIED | 4 pruebas API 36 de transacciones, revisiones, aislamiento y duplicados |
| Migraciones Room 1/2→3 | VERIFIED | Bases creadas con esquemas v1/v2 y abiertas por Room v3: validación de esquema, documento y outbox conservados |
| Documentos y OCR | VERIFIED | 3 pruebas API 36: OCR real de imagen, PDF byte a byte, rechazo de corrupto/tamaño excesivo |
| Registro manual Compose | VERIFIED | Alta, búsqueda, detalle y recreación de Activity en emulador API 36 |
| Sesión/sincronización Android | VERIFIED_LOCAL | 7 pruebas de Keystore/adopción, cuentas separadas, originales con hash coincidente, ACK concurrente, conflictos, rollback ajeno, descarga íntegra y conservación ante fallo; transporte de prueba |
| API Worker D1/R2 | VERIFIED_LOCAL | 19 tests Miniflare: auth, entradas inválidas, replay, intentos, CAS, idempotencia, feed, privacidad, hash, cuotas y firma/claims Google; sin correos reales |
| Bucket dedicado | CREATED | tickettracker-documents, Standard, jurisdicción UE, acceso público desactivado; sin tickets reales subidos |
| D1 dedicada | CREATED | tickettracker, jurisdicción UE, vacía al crearse; migración remota aún no aplicada |
| Paquete Worker | VERIFIED_LOCAL | Wrangler dry-run correcto con D1 y R2 eu; no publicado ni secretos configurados |
| Análisis estático/formato | VERIFIED | Spotless/ktfmt, Prettier y Lint correctos; avisos de versiones/objetivo SDK pendientes de revisión habitual |
| WorkManager sync/recordatorios | IMPLEMENTED | Red, backoff, revisión cloud cada 6 h y avisos opt-in; Doze y dispositivo físico pendientes |
| Cámara y Sharesheet | IMPLEMENTED | Compilan; pruebas físicas y Gmail real pendientes |
| Fechas manuales | IMPLEMENTED | Reglas civiles probadas; no aplica plazos jurídicos automáticos |
| Exportación ZIP | IMPLEMENTED | Originales/manifiesto/hashes; no hay restaurador integrado todavía |
| CI | PARTIALLY_VERIFIED | En c63d1e0, Worker, build/lint/JVM/APK y dispositivo API 29 pasan en GitHub Actions tras corregir setup-android; API 36 en curso. La corrección posterior de hashes/migración v3 se validó localmente y debe repetir CI |
| Supabase/Coolify | HISTORICAL | Auditoría previa solo en lectura; descartados para el producto y sin modificaciones en el VPS |
| Deploy y recuperación cloud real | AWAITING_APPROVAL | Inventario concreto en integrations/CLOUDFLARE_DEPLOYMENT.md; requiere autorización según AGENTS.md |
| Google real | AWAITING_CONFIGURATION | Código y validación criptográfica probados con claves ficticias; faltan clientes OAuth/huellas de firma y ensayo real |
| Gmail directo / motor jurídico / beta | NOT_STARTED | Requieren sus decisiones y validaciones específicas; Google Play no autorizado |

VERIFIED acredita únicamente la prueba indicada. VERIFIED_LOCAL no significa despliegue o recuperación real. CREATED acredita un recurso vacío, no una integración operativa.

## Comprobaciones ejecutadas

- `:domain:test`: 15 pruebas, cero fallos.
- `:app:connectedDebugAndroidTest` en AVD propio API 36: 16 pruebas, cero fallos.
- `spotlessCheck`, `:app:lintDebug`, `:core:documents:lintDebug`, `:core:database:lintDebug`: correctos.
- `:app:assembleDebug` y `:app:assembleRelease`: correctos; APK release sin firma de distribución.
- `node --test --test-concurrency=1 test/*.test.mjs`: 19 pruebas, cero fallos.
- Prettier y `wrangler deploy --dry-run`: correctos; solo empaquetado local, sin cambios cloud.

Los informes y capturas de prueba quedan en `.local/` y `build/`, excluidos de Git. El AVD existente API 37.2 preliminar no era compatible con Espresso; se utilizó uno API 36 aislado del proyecto. API 29 queda preparada en CI, sin resultado local. El helper de migración presentó una incompatibilidad de serialización: la prueba usa ahora la apertura real de Room con el esquema anterior, sin desactivar su validación.

## Siguiente paso autorizado pendiente

Aprobar el inventario concreto, conceder acceso Cloudflare de despliegue, aplicar la migración solo a D1 tickettracker, configurar los secretos del Worker y comprobar el remitente Resend. Configurar los clientes Google web/Android. Compilar con la URL real y comprobar dos instalaciones, caída/reintento, conflictos, permisos e integridad antes de declarar recuperación.

## Límites de esta entrega

La APK predeterminada no tiene endpoint cloud. No se han enviado códigos reales ni desplegado API. El borrador de compra no sobrevive a la muerte del proceso. OCR PDF limitado a la primera página; artículos revisados manualmente. No hay recorte, perspectiva, multipágina, devoluciones parciales o reglas jurídicas automáticas. No hay restaurador ZIP, eliminación de cuenta, backup independiente D1/R2 ensayado ni compactación del feed. No se eliminan originales ni se promete recuperación en producción. Rendimiento, precisión OCR y costes reales no se han medido con carga.
