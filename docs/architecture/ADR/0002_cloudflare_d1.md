# ADR 0002 D1, Worker y R2 privados

Aceptada el 17/10/2026 por indicación expresa del propietario: sustituir Supabase por Cloudflare D1 para reducir la dependencia del VPS. Autenticación elegida: código por correo y Google. Esta decisión sustituye las referencias a Supabase de ADR 0001 y del Word original; el documento de origen se conserva como histórico.

Android mantiene Room como fuente observable. El adaptador HTTPS habla con un Worker exclusivo `tickettracker-api`; D1 `tickettracker` conserva identidades y agregados versionados, y R2 `tickettracker-documents` los archivos privados. D1 es SQLite: el aislamiento por proyecto es una base dedicada, no un schema PostgreSQL. Las tablas llevan prefijo `tt_`. No se toca el Supabase compartido ni los demás proyectos.

D1 no aporta Auth ni RLS. El Worker autoriza todas las consultas/objetos por UUID interno de propietario resuelto desde una sesión opaca. El móvil no recibe credenciales Cloudflare, Resend ni secretos OAuth. Los códigos duran diez minutos y admiten cinco intentos; las sesiones caducan a treinta días, se guardan con Keystore y se revocan al salir. Google verifica firma, emisor, audiencia, cliente Android autorizado, caducidad, correo verificado y nonce de un uso. El acceso a Gmail no forma parte de este login.

Una compra viaja como agregado JSON, con líneas y productos de UUID distintos; Room continúa normalizado. Esto reduce operaciones de D1 sin mezclar conceptos del dominio. Compare-and-swap, feed e idempotencia se confirman en un batch transaccional. R2 se verifica antes de marcar un documento sincronizado. No hay eliminación automática de originales.

El bucket Standard privado y la base D1 se crearon con jurisdicción UE. El propietario aprobó posteriormente el inventario de producción: esquema, secretos y Worker ya están desplegados; la evidencia y sus límites están en integrations/PRODUCTION_VALIDATION.md. La jurisdicción de D1/R2 no implica que todo el procesamiento de Worker/Resend ocurra exclusivamente en la UE.

Fuentes: [D1](https://developers.cloudflare.com/d1/), [batches transaccionales](https://developers.cloudflare.com/d1/worker-api/d1-database/), [R2](https://developers.cloudflare.com/r2/), [validación Google](https://developers.google.com/identity/sign-in/android/backend-auth).
