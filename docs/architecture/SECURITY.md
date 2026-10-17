# Seguridad y privacidad

Los tickets permanecen en archivos internos Android. FileProvider concede lectura temporal al visor elegido. SAF evita permisos generales de archivos. Las importaciones limitan bytes, píxeles y memoria; conservan PDF y originales, aunque falle OCR. El procesamiento de PDF usa la primera página sin validar firmas electrónicas.

La sesión opaca se cifra mediante AES-256-GCM y Android Keystore, con escritura atómica. DataStore guarda preferencias y UUID anónimo. D1 conserva HMAC de códigos/tokens con un pepper exclusivo del servidor. Las sesiones duran treinta días; los códigos diez minutos, cinco intentos y un consumo. Límites por IP, email y proyecto reducen abuso. La API no registra payloads/tokens y tiene observabilidad desactivada.

Google se valida con JWKS, RS256, iss, aud, azp permitido, exp, iat, email_verified y nonce de un uso. Para un correo de terceros no autoritativo se exige primero una sesión por código de ese correo. El cliente web y el Android deben pertenecer al proyecto Google correcto. No se solicitan permisos de Gmail para iniciar sesión.

D1 no tiene RLS: el Worker filtra por propietario en cada consulta y acceso a objetos. Las pruebas incluyen acceso cruzado, referencias documentales ajenas, revisiones antiguas e idempotencia. R2 permanece privado y responde a través del Worker con no-store, MIME y checksum. Android exige HTTPS y rechaza redirecciones de API. Las credenciales Cloudflare/Resend y secretos OAuth no entran en el APK.

Las cuotas iniciales son 25 MiB por variante, 100 MiB por usuario, 1 GiB lógico reservado en el bucket del proyecto, 2000 registros por usuario, 5000 peticiones de datos/día para el proyecto y 90 correos/día. Son límites de piloto, no una garantía de factura cero ni un presupuesto global de la cuenta compartida.

El ZIP se exporta sin cifrar y se avisa en pantalla. Android excluye los datos de backup/transferencia automáticos. No hay cifrado específico de la base y archivos adicional al sistema operativo. El bucket no es por sí solo una copia independiente.

Antes de beta: privacidad/Data Safety, eliminación de cuenta, restore y retención independiente de D1/R2, compactación de feed, revisión de SDK, accesibilidad, archivos hostiles, pruebas físicas y carga/abuso. No se declara cumplimiento legal ni recuperación remota verificada.
