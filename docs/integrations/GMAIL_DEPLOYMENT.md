# Inventario de Gmail para el piloto 0.1.4

20/10/2026. **APROBADO Y APLICADO.** El propietario aprobó expresamente habilitar Gmail API y guardar el permiso de lectura de este inventario. La configuración de consola está verificada; el consentimiento y lectura real en un móvil siguen pendientes.

## Estado observado en consola (solo lectura)

- Proyecto independiente TicketTracker, ID `tickettracker-511912`.
- Google Auth externo en modo Prueba. Un único usuario de pruebas: la cuenta del propietario ya configurada; no se añaden destinatarios.
- Clientes Android debug y audiencia web existentes, detallados en GOOGLE_AUTH.md. Firma debug del APK preservada.
- Consentimiento actual: openid, userinfo.email y userinfo.profile. No hay alcances sensibles/restringidos.
- Gmail API muestra «habilitar esta API»: está deshabilitada.

## Cambios concretos aprobados y aplicados

1. Habilitar únicamente `gmail.googleapis.com` en ese proyecto.
2. Añadir únicamente `https://www.googleapis.com/auth/gmail.readonly` a Acceso a los datos. Conservar los tres alcances de identidad existentes.
3. Conservar el modo Prueba, el único usuario permitido y los clientes existentes. No crear secretos, clientes ni claves; no activar facturación ni publicar la app.

El alcance nuevo permite leer correo cuando el usuario lo conceda; Google lo clasifica como restringido. Declararlo en el proyecto no concede por sí solo acceso al buzón. Cada móvil debe aceptar la autorización opcional desde TicketTracker. La app usa cabeceras/metadatos y solo descarga el adjunto seleccionado. El agente no abrirá ni buscará mensajes personales para esta configuración.

## Comprobaciones y reversión

Después de aplicar: verificar en consola Gmail API habilitada, una fila gmail.readonly y conservación de identidad/modo Prueba/usuarios. Guardar evidencia privada de consola en .local/. No tocar Cloudflare, D1/R2, Resend, Supabase ni otras aplicaciones.

Comprobado: consola muestra Gmail API «Habilitada»; Acceso a los datos confirmó «Se guardaron los cambios» con gmail.readonly y los tres alcances de identidad. Público sigue en Prueba y mantiene el usuario actual. No se crearon clientes, claves ni secretos. Evidencia privada en .local/ocr-validation/gmail-api-enabled.png, gmail-scope-saved.png y gmail-testing-preserved.png. No se consultó el buzón.

La validación móvil consiste en conectar Gmail, buscar un ticket y elegir un PDF/imagen. La lectura del ticket aportado, transportes controlados, límites y aislamiento se prueban localmente; no equivalen a una lectura real del buzón. Los mensajes con solo enlaces no están soportados en esta versión. Las restricciones de Google para apps no verificadas siguen vigentes.

Si se decide retirar la función, el usuario puede retirar el permiso Gmail desde la app o su cuenta Google. También pueden quitarse el alcance declarado y deshabilitarse Gmail API en el mismo proyecto mediante un nuevo cambio autorizado. La sesión de TicketTracker y el inventario local son independientes.

## Segunda cuenta de pruebas en 0.1.5

El propietario aprobó el inventario privado .local/gmail-test-user-inventory.md para admitir la segunda cuenta con la que intentó importar correo. Se comprobó en consola que figuran ambas cuentas y continúa el modo Prueba; no se creó una entrada duplicada al observar que el alta ya estaba aplicada. Captura: .local/invoice-validation/gmail-test-users-verified.png. Las direcciones completas no se versionan en este añadido.

Esta autorización no completa la verificación pública de Google. La app explica la restricción de cuentas de pruebas y el posible aviso de aplicación no verificada. No se consultó el buzón del propietario; consentimiento e importación reales desde el móvil siguen pendientes de prueba por su parte.
