# Inventario: una imagen optimizada en R2

18/10/2026. **Aprobado y ejecutado.** Sustituye la conservación remota de originales de ADR 0004 por ADR 0005. La limpieza anterior de cuatro derivados ya terminó y no forma parte de esta solicitud.

## Recursos y paquete concreto

Solo Worker `tickettracker-api`, D1 exclusiva `tickettracker` (`b8a13f81-ee19-49bb-8618-bc4dfd3cd94e`) y bucket privado UE `tickettracker-documents`, cuenta `93d96c1211328e81e46cb2bc8af30d47`. Publicar el paquete validado `.local/worker-bundle/worker.js`, SHA-256 `95552df3e9235539afcce37b5f30a627a8da095295b1a8a4df7dcefc22e3d171`.

Conservar bindings, secretos cifrados, clientes Google, cuotas, cron y planes. Sin nuevas tablas, migraciones o acceso público. Sin cambios en Supabase, Coolify u otros proyectos. No crear sesiones de aplicación en nombre de otra cuenta.

## Cambios a aplicar

1. Publicar la política de una variante OPTIMIZED por imagen: hasta 2 MiB, nunca mayor que su origen, autorización por propietario, hash y subida/finalización verificadas. Rechazar imágenes nuevas sin optimizar. Los PDF siguen íntegros. Mantener compatibilidad con operaciones antiguas mediante nuevas claves de idempotencia y con previews PDF sin sustituir el PDF por su miniatura.
2. Subir únicamente las dos copias WebP inventariadas bajo el mismo prefijo privado de propietario `adf2d080-3400-43cc-adec-f2c99e6a3eb1/`. Descargarlas desde R2 y comprobar SHA-256 antes de cambiar referencias.
3. Actualizar solo los dos documentos de la tabla desde revisión 3 a 4, con control CAS, metadatos de procedencia y feed existente. Registrar una variante optimizada por documento, sin modificar compras, líneas, identidad o asociación. Conservar filas/objetos antiguos hasta la comprobación del reemplazo.
4. Retirar exclusivamente los dos JPEG remotos reemplazados y después sus dos filas. Conservar sus originales privados locales ya verificados y el PDF de 616 bytes. Verificar tres objetos finales, hashes, revisiones y repetición idempotente. Otras subidas no amplían este inventario. Si cambia una revisión o un hash, detener ese documento y revisar.

| Documento | JPEG anterior, bytes | WebP, bytes | Reducción | Nuevo objeto (bajo el prefijo indicado) |
| --- | ---: | ---: | ---: | --- |
| 81814e5f-18d1-45e0-9e7b-329cc3463aee | 2.007.137 | 267.890 | 86,7 % | 20512841-17d7-4415-9ce7-02d37a9845ed.webp |
| b3649b6f-e8dc-433d-9bfc-4cb194dadf10 | 4.209.738 | 1.665.690 | 60,4 % | bd57aab7-4096-4162-ae78-8a52a12397c5.webp |

SHA-256 WebP en orden: `645a37858cdd9be74dc36a7c5595991443d9f4c2568a33cdcf73b9c2fb49a115`, `b91f51b8baca7c7a86e50053a924e6d5e142479e974b8e3fa2c0465093a90a31`.

JPEG a retirar (mismo prefijo): `7c8e03ef-e276-4d2e-8b57-e7153b2427e8`, SHA-256 `bd26e73f8f038412519fcfb9f0f2273b778bb94482d33e0f961144421a512b35`; `bb8d1a6a-a45c-43d6-8e0e-cc0aa1adec12`, SHA-256 `37f3ce5a66b2c4e4a96ae1a38287333a0221412f4aa76779e5f6818817dd3180`.

Manifiesto privado exacto `.local/optimized-storage-plan.json`, SHA-256 `469657ca49e910a3f66d5271265954b74775d7cfafe1e9a422a380e85d9c0313`. Objetivo para estos tres documentos: **1.934.196 bytes frente a 6.217.491**, ahorro 4.283.295 bytes (68,9 %). No es una estimación de factura ni incluye otros objetos que se suban después.

## Evidencia y límites

Las copias se generaron con el mismo código Android de la APK y se inspeccionaron visualmente. PSNR de luminancia a vista común de 1600 px: 41,12 y 39,46 dB. El segundo documento conserva resolución completa para evitar el deterioro del texto fino. Los valores críticos reconocidos se conservaron; el texto OCR completo puede variar. No se acredita lectura universal de códigos de barras/QR, precisión jurídica ni fidelidad binaria al JPEG. Las imágenes privadas y el OCR no se incluyen en Git ni en CI.

El Worker cuenta con 30 pruebas locales de integridad, aislamiento, cuotas, reintentos y PDF. La APK 0.1.3 debe instalarse como actualización antes de usar el nuevo formato cloud: las versiones de prueba anteriores suponían que toda copia era ORIGINAL. Las miniaturas permanecen locales y el ZIP v3 identifica qué originales están realmente disponibles.

Reversión: conservar código/configuración anteriores y backup local de las dos filas/cuerpos, originales y WebP. Se puede revertir código antes de cambiar metadatos. Después no restaurar referencias a objetos eliminados: cualquier reposición requiere comprobar y reponer primero sus bytes. El borrado permanente desde la interfaz de Cloudflare requiere confirmación en su diálogo final.

## Ejecución confirmada

El propietario aprobó «Sí, desplegar y sustituir esos dos JPEG» y confirmó después «Confirmo, elimina los dos JPEG antiguos» en el diálogo de R2. Worker publicado el 18/10/2026 a las 12:14 UTC, versión `b6be4643cbc04222a56244fc7c5d03d0`, con el hash indicado. Configuración, bindings y ambos secretos cifrados conservados; health 200 y acceso a almacenamiento sin sesión 401.

R2 confirmó la subida de los dos WebP; ambas descargas reales coinciden en tamaño y SHA-256. Se publicaron las dos revisiones 4 con OPTIMIZED_IMAGE y solo después se eliminaron los dos JPEG. Cloudflare confirmó «Se han eliminado 2 archivos correctamente». Se retiraron exclusivamente sus dos filas D1. Resultado: OPTIMIZED 2 / 1.933.580 bytes y ORIGINAL 1 / 616 bytes, total 1.934.196. Repetir la aplicación no cambia las revisiones. Las métricas agregadas de la consola pueden tardar en reflejarlo.

Pruebas privadas excluidas de Git: `.local/optimized-r2-verification.json`, `.local/optimized-storage-proof.json` y `.local/optimized-r2-after.png`; originales en `.local/storage-preservation/`. Wrangler se cerró al terminar. No se afirma un nuevo login Google del agente ni recuperación Android real entre móviles: eso sigue en pruebas.
