# TicketTracker API

Worker JavaScript con D1 exclusivo y R2 privado. API v1, códigos de correo mediante Resend y verificación Google con nonce. La cuenta interna tiene UUID propio; el propietario nunca procede del payload del móvil.

Node 22 o posterior:

```text
npm ci
npm test
npm run format:check
npm run check
npm run deploy:check
```

Los tests usan D1/R2 locales, correo interceptado y claves RSA ficticias. No necesitan credenciales ni envían emails. Para desarrollo manual, copiar `.dev.vars.example` a `.dev.vars`, elegir valores locales y ejecutar `npm run migrate:local` y `npm run dev`. Usar un proveedor de correo de pruebas o un destinatario autorizado si se configura Resend real.

No ejecutar `deploy` ni migraciones `--remote` hasta aprobar el [inventario](../../docs/integrations/CLOUDFLARE_DEPLOYMENT.md). El binding R2 declara jurisdicción `eu`; la base y bucket de otros proyectos no se utilizan.

Rutas públicas: `GET /health` y `POST /v1/auth/{email/request,email/verify,google/nonce,google}`. Los datos requieren `Authorization: Bearer …`: `/v1/me`, `/v1/sync/mutate`, `/v1/sync/changes?cursor=…`, `/v1/records/{id}`, `/v1/documents/{id}/variants/{variant}` (GET/PUT) y `/v1/documents/{id}/finalize` (POST). Logout revoca la sesión presentada.

Configuración pública en wrangler.jsonc; secretos solo en bindings de Worker. No hay credenciales Cloudflare ni acceso directo a R2 en Android. Véanse [seguridad](../../docs/architecture/SECURITY.md) y [sincronización](../../docs/architecture/SYNC_STRATEGY.md).
