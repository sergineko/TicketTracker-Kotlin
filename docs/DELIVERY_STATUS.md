# Estado de entrega

Actualizado el 18/10/2026. Versión de pruebas **0.1.2**. **El MVP cloud no está terminado.** Backend D1 + Worker + R2 privado y acceso por código de correo + Google (ADR 0002). Suscripciones y seguros siguen siendo locales (ADR 0003). Original único y propuestas OCR: ADR 0004. Nuevo despliegue de almacenamiento preparado, pendiente de aprobación.

## Evidencia actual

| Área | Estado | Evidencia y límites |
| --- | --- | --- |
| APK personal 0.1.2 | VERIFIED_LOCAL | versionCode 3, universal Android 10+, 123462149 bytes; firma debug v2 registrada para Google. Instalado y probado en AVD API 36 |
| Dominio | VERIFIED_LOCAL | 23 pruebas JVM: importes, fechas civiles, contadores, artículos/cantidades/monedas y propuestas OCR conservadoras |
| Android | VERIFIED_LOCAL | Suite completa de 27 pruebas, cero fallos, en AVD propio API 36; seis flujos Compose repetidos con fuente al 130 % en oscuro, cero fallos |
| Room v4 | VERIFIED_LOCAL | Migraciones 1/2/3→4, compras/líneas/productos separados, CAS, outbox, contratos por cuenta y exportación ZIP v2 con originales íntegros |
| Documentos/OCR | VERIFIED_LOCAL | OCR real de imagen reconoce artículo, total y fecha impresa de devolución. Original con hash idéntico, PDF byte a byte, miniatura regenerable y rechazo de corrupto/tamaño excesivo |
| Borrador OCR | VERIFIED_LOCAL | Propone artículos sin sustituir campos escritos. Fechas impresas exigen aplicar por producto; prueba de selección y recreación. No calcula plazos legales ni periodos |
| Transporte Android | VERIFIED_LOCAL | Ocho pruebas con Room/Keystore reales y transporte controlado: cuentas separadas, conflictos, ACK, rollback, descarga íntegra y conservación ante fallo. Solo ORIGINAL en nueva outbox; payload anterior de tres variantes permanece compatible |
| Cuenta y Google | USER_REPORTED / VERIFIED_LOCAL | El propietario confirma que Google crea sesión en su móvil. Nueva pantalla con identidad, estado y alcance de sync, acceso secundario y cierre con limpieza de Credential Manager. Estados conectado/caducado/pendiente/error probados; no equivalen a un nuevo login real del agente |
| Diseño | VERIFIED_LOCAL | Cabecera con símbolo transparente sobre fondo nativo claro/oscuro, resumen según referencia, garantías activas/críticas/devoluciones y contratos locales. No usa el icono con placa blanca en la cabecera |
| Suscripciones/seguros | LOCAL_ONLY | CRUD, aislamiento, adopción y ZIP probados. Sin transporte cloud, avisos, cobros ni renovación automática |
| Worker nuevo | VERIFIED_LOCAL_NOT_DEPLOYED | 25 tests Miniflare, cero fallos: idempotencia, cuotas, aislamiento, original corrupto/ausente, limpieza interrumpida y subida antigua concurrente. Prettier y dry-run correctos |
| Worker actual | DEPLOYED_PREVIOUS_VERSION | https://tickettracker-api.sergiotech.workers.dev con D1/R2 UE privados, Resend y pepper cifrados, cron solo de autenticación. El nuevo inventario de almacenamiento requiere aprobación |
| R2 actual | READ_ONLY_INVENTORY | Último inventario: tres originales registrados/verificados, 6217491 bytes, y cuatro derivados, 1412320 bytes. El inventario aumentó por nuevas subidas durante la revisión. No se han borrado copias ni originales |
| Producción anterior | PARTIALLY_VERIFIED | Ensayo HTTPS real de correo, sesión, replay, CAS, feed y R2 SHA-256 con PDF sintético de 616 bytes. Ver integrations/PRODUCTION_VALIDATION.md |
| Compilación y estático | VERIFIED_LOCAL | Debug, release optimizada sin firma, Spotless y Lint app/database/documents: cero errores, 67 avisos de versiones/SDK/recursos/estilo |
| CI | FIX_PREPARED | f207662: build y Worker correctos; resumen falló en API 29/36 al buscar un elemento fuera de pantalla (run 39076808794). Reproducido y corregido con espera de datos y scroll; resultado remoto nuevo pendiente. PR #11 Kotlin/Hilt incompatible sin integrar |
| Supabase/Coolify | HISTORICAL_READ_ONLY | Fuera del producto. No se modifican VPS, bases compartidas ni otros proyectos |

VERIFIED_LOCAL acredita solo la prueba indicada; no acredita recuperación real entre móviles. USER_REPORTED distingue la observación del propietario de las pruebas realizadas por el agente.

## Entrega y comprobaciones

APK en `entregas/TicketTracker-0.1.2-pruebas.apk`, con hash e instrucciones. SHA-256 `f06794cd5bf5a16f77e8494216210dc315deeeef909de3224d60fc72b777e57c`. Certificado SHA-1 `26:3A:4B:18:60:7F:E2:CD:88:56:57:1E:BB:3F:34:7E:DF:AF:CE:D2`; ABI arm64-v8a/armeabi-v7a/x86/x86_64, minSdk 29 y targetSdk 36. La APK no contiene registros de demostración, claves Cloudflare/Resend ni secretos OAuth.

Ejecutados `:domain:test`, `spotlessCheck`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:assembleRelease`, Lint de app/database/documents y la suite instrumentada mediante ADB sobre el APK exacto. Backend: `node --test --test-concurrency=1 test/*.test.mjs`, Prettier y `wrangler deploy --dry-run`. Informes y capturas en `.local/` y `build/`, excluidos de Git; capturas pobladas solo con fixtures identificados como prueba.

El AVD API 36 es propio del proyecto; no se utiliza el emulador API 37.2 del propietario. API 29 está en CI, sin nueva ejecución local. La release optimizada no es el APK debug firmado que se entrega. Las copias anteriores se conservan en el archivo local de artefactos.

## Próximo paso de producción

Revisar y aprobar `integrations/STORAGE_OPTIMIZATION_DEPLOYMENT.md`: publicar el Worker con los mismos bindings/secretos/cuotas y retirar únicamente los cuatro derivados del manifiesto inventariado después de comprobar sus originales. No hay tablas nuevas, migraciones, cambio de plan, bucket público o limpieza de originales. El botón de consulta/optimización necesita ese Worker publicado; las nuevas subidas Android de original único son compatibles con el Worker anterior.

La comprobación HTTPS `/health` ya responde 200 desde este equipo; el timeout local documentado anteriormente no se reproduce en esta revisión. Queda pendiente validar el transporte Android real y la recuperación de originales en otra instalación antes de afirmar recuperación cloud completa.

## Límites

Google sigue en modo de pruebas, sin Gmail ni publicación en Play. Cámara, Sharesheet con apps reales, notificaciones bajo Doze y dispositivo físico requieren validación. OCR PDF solo procesa la primera página; las propuestas de artículos/fechas pueden requerir corrección. La regla de fecha impresa no entiende condiciones jurídicas completas o situadas en otra línea y siempre exige revisión. No hay recorte/perspectiva/multipágina, motor jurídico, devolución parcial, restaurador ZIP, eliminación de cuenta, backup independiente D1/R2 ensayado o compactación de feed. El borrador de compra no sobrevive a la muerte del proceso. No se han medido precisión/costes bajo carga. No borrar el último original ni depender aún de la nube para desinstalar o cambiar de móvil.
