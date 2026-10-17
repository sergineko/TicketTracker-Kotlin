# Inventario de despliegue pendiente de aprobación

Fecha: 17/10/2026. Estado: preparado para revisión; no se ha aplicado el esquema ni publicado el Worker. La base D1 dedicada y el bucket vacío se crearon con autorización anterior. La regla de AGENTS.md exige aprobación de un inventario concreto antes de nuevos cambios de producción.

## Recursos existentes de este proyecto

| Recurso | Identidad | Estado observado |
| --- | --- | --- |
| D1 | tickettracker · b8a13f81-ee19-49bb-8618-bc4dfd3cd94e | Jurisdicción UE, vacío al crearse |
| R2 | tickettracker-documents | Standard, jurisdicción UE, acceso público desactivado |

## Cambios propuestos

1. Aplicar únicamente a esa D1 la migración `backend/cloudflare/migrations/0001_initial.sql`: once tablas `tt_`, índices y dos triggers de feed. Es una creación inicial, sin borrados ni migración de datos del VPS.
2. Crear el Worker `tickettracker-api` con la configuración versionada. Enlazar DB a esa D1 y DOCUMENTS al bucket privado indicado, con jurisdicción eu. Publicar su ruta HTTPS workers.dev. `/health` es pública; los datos y objetos requieren sesión.
3. Guardar como secretos del Worker un SESSION_PEPPER aleatorio exclusivo y RESEND_API_KEY. No copiarlos a Git, logs ni APK. Probar el remitente propuesto `TicketTracker <tickettracker@contact.sergiotech.es>` contra el dominio verificado antes de enviar un código real.
4. Mantener las cuotas descritas en SECURITY.md. No activar planes de pago ni modificar el resto de buckets/Workers. Programar a las 04:23 UTC solo la limpieza de retos/sesiones/límites caducados; no borrar compras ni objetos.
5. Configurar Google con un cliente web público (audiencia) y cliente Android del paquete `es.sergiotech.tickettracker` con huella de firma. Mantenerlo deshabilitado hasta disponer de identificadores válidos. No solicitar Gmail ni otros scopes de correo.
6. Compilar Android con la URL HTTPS y client ID públicos y ejecutar el ensayo de dos instalaciones con cuenta del propietario. Verificar subidas/descargas SHA-256, conflictos y acceso cruzado denegado. Ningún ticket privado se usará como fixture de CI.

## Preparación, validación y reversión

Antes de DDL, comprobar de nuevo que la D1 está vacía y exportar una copia si contiene algo. El dry-run de Wrangler empaqueta correctamente los bindings sin publicar. Los tests locales usan Miniflare D1/R2 y correo/JWKS simulados; las pruebas instrumentadas usan Room/Keystore reales y transporte de prueba.

Para revertir el estreno: retirar la ruta/Worker nuevo y revocar sus sesiones/credenciales, conservando D1 y R2 para inspección y exportación. No borrar recursos ni objetos como reversión automática. Tras introducir datos reales, exportar D1 y preservar objetos antes de cualquier cambio de esquema. Restauración independiente, RPO/RTO y backup periódico requieren un ensayo y una decisión antes de beta.

## Operación autorizada, después de aprobación

Desde `backend/cloudflare`, con Node 22+ y `npm ci`:

```text
npm test
npm run format:check
npm run deploy:check
npx wrangler d1 migrations apply DB --remote
npx wrangler secret put SESSION_PEPPER
npx wrangler secret put RESEND_API_KEY
npm run deploy
```

Wrangler requiere acceso a la cuenta Cloudflare propietaria. La concesión OAuth se revisa en su pantalla de autorización antes de aceptarla. Mantener logs/configuración local dentro de `.local/`; no registrar secretos. La compilación Android usa `-PtickettrackerApiUrl=https://…` y `-PtickettrackerGoogleClientId=…`; estos valores son públicos.

Supabase, Coolify, PostgreSQL y los otros proyectos quedan fuera de este despliegue. No se han cambiado durante esta integración.
