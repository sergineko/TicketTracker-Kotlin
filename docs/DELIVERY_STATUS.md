# Estado de entrega

Actualizado el 18/10/2026. Versión de pruebas 0.1.1. **El MVP cloud no está terminado.** La decisión vigente es D1 + Worker + R2 privado y acceso por código de correo + Google (ADR 0002). Suscripciones y seguros son registros solo locales en este incremento (ADR 0003).

## Evidencia de implementación

| Área | Estado | Comprobación y límite |
| --- | --- | --- |
| Proyecto Android modular | VERIFIED | Cinco módulos, Kotlin/Compose/Material 3/Hilt; debug y release optimizada compiladas, release sin firma de distribución |
| APK personal 0.1.1 | VERIFIED_LOCAL | versionCode 2, APK universal de 123027297 bytes, firma debug v2 válida y coincidente con Google piloto; instalación y apertura comprobadas en API 36. Archivo en entregas/, guía en testing/PERSONAL_TESTING.md |
| Dominio | VERIFIED | 18 pruebas JVM; importes, OCR conservador, límites inclusivos de garantías/devoluciones y renovaciones |
| Room/repositorios | VERIFIED_LOCAL | 4 pruebas de compras y 2 de contratos/exportación: transacciones, revisiones, aislamiento, borrado lógico y originales íntegros |
| Migraciones Room 1/2/3→4 | VERIFIED_LOCAL | Bases creadas con esquemas v1/v2/v3 y abiertas por Room v4: esquema validado, documento/outbox conservados y tabla de contratos utilizable |
| Documentos y OCR | VERIFIED | 3 pruebas API 36: OCR real de imagen, PDF byte a byte, rechazo de corrupto/tamaño excesivo |
| Flujos Compose | VERIFIED_LOCAL | Compra manual/búsqueda/detalle/recreación; filtros del resumen y pestañas; alta, recreación, reapertura y borrado de suscripción en API 36 |
| Suscripciones/seguros | VERIFIED_LOCAL | CRUD manual Room por cuenta, CAS y adopción de inventario anónimo probados; sin transporte cloud, cobros, renovación automática ni avisos |
| Sesión/sincronización Android | VERIFIED_LOCAL | 7 pruebas de Keystore/adopción, cuentas separadas, originales con hash coincidente, ACK concurrente, conflictos, rollback ajeno, descarga íntegra y conservación ante fallo; transporte de prueba |
| API Worker D1/R2 | VERIFIED_PARTIAL_PRODUCTION | 19 tests Miniflare y ensayo HTTPS real de correo, sesión, replay, CAS, idempotencia, feed, subida/descarga SHA-256; ver integrations/PRODUCTION_VALIDATION.md |
| Bucket dedicado | DEPLOYED_PRIVATE | tickettracker-documents, Standard, jurisdicción UE, acceso público desactivado; conserva un PDF sintético de validación de 616 bytes |
| D1 dedicada | DEPLOYED | tickettracker, jurisdicción UE, migración inicial aplicada: 11 tablas tt_, 6 índices explícitos y 2 triggers |
| Worker | DEPLOYED | https://tickettracker-api.sergiotech.workers.dev, DB y R2 eu enlazados, Resend y pepper cifrados; cron solo limpia autenticación caducada |
| Análisis estático/formato | VERIFIED_LOCAL | Spotless/ktfmt y Lint Android sin errores; 56 avisos de versiones, objetivo SDK, recursos sin usar y estilo. Backend sin cambios, con la verificación previa registrada |
| WorkManager sync/recordatorios | IMPLEMENTED | Red, backoff, revisión cloud cada 6 h y avisos opt-in; Doze y dispositivo físico pendientes |
| Cámara y Sharesheet | IMPLEMENTED | Compilan; pruebas físicas y Gmail real pendientes |
| Fechas manuales | IMPLEMENTED | Reglas civiles probadas; no aplica plazos jurídicos automáticos |
| Exportación ZIP v2 | VERIFIED_LOCAL | Contratos solo del propietario actual y original sintético byte a byte; incluye manifiesto y hashes. No hay restaurador integrado |
| CI | VERIFIED_REFERENCE | Base aa10441: Worker, build/lint/JVM/APK y API 29/36 correctos en run 39053932665. Resultado remoto del nuevo incremento pendiente. PR Dependabot #11 incompatible Hilt/Kotlin, sin integrar |
| Identidad visual | VERIFIED_LOCAL | Composición compacta con tarjetas turquesa/violeta/naranja, cápsulas, pestañas, filas y «+» central. Contadores Garantía activa/Crítica/Devoluciones posibles probados; ver design/BRAND.md |
| Supabase/Coolify | HISTORICAL | Auditoría previa solo en lectura; descartados para el producto y sin modificaciones en el VPS |
| Deploy y recuperación cloud real | PARTIALLY_VERIFIED | Inventario aprobado y desplegado. Backend probado con documento sintético; recuperación entre dos instalaciones Android aún pendiente |
| Google real | CONFIGURED_NOT_VERIFIED | Proyecto independiente, clientes web/Android debug, identidad básica y usuario piloto configurados; nonce remoto correcto. Falta login real desde Android y firma de distribución |
| Gmail directo / motor jurídico / beta | NOT_STARTED | Requieren sus decisiones y validaciones específicas; Google Play no autorizado |

VERIFIED acredita únicamente la prueba indicada. VERIFIED_LOCAL no significa despliegue o recuperación real. CREATED acredita un recurso vacío, no una integración operativa.

## Comprobaciones ejecutadas

- `:domain:test`: 18 pruebas, cero fallos.
- Suite completa `:app:connectedDebugAndroidTest` en AVD propio API 36: 20 pruebas, cero fallos. Tras el último ajuste de posición del botón central: tres pruebas de interfaz afectadas, cero fallos.
- Corregida la navegación después de guardar contratos para volver a la lista en el hilo principal; el borrador y su revisión sobreviven a la recreación de Activity.
- APK 0.1.1: tres flujos Compose repetidos, cero fallos; revisión en claro/oscuro, español/inglés y fuente al 130 %. Se amplió el bloque Devoluciones y la altura de las etiquetas para conservar el texto completo en español ampliado; tres flujos repetidos tras el ajuste, cero fallos.
- `spotlessCheck`, `:app:lintDebug`, `:core:documents:lintDebug`, `:core:database:lintDebug`: correctos.
- `:app:assembleDebug` y `:app:assembleRelease`: correctos; APK release sin firma de distribución.
- APK personal: `apksigner verify --verbose --print-certs` correcto; certificado SHA-1 `26:3A:4B:18:60:7F:E2:CD:88:56:57:1E:BB:3F:34:7E:DF:AF:CE:D2`. `aapt dump badging` confirma Android 10+, target 36 y ABI arm64-v8a/armeabi-v7a/x86/x86_64. `adb install -r` y apertura de MainActivity correctos. SHA-256 `8af23578eb4fd2a02336fc8a6836480062f662bab4b08e5727bba355bcc80a32`.
- `node --test --test-concurrency=1 test/*.test.mjs`: 19 pruebas, cero fallos.
- Prettier y `wrangler deploy --dry-run`: correctos; solo empaquetado local, sin cambios cloud.

Los informes/capturas quedan en `.local/` y `build/`; APK, hash e instrucciones de instalación en `entregas/`, excluidos de Git. El AVD existente API 37.2 preliminar no era compatible con Espresso; se utilizó uno API 36 aislado del proyecto. API 29 queda preparada en CI, sin resultado local. El helper de migración presentó una incompatibilidad de serialización: la prueba usa ahora la apertura real de Room con el esquema anterior, sin desactivar su validación.

El nuevo diseño y la tabla Room v4 no alteran producción: D1, Worker, R2, Supabase y VPS permanecen como en el inventario previo. Las capturas pobladas usan fixtures marcados «prueba», retirados al terminar el test. La APK no incluye esos registros.

## Siguiente validación pendiente

La APK incluye la URL y audiencia Google públicas del piloto. Comprobar Google real y dos instalaciones Android, caída/reintento, conflictos, permisos e integridad antes de declarar recuperación. La red local agota el tiempo al conectar con las IP de workers.dev; desde el VPS HTTPS funciona. No se alteraron DNS, firewall ni reenvío SSH. El ensayo remoto de backend no acredita el transporte Android.

## Límites de esta entrega

Se enviaron dos códigos al propietario y se verificó su acceso; no se incluyeron en informes ni Git. Google permanece en modo de pruebas, sin Gmail y sin publicación en Play. El borrador de compra no sobrevive a la muerte del proceso. OCR PDF limitado a la primera página; artículos revisados manualmente. No hay recorte, perspectiva, multipágina, devoluciones parciales o reglas jurídicas automáticas. No hay restaurador ZIP, eliminación de cuenta, backup independiente D1/R2 ensayado ni compactación del feed. No se eliminan originales ni se promete recuperación en producción. Rendimiento, precisión OCR y costes reales no se han medido con carga.
