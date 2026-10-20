# Google Auth del piloto

Configurado el 17/10/2026 con autorización del propietario en un proyecto independiente `TicketTracker`, ID `tickettracker-511912`. Estado externo **Prueba**. El 20/10 se verificó la segunda cuenta del propietario tras aprobar su inventario privado; ambas figuran en usuarios de prueba. Sin facturación ni publicación en Google Play. Detalles en GMAIL_DEPLOYMENT.md.

| Cliente | Uso | Identificador público |
| --- | --- | --- |
| TicketTracker Android backend audience | Audiencia del ID token; `GOOGLE_CLIENT_ID` en Worker y Android | 959037289403-5o2stqqn9uv38e2uorrcnqq7qi1igmta.apps.googleusercontent.com |
| TicketTracker Android debug Sergio | Parte autorizada Android; `GOOGLE_ANDROID_CLIENT_ID` en Worker | 959037289403-bejhrcq3ct5hi05mekmmqehtfj45h1qj.apps.googleusercontent.com |

Paquete `es.sergiotech.tickettracker`. SHA-1 debug registrado: `26:3A:4B:18:60:7F:E2:CD:88:56:57:1E:BB:3F:34:7E:DF:AF:CE:D2`, comprobado con `:app:signingReport`. La APK debug local usa esa firma; APK de CI u otro desarrollador puede usar otra. No se creó clave de distribución: release continúa sin firma y necesitará otro cliente Android para su certificado.

El login sigue solicitando solo `openid`, `https://www.googleapis.com/auth/userinfo.email` y `https://www.googleapis.com/auth/userinfo.profile`. El incremento 0.1.4 añade Gmail como autorización opcional separada; inventario y evidencia en GMAIL_DEPLOYMENT.md. No se solicitan permisos Drive o Cloud Platform. El propietario aprobó la aceptación de la Política de Datos del Usuario de las APIs y la creación de ambos clientes. El secreto web no se utiliza en este flujo nativo y no se guardó en Git ni en el APK.

Credential Manager usa el cliente web y un nonce emitido por el Worker. El Worker verifica firma Google, emisor, audiencia, parte Android autorizada, caducidad, correo verificado y nonce de un uso. La validación criptográfica tiene pruebas locales. El propietario comunicó una sesión Google correcta en su móvil; no se ha realizado un nuevo login real del agente en 0.1.4. Gmail utiliza AuthorizationClient y un access token temporal, sin pasarlo al Worker. Su consentimiento y lectura real en el móvil requieren una prueba específica y no quedan acreditados por el login de identidad.
