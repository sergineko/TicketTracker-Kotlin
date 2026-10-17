# Validación del piloto Cloudflare

17/10/2026. Inventario aprobado por el propietario. Worker `tickettracker-api`, D1 exclusiva `tickettracker` y R2 privado `tickettracker-documents` (eu). Supabase y los demás proyectos no se modificaron.

## Resultados observados

| Comprobación real | Resultado |
| --- | --- |
| `/health` desde una red externa | 200, servicio TicketTracker y almacenamiento D1+R2 |
| Datos sin sesión | 401 en cuenta, feed y descarga del original |
| Remitente Resend | Dos códigos solicitados por el propietario y recibidos en su correo autorizado |
| Verificación de código y cuenta | 200 y 200; código reutilizado: 401 |
| Sesión tras publicar configuración Google | 200; secretos conservados |
| Nonce Google configurado | 200; no equivale a login Google real |
| Creación D1 e idempotencia | Misma mutación repetida devuelve la misma respuesta |
| Finalizar documento sin objetos | 409 |
| Subir bytes corruptos | 422 |
| Subida original / finalizar / descarga | 200 / 200 / 200; mismo SHA-256 |
| Compra enlazada y feed | Documento y compra presentes; revisión obsoleta devuelve 409 |
| Borrado lógico de compra sintética | 200; se conserva su historial y original |
| Logout y token revocado | 200; consulta posterior devuelve 401 |

Fixture identificado expresamente como `VALIDACION-CLOUD-20261017.pdf`, PDF sintético de 616 bytes. SHA-256 `ca9e1cea773cb50403bdc581d52ae4a87adc90e421d9988f193012dffe307cde`. Documento `ca020c95-8bd3-4d2c-b737-84c15a54dc8a`; compra sintética `db70f47b-2702-497c-b7a9-5b2050da32e8`. El original permanece tanto en R2 como en `.local/cloud-validation-original.pdf`; no se utilizó un ticket privado como fixture ni se borró el último original.

Las peticiones de prueba se ejecutaron por HTTPS desde el VPS autorizado, sin modificar su configuración. Cabeceras de sesión y cuerpos viajaron por stdin SSH; no por argumentos ni archivos remotos. La sesión local se protegió con Windows DPAPI y se revocó al terminar. Una primera sesión de ensayo quedó sin guardar por un fallo de la herramienta local; su caducidad administrativa está pendiente de identificarla de forma única y recuperar acceso D1 de gestión. La revisión automática rechazó una consulta de caducidad que podía afectar a varias sesiones; esa modificación no se ejecutó. Las fechas y tokens del propietario no se incluyen en este informe.

## Límites y siguientes ensayos

La red Windows local agota el tiempo de conexión a las IP resueltas de workers.dev; HTTPS funciona desde otra red. No se cambió el DNS, firewall, aislamiento ni reenvío SSH. El ensayo de backend **no acredita** el transporte Android, dos instalaciones o restauración independiente.

Pendientes: Google real desde Android, segunda instalación con recuperación completa, caída/reintento en dispositivo, conflicto entre dos dispositivos, aislamiento real entre dos cuentas, backup independiente D1/R2 y recuperación ensayada. El aislamiento entre propietarios, cuotas y validación Google sí tienen pruebas Miniflare; no sustituye esos ensayos reales. El MVP cloud no está terminado.

## Revisión de CI

Main `c7a725e` tiene los cuatro jobs correctos: Worker, build/lint/JVM/APK, dispositivo API 29 y API 36. [Ejecución 39032407711](https://github.com/sergineko/TicketTracker-Kotlin/actions/runs/39032407711).

El [fallo consultado por el propietario](https://github.com/sergineko/TicketTracker-Kotlin/actions/runs/39032066012) pertenece al [PR Dependabot #11](https://github.com/sergineko/TicketTracker-Kotlin/pull/11), sin integrar. Solo actualiza Kotlin de 2.2.10 a 2.4.21. Hilt 2.59.2 informa: `Provided Metadata instance has version 2.4.0, while maximum supported version is 2.3.0`. Requiere una actualización coordinada y validada de la herramienta de compilación. Se conserva la combinación que pasa CI; no se fusionó ni cerró el PR automáticamente.
