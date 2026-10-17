# TicketTracker

Aplicación Android nativa de Sergiotech para organizar compras, justificantes, garantías y devoluciones. Kotlin, Compose, Material 3. Paquete `es.sergiotech.tickettracker`, Android 10/API 29 o superior; objetivo API 36.

## Incremento 0.1

- Alta, edición, borrado lógico y búsqueda de compras con artículos y fechas independientes.
- Room observable, importes enteros, revisiones y outbox transaccional.
- CameraX, SAF, compartir PDF/imagen desde otras apps y OCR local ML Kit.
- Originales privados, miniaturas, SHA-256, duplicados por propietario y límite de 25 MiB.
- Temas, español/inglés, recordatorios opt-in y exportación ZIP con manifiesto.
- Iconos aportados por el propietario, paleta turquesa y resumen de compras/fechas con navegación central; [referencia visual](docs/design/BRAND.md).
- Adaptadores Cloudflare: sesiones cifradas Keystore, códigos de correo, Google, sincronización incremental, conflictos y descarga íntegra bajo demanda.
- Worker con D1/R2 privados, autorización por propietario, cuotas e idempotencia; pruebas locales sin credenciales.

**El MVP cloud no está terminado.** El Worker está publicado con D1 `tickettracker` y R2 `tickettracker-documents` privados en jurisdicción UE. Correo y operaciones D1/R2 tienen una prueba HTTPS real con un documento sintético; la APK incluye la URL y audiencia Google públicas del piloto. Google desde Android y la recuperación entre instalaciones siguen pendientes. Desinstalar borra los datos del dispositivo: conserva una exportación hasta validar la recuperación.

La decisión posterior del propietario sustituye Supabase por D1 + Worker + R2. El VPS y los otros proyectos no se modifican. El código de Google requiere clientes OAuth válidos y huellas de firma; iniciar sesión no concede acceso a Gmail. El OCR de PDF procesa la primera página y conserva el original completo. Recorte, perspectiva, multipágina y motor jurídico siguen en el roadmap.

## Compilar Android

Abrir con Android Studio compatible con AGP 9.1, Gradle 9.3.1 y JDK 21 o posterior. Instalar SDK 36 y Build Tools 36.0.0. Android Studio crea `local.properties`, excluido de Git. El bytecode usa Java 17.

```powershell
./tools/android.ps1 validate
./tools/android.ps1 device
./tools/android.ps1 release
```

En Windows, el script selecciona el JDK de Android Studio y usa `.local/` para Gradle. Para comandos directos:

```text
./gradlew spotlessCheck :domain:test :app:lintDebug :core:documents:lintDebug :core:database:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease
```

La release está optimizada y sin firma de distribución. La APK debug se genera en `app/build/outputs/apk/debug/app-debug.apk`. API 36 se ha probado localmente; CI ha pasado en API 29 y 36.

`gradle.properties` contiene la configuración **pública** del piloto. Puede sustituirse con `-PtickettrackerApiUrl=https://…` y `-PtickettrackerGoogleClientId=…`; para una compilación solo local, pasar ambas propiedades vacías. El segundo es el cliente web de audiencia del token. Google debug solo admite la huella registrada en [GOOGLE_AUTH.md](docs/integrations/GOOGLE_AUTH.md); otra firma necesita su cliente Android. Nunca introducir claves Cloudflare, Resend, S3 ni administrativas en el APK.

## API y estructura

| Carpeta | Responsabilidad |
| --- | --- |
| domain | Kotlin sin dependencias Android ni proveedores; modelos, contratos y reglas |
| core/database | Room normalizado, migraciones 1→2→3, outbox, cursores y conflictos |
| core/documents | Archivos privados, OCR, límites e integridad |
| core/designsystem | Material 3 |
| app | Compose, ViewModels, adaptadores, Keystore, sincronización y trabajos |
| backend/cloudflare | Worker, migración SQLite, tests D1/R2/Google y configuración |
| docs | Producto, decisiones, diseño, evidencia e inventario de producción |

Desde `backend/cloudflare`, con Node 22+: `npm ci`, `npm test`, `npm run format:check` y `npm run deploy:check`. No hacen falta credenciales para los tests. No se despliega desde CI.

El [estado central](docs/DELIVERY_STATUS.md) distingue código, pruebas locales y producción. El [inventario aprobado](docs/integrations/CLOUDFLARE_DEPLOYMENT.md) y la [validación real](docs/integrations/PRODUCTION_VALIDATION.md) registran el despliegue y sus límites. [ADR 0002](docs/architecture/ADR/0002_cloudflare_d1.md) documenta la sustitución de Supabase. La especificación de origen conserva el concepto del Word y señala las decisiones posteriores; el Word y las credenciales quedan fuera de Git.

Identidad visual basada en los recursos aportados por el propietario. Todos los derechos reservados por el propietario; no se asigna una licencia de software libre sin su decisión.
