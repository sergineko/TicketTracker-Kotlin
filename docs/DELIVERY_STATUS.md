# Estado de entrega

Actualizado el 21/10/2026. Versión de pruebas **0.1.6**, versionCode 7. **El MVP cloud no está terminado.** Cloudflare D1 + Worker + R2 privado y acceso por correo/Google (ADR 0002). Suscripciones y seguros locales (ADR 0003). Imagen remota optimizada y original local (ADR 0005). Lectura de tickets y correo opcional: ADR 0006. Facturas y PDF multipágina: ADR 0007. Lectura por columnas y registro compacto: ADR 0008.

## Evidencia actual

| Área | Estado | Evidencia y límites |
| --- | --- | --- |
| APK personal 0.1.6 | VERIFIED_LOCAL | Universal Android 10+, 124110461 bytes, misma firma debug del piloto Google; instalada como actualización en AVD propio API 36 |
| Dominio | VERIFIED_LOCAL | 45 pruebas JVM, cero fallos. Plan de columnas es/en con orden variable; ropa, ferretería, restauración, farmacia y servicios sintéticos. Fechas con mes escrito, emisor con números y cantidades fraccionarias rechazadas sin redondear |
| Android | VERIFIED_LOCAL | Suite final OK (56 tests): 54 funcionales, dos sondas privadas omitidas sin entrada. Las sondas con documentos reales se ejecutaron expresamente por separado |
| OCR real | VERIFIED_LOCAL | Regresión 0.1.6: Mercadona conserva comercio, fecha, total y nueve artículos; MediaMarkt conserva fecha, total, artículo/cantidad/precio usando solo OCR en API 36. La prueba de texto digital de ese documento consta en 0.1.5. No acredita precisión universal |
| Compresión digital | VERIFIED_LOCAL | Regresión de imagen optimizada correcta en 0.1.6. Medición de 0.1.4: captura de 101054 bytes → WebP sin pérdida de 51212 bytes (49,3 % menos), PSNR 100 y tokens 100 %. Original local preservado; sin subir documentos de validación a R2 |
| PDF/importación | VERIFIED_LOCAL | Hasta diez páginas y 100000 caracteres; aviso parcial si supera límites o hay páginas sin texto. API 35+ compara texto nativo/OCR; API 29–34 usa OCR. PDF íntegro, SHA-256, corrupción y límites comprobados; fixture de once páginas verifica conservación y aviso. Campos desconocidos vacíos y edición manual preservada |
| Gmail Android | VERIFIED_LOCAL | Autorización opcional independiente; cabeceras/metadatos, paginación, adjunto seleccionado y límite 25 MiB. Transporte controlado, tamaños, errores sin resultados ficticios, cambio de propietario y consentimiento obsoleto probados. Sin lectura real de buzón por el agente |
| Gmail Google Cloud | CONFIGURED | API habilitada y gmail.readonly guardado según inventario aprobado. Segundo usuario de pruebas autorizado y ambas cuentas verificadas en consola; continúa modo Prueba. No equivale a verificación pública. Consentimiento e importación reales desde móvil pendientes |
| Compartir | VERIFIED_LOCAL | SEND, SEND_MULTIPLE, ClipData y Abrir con PDF/imagen; texto seleccionado con revisión y sin documento inventado. Flujo real de Intent → revisión Compose probado en AVD. Aplicaciones de correo de un móvil físico pendientes |
| Room v4 | VERIFIED_LOCAL | Sin migración en este incremento. Reimportación actualiza el OCR local sin duplicar documentos. Rechazo de importaciones preparadas para otro propietario; migraciones, CAS y outbox anteriores pasan |
| Sincronización | VERIFIED_LOCAL / DEPLOYED | Once casos Android con Room/Keystore y transporte controlado pasan. Worker y D1/R2 conservan el despliegue 0.1.3; este incremento no los modifica. Recuperación real entre móviles pendiente |
| Cuenta y Google | USER_REPORTED / VERIFIED_LOCAL | Propietario confirmó login Google en su móvil; pantalla con identidad y estado probada. El login de identidad no concede acceso a Gmail |
| Diseño y registro | VERIFIED_LOCAL | Formulario con importación inicial y apertura del justificante, datos básicos primero, artículos y opciones plegables, acción fija de guardar, errores por campo y suma explícita. Compose y capturas sintéticas en claro/oscuro; fuente 130 %. Contadores e identidad anteriores conservados |
| Suscripciones/seguros | LOCAL_ONLY | CRUD, aislamiento y ZIP. Sin transporte cloud, avisos, cobros ni renovación automática |
| Compilación/estático | VERIFIED_LOCAL | Debug, APK de tests y release optimizada sin firma correctas; Lint app/database/documents sin errores, con avisos de SDK/versiones/recursos/estilo. Spotless aplicado |
| CI 0.1.6 | VERIFIED | Código 258c449a16050ef2735bb7b6d53d486b5ffc2cda, [run 39654239703](https://github.com/sergineko/TicketTracker-Kotlin/actions/runs/39654239703): backend, build y suites API 29/36 completados con éxito. PR #11 Kotlin/Hilt incompatible sigue sin integrar |
| Producción Cloudflare | DEPLOYED (18/10) | Worker b6be4643cbc04222a56244fc7c5d03d0; hash 95552df3e9235539afcce37b5f30a627a8da095295b1a8a4df7dcefc22e3d171. 30 pruebas Miniflare y health/storage verificadas en 0.1.3 |
| R2/D1 reales | VERIFIED (18/10) | Dos WebP verificados por hash/tamaño y documentos en revisión 4; limpieza de los dos JPEG aprobada y terminada. 2 imágenes (1933580 bytes) + 1 PDF (616), total 1934196. Ahorro 68,9 %. Originales locales preservados; no se repite esa limpieza |
| Supabase/Coolify | HISTORICAL_READ_ONLY | Fuera del producto; sin cambios en VPS, bases compartidas ni otros proyectos |

VERIFIED_LOCAL acredita solo la prueba indicada. Imágenes, documentos originales, texto OCR privado y credenciales no se incluyen en Git ni en las pruebas de CI. Los fixtures automáticos son sintéticos y no forman parte del inventario de la APK.

## APK y validación

`entregas/TicketTracker-0.1.6-pruebas.apk`. SHA-256 `eab3c80f39ae532405a6eedc8c05c12598fb38a0b9bd31927cb5da3e60726339`. Certificado SHA-1 `26:3A:4B:18:60:7F:E2:CD:88:56:57:1E:BB:3F:34:7E:DF:AF:CE:D2`, registrado en Google. ABI arm64-v8a/armeabi-v7a/x86/x86_64, minSdk 29, targetSdk 36. No incluye tokens ni secretos administrativos/OAuth. Instalar como actualización, sin desinstalar la versión anterior.

Ejecutados dominio, Spotless, assembleDebug, assembleDebugAndroidTest, assembleRelease, Lint y suite instrumentada mediante ADB. Evidencia privada 0.1.6 en .local/adaptive-editor/: build-final.log, suite-final.log, lint-verified.log, import-final.log, invoice-regression.log, mercadona-regression.log y capturas editor-light-preview.png/editor-dark-preview.png. La suite completa valida 54 casos funcionales; las dos sondas sin entrada privada se omiten y se ejecutan por separado con entradas reales. Tras reforzar el rechazo de cantidades fraccionarias, las 13 pruebas instrumentadas de importación afectadas pasan de nuevo; el CI completo también pasa en API 29 y 36. AVD propio emulator-5562 API 36; no se utiliza el emulador del propietario. Evidencia remota: .local/adaptive-editor/ci-evidence.json. Emulador propio y daemon Gradle cerrados; copias temporales privadas retiradas del emulador y originales locales preservados. Los documentos privados no se incluyen en Git ni CI. Las capturas son fixtures sintéticos y no forman parte del inventario de la APK.

Room v4 y los contratos remotos no cambian. La fecha introducida como DD/MM/AAAA se convierte a LocalDate al guardar. Suma e importe de compra permanecen independientes; la suma solo se aplica mediante elección del usuario. Las filas ambiguas se señalan para revisar, sin crear cantidades fraccionarias redondeadas ni completar plazos legales.

## Producción y límites

La configuración Gmail se limita al inventario aprobado GMAIL_DEPLOYMENT.md y al añadido privado aprobado de la segunda cuenta. El permiso es de lectura del buzón, opcional y separado de la sesión TicketTracker. La app no consulta cuerpos HTML ni sigue enlaces; solo importa un adjunto elegido. El consentimiento y acceso real deben comprobarse en el móvil del propietario. Google sigue en modo Prueba y los requisitos de verificación para distribución pública no están completados; puede mostrar un aviso de aplicación no verificada incluso a usuarios de pruebas.

La política de imagen única cloud y los inventarios de limpieza anteriores permanecen aplicados. Los controles de compresión no garantizan lectura universal QR/barras ni OCR exacto. La fotografía grande puede quedar pendiente si ninguna copia pasa los controles; nunca se sube como alternativa silenciosa. El OCR y texto PDF procesan hasta diez páginas/100000 caracteres, conservan el original íntegro y no infieren derechos legales. Formatos desconocidos o ambiguos necesitan revisión manual.

Pendientes: recuperación real Android entre instalaciones, notificaciones bajo Doze, recorte/perspectiva y lectura por encima de los límites, motor jurídico, devolución parcial, eliminación de cuenta, restaurador ZIP, backup independiente D1/R2 ensayado y compactación del feed. El borrador no sobrevive a la muerte del proceso. Sin medición de precisión/costes bajo carga. Conservar exportaciones y el último original antes de desinstalar o cambiar de móvil.
