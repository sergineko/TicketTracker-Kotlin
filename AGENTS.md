# TicketTracker

Leer `docs/DELIVERY_STATUS.md`, `docs/product/REQUIREMENTS.md` y las decisiones de arquitectura antes de cambiar el proyecto.

- App Android nativa Kotlin/Compose/Material 3, paquete `es.sergiotech.tickettracker`, minSdk 29 y targetSdk 36.
- Room es la fuente observable de la interfaz. Dominio sin dependencias de Android/Supabase/Cloudflare.
- Compras, líneas y productos separados. Importes en unidades menores enteras, fechas civiles con LocalDate.
- Backend vigente: Cloudflare D1 en base exclusiva `tickettracker`, Worker propio y R2 privado dedicado (ADR 0002). No reutilizar bases de otros proyectos. Supabase compartido queda fuera del producto; no usar `public` ni modificarlo.
- La infraestructura real se consulta solo en lectura hasta que el propietario apruebe un inventario concreto de cambios de producción. Nunca guardar claves en Git, logs, pruebas o APK.
- No eliminar el último original local ni afirmar sincronización o recuperación sin prueba real. Los fallos de OCR deben conservar el documento.
- No inferir plazos legales por el mero canal o fecha del ticket. Las reglas futuras serán condicionadas, versionadas y documentadas.
- No sustituir errores por datos de demostración. Todos los textos de interfaz en recursos es/en.
- Ejecutar tests afectados y compilación. Para cambios de datos/importación, ejecutar también las pruebas instrumentadas. No afirmar pruebas no realizadas.
- Actualizar el estado central con evidencia y límites; el MVP cloud no está terminado.
- Publicación en Google Play, servicios de pago, cambios en infraestructura compartida y migraciones destructivas requieren autorización explícita.

Comandos y entorno en README. `./tools/android.ps1` selecciona el JDK de Android Studio en Windows. No se versionan `.local/`, `local.properties`, builds ni documentos privados.
