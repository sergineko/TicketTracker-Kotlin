# Integración con Gmail

La primera vía es Android Sharesheet: Gmail u otro gestor puede compartir una imagen/PDF con TicketTracker. También pueden seleccionarse adjuntos descargados con SAF. La app no lee correos ni interpreta el login Google como permiso de Gmail.

El conector directo futuro requiere consentimiento OAuth separado, scopes mínimos, verificación y posible evaluación de seguridad. Antes de implementarlo hay que confirmar el proceso vigente y su coste en documentación oficial de Google. No utilizar contraseñas, scraping autenticado ni reenviar mensajes a IA externa.

El acceso con Google para identidad también está pendiente; Credential Manager se incorporará con el adaptador de Auth tras preparar endpoint, clientes y redirecciones. No se incluye un botón de login que simule una sesión remota.
