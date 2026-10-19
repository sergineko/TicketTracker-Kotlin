# Estado de entrega

Actualizado el 20/10/2026. Versión de pruebas **0.1.4**, versionCode 5. **El MVP cloud no está terminado.** Cloudflare D1 + Worker + R2 privado y acceso por correo/Google (ADR 0002). Suscripciones y seguros locales (ADR 0003). Imagen remota optimizada y original local (ADR 0005). Lectura de tickets y correo opcional: ADR 0006.

## Evidencia actual

| Área | Estado | Evidencia y límites |
| --- | --- | --- |
| APK personal 0.1.4 | VERIFIED_LOCAL | Universal Android 10+, 126072725 bytes, misma firma debug del piloto Google; instalada como actualización en AVD propio API 36 |
| Dominio | VERIFIED_LOCAL | 28 pruebas JVM, cero fallos; incluye cabeceras fiscales, filas físicas, total/IVA, cantidades y precios coherentes |
| Android | VERIFIED_LOCAL | Suite final OK (47 tests): 45 funcionales, dos sondas privadas omitidas sin entrada. OCR real y compresión se ejecutaron expresamente por separado |
| OCR real | VERIFIED_LOCAL | Captura privada aportada: comercio, fecha, total y nueve líneas reconocidos; cantidad y precio de la línea de dos unidades comprobados. No acredita precisión universal; las descripciones siguen siendo editables |
| Compresión digital | VERIFIED_LOCAL | La captura de 101054 bytes queda en WebP sin pérdida de 51212 bytes (49,3 % menos), PSNR 100 y retención de tokens 100 %. Original local preservado. No se subió a R2 durante esta validación |
| PDF/importación | VERIFIED_LOCAL | Texto nativo en API 35+ comparado con OCR; primera página. API 29–34 usa OCR. PDF almacenado íntegro, límites, corrupción y SHA-256 comprobados. Fecha no reconocida vacía; conserva campos escritos |
| Gmail Android | VERIFIED_LOCAL | Autorización opcional independiente; cabeceras/metadatos, paginación, adjunto seleccionado y límite 25 MiB. Transporte controlado, tamaños, errores sin resultados ficticios, cambio de propietario y consentimiento obsoleto probados. Sin lectura real de buzón por el agente |
| Gmail Google Cloud | CONFIGURED | Inventario GMAIL_DEPLOYMENT.md aprobado y aplicado el 20/10. API habilitada y gmail.readonly guardado; modo Prueba, usuario y clientes conservados. Consentimiento y búsqueda reales desde el móvil pendientes |
| Compartir | VERIFIED_LOCAL | SEND, SEND_MULTIPLE, ClipData y Abrir con PDF/imagen; texto seleccionado con revisión y sin documento inventado. Flujo real de Intent → revisión Compose probado en AVD. Aplicaciones de correo de un móvil físico pendientes |
| Room v4 | VERIFIED_LOCAL | Sin migración en este incremento. Reimportación actualiza el OCR local sin duplicar documentos. Rechazo de importaciones preparadas para otro propietario; migraciones, CAS y outbox anteriores pasan |
| Sincronización | VERIFIED_LOCAL / DEPLOYED | Once casos Android con Room/Keystore y transporte controlado pasan. Worker y D1/R2 conservan el despliegue 0.1.3; este incremento no los modifica. Recuperación real entre móviles pendiente |
| Cuenta y Google | USER_REPORTED / VERIFIED_LOCAL | Propietario confirmó login Google en su móvil; pantalla con identidad y estado probada. El login de identidad no concede acceso a Gmail |
| Diseño | VERIFIED_LOCAL | Marca clara/oscura integrada, referencia compacta y contadores Garantía activa / Crítica / Devoluciones posibles; cuenta y nuevos controles Gmail comprobados en Compose |
| Suscripciones/seguros | LOCAL_ONLY | CRUD, aislamiento y ZIP. Sin transporte cloud, avisos, cobros ni renovación automática |
| Compilación/estático | VERIFIED_LOCAL | Debug, APK de tests y release optimizada sin firma correctas; Lint app/database/documents sin errores, con avisos de SDK/versiones/recursos/estilo. Spotless aplicado |
| CI 0.1.4 | PENDING | Pendiente de publicar el código final y observar build, Worker y dispositivos API 29/36. CI 0.1.3 (39127114216, código 7c0f07c) terminó con los cuatro jobs correctos |
| Producción Cloudflare | DEPLOYED (18/10) | Worker b6be4643cbc04222a56244fc7c5d03d0; hash 95552df3e9235539afcce37b5f30a627a8da095295b1a8a4df7dcefc22e3d171. 30 pruebas Miniflare y health/storage verificadas en 0.1.3 |
| R2/D1 reales | VERIFIED (18/10) | Dos WebP verificados por hash/tamaño y documentos en revisión 4; limpieza de los dos JPEG aprobada y terminada. 2 imágenes (1933580 bytes) + 1 PDF (616), total 1934196. Ahorro 68,9 %. Originales locales preservados; no se repite esa limpieza |
| Supabase/Coolify | HISTORICAL_READ_ONLY | Fuera del producto; sin cambios en VPS, bases compartidas ni otros proyectos |

VERIFIED_LOCAL acredita solo la prueba indicada. Imágenes, documentos originales, texto OCR privado y credenciales no se incluyen en Git ni en las pruebas de CI. Los fixtures automáticos son sintéticos y no forman parte del inventario de la APK.

## APK y validación

`entregas/TicketTracker-0.1.4-pruebas.apk`. SHA-256 `08e94190b81c577a93c81754125d6ae77bc6f894af9f13e3be33fe6513e6cab7`. Certificado SHA-1 `26:3A:4B:18:60:7F:E2:CD:88:56:57:1E:BB:3F:34:7E:DF:AF:CE:D2`, registrado en Google. ABI arm64-v8a/armeabi-v7a/x86/x86_64, minSdk 29, targetSdk 36. No incluye tokens ni secretos administrativos/OAuth. Instalar como actualización, sin desinstalar la versión anterior.

Ejecutados dominio, Spotless, assembleDebug, assembleDebugAndroidTest, assembleRelease, Lint y suite instrumentada final mediante ADB. Evidencia privada: .local/ocr-mail-final-build.log, .local/ocr-mail-final-suite.log, .local/ocr-mail-share-ui.log y .local/ocr-validation/. AVD propio emulator-5562 API 36; no se usa el emulador API 37.2 del propietario. Un fallo de cierre del escenario de Compartir se corrigió restaurando el Intent de lanzamiento del test; recorrido y suite final pasan. API 29 se comprobará en CI.

## Producción y límites

La configuración Gmail se limita al inventario aprobado GMAIL_DEPLOYMENT.md. El permiso es de lectura del buzón, opcional y separado de la sesión TicketTracker. La app no consulta cuerpos HTML ni sigue enlaces; solo importa un adjunto elegido. El consentimiento y acceso real deben comprobarse en el móvil del propietario. Google sigue en modo Prueba y los requisitos de verificación para distribución pública no están completados.

La política de imagen única cloud y los inventarios de limpieza anteriores permanecen aplicados. Los controles de compresión no garantizan lectura universal QR/barras ni OCR exacto. La fotografía grande puede quedar pendiente si ninguna copia pasa los controles; nunca se sube como alternativa silenciosa. El OCR y texto PDF solo procesan la primera página y no infieren derechos legales.

Pendientes: recuperación real Android entre instalaciones, notificaciones bajo Doze, recorte/perspectiva/multipágina, motor jurídico, devolución parcial, eliminación de cuenta, restaurador ZIP, backup independiente D1/R2 ensayado y compactación del feed. El borrador no sobrevive a la muerte del proceso. Sin medición de precisión/costes bajo carga. Conservar exportaciones y el último original antes de desinstalar o cambiar de móvil.
