# Inventario de despliegue aprobado

Fecha: 17/10/2026. El propietario aprobó expresamente este inventario y los cinco permisos OAuth de Wrangler. Esquema, Worker, secretos y clientes Google aplicados. La base D1 dedicada y el bucket se crearon con autorización anterior. Nuevos cambios fuera de este inventario siguen sujetos a AGENTS.md.

## Recursos existentes de este proyecto

| Recurso | Identidad | Estado observado |
| --- | --- | --- |
| D1 | tickettracker · b8a13f81-ee19-49bb-8618-bc4dfd3cd94e | Jurisdicción UE, esquema inicial aplicado |
| R2 | tickettracker-documents | Standard, jurisdicción UE, acceso público desactivado; un original sintético conservado |
| Worker | tickettracker-api | https://tickettracker-api.sergiotech.workers.dev |

## Cambios autorizados

1. Aplicar únicamente a esa D1 la migración `backend/cloudflare/migrations/0001_initial.sql`: once tablas `tt_`, índices y dos triggers de feed; Wrangler mantiene además su tabla de seguimiento de migraciones. Es una creación inicial, sin borrados ni migración de datos del VPS.
2. Crear el Worker `tickettracker-api` con la configuración versionada. Enlazar DB a esa D1 y DOCUMENTS al bucket privado indicado, con jurisdicción eu. Publicar su ruta HTTPS workers.dev. `/health` es pública; los datos y objetos requieren sesión.
3. Guardar como secretos del Worker un SESSION_PEPPER aleatorio exclusivo y RESEND_API_KEY. No copiarlos a Git, logs ni APK. Probar el remitente propuesto `TicketTracker <tickettracker@contact.sergiotech.es>` contra el dominio verificado antes de enviar un código real.
4. Mantener las cuotas descritas en SECURITY.md. No activar planes de pago ni modificar el resto de buckets/Workers. Programar a las 04:23 UTC solo la limpieza de retos/sesiones/límites caducados; no borrar compras ni objetos.
5. Configurar Google con un cliente web público (audiencia) y cliente Android del paquete `es.sergiotech.tickettracker` con huella de firma. Mantenerlo deshabilitado hasta disponer de identificadores válidos. No solicitar Gmail ni otros scopes de correo.
6. Compilar Android con la URL HTTPS y client ID públicos y ejecutar el ensayo de dos instalaciones con cuenta del propietario. Verificar subidas/descargas SHA-256, conflictos y acceso cruzado denegado. Ningún ticket privado se usará como fixture de CI.

## Preparación, validación y reversión

Antes de DDL, comprobar de nuevo que la D1 está vacía y exportar una copia si contiene algo. El dry-run de Wrangler empaqueta correctamente los bindings sin publicar. Los tests locales usan Miniflare D1/R2 y correo/JWKS simulados; las pruebas instrumentadas usan Room/Keystore reales y transporte de prueba.

Para revertir el estreno: retirar la ruta/Worker nuevo y revocar sus sesiones/credenciales, conservando D1 y R2 para inspección y exportación. No borrar recursos ni objetos como reversión automática. Tras introducir datos reales, exportar D1 y preservar objetos antes de cualquier cambio de esquema. Restauración independiente, RPO/RTO y backup periódico requieren un ensayo y una decisión antes de beta.

## Operación de mantenimiento

Desde `backend/cloudflare`, con Node 22+ y `npm ci`:

La migración inicial ya está aplicada; Wrangler registra su seguimiento. No generar otro pepper en cada despliegue: invalidaría las sesiones existentes. Los secretos se conservan al publicar.

```text
npm test
npm run format:check
npm run deploy:check
npx wrangler d1 migrations apply DB --remote
npx wrangler secret put SESSION_PEPPER
npx wrangler secret put RESEND_API_KEY
npm run deploy
```

Wrangler requiere acceso a la cuenta Cloudflare propietaria. La concesión OAuth se revisa en su pantalla de autorización antes de aceptarla. Mantener logs/configuración local dentro de `.local/`; no registrar secretos. La compilación Android permite sustituir `-PtickettrackerApiUrl=https://…` y `-PtickettrackerGoogleClientId=…`; estos valores son públicos.

En este estreno, el preflight R2 de `wrangler deploy` solicitaba permisos mayores que los cinco aprobados. Se utilizó la [API oficial de subida de Workers](https://developers.cloudflare.com/api/resources/workers/subresources/scripts/methods/update/) con Workers Scripts Write, los bindings D1/R2 conocidos y `keep_bindings: ["secret_text"]`. No se concedieron esos permisos adicionales. El paquete publicado tiene SHA-256 `8aae428d1347cb8c9de33b2eca94226580f4e923ea99420a63f4a89f3741ef83`; la versión tras configurar Google es `1b66486a9de240f19d9b226ed82d6678`. Los dos secretos cifrados seguían presentes después de publicar y la sesión existente continuó válida.

Ver [validación real y límites](PRODUCTION_VALIDATION.md) y [configuración Google](GOOGLE_AUTH.md). No se activaron planes de pago. El cron de las 04:23 UTC solo limpia retos/sesiones/límites caducados.

Supabase, Coolify, PostgreSQL y los otros proyectos quedan fuera de este despliegue. No se han cambiado durante esta integración.
