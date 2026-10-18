# Estado de entrega

Actualizado el 18/10/2026. Versión de pruebas **0.1.3**, versionCode 4. **El MVP cloud no está terminado.** Cloudflare D1 + Worker + R2 privado, acceso por código de correo y Google (ADR 0002). Suscripciones y seguros siguen locales (ADR 0003). Una imagen optimizada remota y original local: ADR 0005, que sustituye la parte de almacenamiento cloud de ADR 0004.

## Evidencia actual

| Área | Estado | Evidencia y límites |
| --- | --- | --- |
| APK personal 0.1.3 | VERIFIED_LOCAL | Universal Android 10+, 123824433 bytes, firma debug v2 del piloto Google. Instalación y suite en AVD propio API 36 |
| Dominio | VERIFIED_LOCAL | 23 pruebas JVM, cero fallos: importes, fechas civiles, contadores y propuestas OCR conservadoras |
| Android | VERIFIED_LOCAL | Suite final OK (31 tests): 30 casos funcionales sin fallos y una sonda de imagen privada omitida al no aportar entrada; esa sonda se ejecutó expresamente con las dos imágenes reales, fuera de CI |
| Room v4 | VERIFIED_LOCAL | Migraciones 1/2/3→4, compras/líneas/productos separados, CAS, outbox y contratos por cuenta. Sin migración de esquema en este incremento |
| Imagen única cloud | VERIFIED_LOCAL / DEPLOYED | Solo OPTIMIZED, hasta 2 MiB y nunca mayor que el origen. WebP con control visual y de datos OCR; original y miniaturas locales. No sube el original grande si falla la optimización. PDF único íntegro |
| Recuperación/exportación | VERIFIED_LOCAL | Descarga de copia optimizada sin ORIGINAL, SHA-256 y reemplazo atómico; ZIP v3 indica originales ausentes. Reimportar el original de una imagen recuperada lo conserva y evita duplicar el documento. No acredita recuperación real entre móviles |
| Transporte Android | VERIFIED_LOCAL | Once casos con Room/Keystore reales y transporte controlado: cuentas separadas, ACK, conflictos, rollback, subida solo OPTIMIZED, rebasing de outbox antigua, integridad, copia optimizada, bloqueo del fallback grande y reintento de limpieza sin fuente remota |
| OCR y borrador | VERIFIED_LOCAL | Comercio, fecha, total, artículos/cantidades/precios y fechas absolutas impresas. Aplicación por producto sin sustituir campos escritos. PDF OCR solo primera página; no calcula plazos legales |
| Cuenta y Google | USER_REPORTED / VERIFIED_LOCAL | El propietario confirmó Google correcto en su móvil. Pantalla con identidad, estado y alcance; acceso/cierre secundarios. No se afirma un nuevo login real del agente en este incremento |
| Diseño | VERIFIED_LOCAL | Marca transparente sobre fondo nativo claro/oscuro, resumen según referencia y contadores Garantía activa / Crítica / Devoluciones posibles. Los seis flujos Compose ya se probaron con fuente al 130 % en oscuro en 0.1.2 |
| Suscripciones/seguros | LOCAL_ONLY | CRUD, aislamiento, adopción y ZIP. Sin transporte cloud, avisos, cobros ni renovación automática |
| Worker | DEPLOYED / VERIFIED_LOCAL | 30 pruebas Miniflare sin fallos: idempotencia, cuotas, aislamiento, PDF con preview antigua, conversión por etapas, corrupción, interrupciones y concurrencia. Prettier y dry-run correctos |
| Producción actual | DEPLOYED | Worker b6be4643cbc04222a56244fc7c5d03d0, hash 95552df3e9235539afcce37b5f30a627a8da095295b1a8a4df7dcefc22e3d171. Configuración, secretos cifrados, cuotas y cron conservados; health 200, storage sin sesión 401 |
| R2/D1 reales | VERIFIED | Dos WebP descargados desde R2 con tamaño/hash coincidentes; dos documentos en revisión 4 y OPTIMIZED_IMAGE. Tras confirmación final se retiraron solo los dos JPEG anteriores. Quedan 2 imágenes (1933580 bytes) + 1 PDF (616), total 1934196 bytes; ahorro 68,9 % frente a 6217491. Originales locales verificados y preservados |
| Compilación/estático | VERIFIED_LOCAL | Debug y release optimizada sin firma, dominio y Lint app/database/documents sin errores. Avisos pendientes de versiones/SDK/recursos/estilo; no equivalen a errores de compilación |
| CI 0.1.3 | VERIFIED | Código 7c0f07c, run 39127114216: Worker, build y dispositivos API 29/36 completados correctamente. CI anterior 57485dd también correcto. PR #11 Kotlin/Hilt incompatible sigue sin integrar |
| Supabase/Coolify | HISTORICAL_READ_ONLY | Fuera del producto; sin cambios en VPS, bases compartidas ni otros proyectos |

VERIFIED_LOCAL acredita solo la prueba indicada. Los fixtures sintéticos y las capturas de pruebas no se incluyen en la APK. Las imágenes reales se procesaron mediante entrada local explícita y no forman parte de los tests ni de Git.

## APK y validación

`entregas/TicketTracker-0.1.3-pruebas.apk`. SHA-256 `1a71fa6b9b020b695d96eb5959b0dd46c4ab2005225b202c82797d35eec1a052`. Certificado SHA-1 `26:3A:4B:18:60:7F:E2:CD:88:56:57:1E:BB:3F:34:7E:DF:AF:CE:D2`, registrado en Google. ABI arm64-v8a/armeabi-v7a/x86/x86_64, minSdk 29, targetSdk 36. No incluye claves administrativas, tokens ni secretos OAuth. Instalar como actualización con la misma firma; las versiones de prueba anteriores asumían ORIGINAL en el formato remoto.

Ejecutados dominio, Spotless, assembleDebug, assembleDebugAndroidTest, assembleRelease y Lint, suite instrumentada mediante ADB sobre el código final. Worker: node --test --test-concurrency=1 test/*.test.mjs, Prettier y deploy --dry-run. Logs/capturas en .local/ y build/, excluidos de Git. AVD API 36 propio; no se usa el emulador API 37.2 del propietario. La release sin firma no es la APK debug entregada. API 29 se comprueba mediante CI, sin nueva ejecución local.

## Producción y límites

El inventario `integrations/OPTIMIZED_IMAGES_DEPLOYMENT.md` se aprobó y ejecutó con confirmación final del borrado. Descargas reales R2, hashes, revisión/feed y repetición idempotente comprobados. Evidencia privada en .local/optimized-r2-verification.json, .local/optimized-storage-proof.json y .local/optimized-r2-after.png. Wrangler cerrado. La limpieza previa de cuatro derivados también terminó y consta en su inventario histórico.

Los controles de compresión no garantizan OCR exacto ni lectura universal QR/barras. La foto con texto fino conserva su resolución completa; la app puede dejar pendiente una imagen que no logre comprimir con calidad suficiente. PDF no se rasteriza. El ZIP sigue sin restaurador integrado.

Google continúa en modo de pruebas, sin Gmail ni publicación en Play. Quedan por validar recuperación real Android entre instalaciones con sesión, cámara/compartir con otras apps en un móvil físico y notificaciones bajo Doze. No hay recorte/perspectiva/multipágina, motor jurídico, devolución parcial, eliminación de cuenta, backup independiente D1/R2 ensayado o compactación de feed. El borrador no sobrevive a la muerte del proceso. Sin medición de precisión/costes bajo carga. Conservar exportaciones y el último original antes de desinstalar o cambiar de móvil.
