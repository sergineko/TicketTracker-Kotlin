# Prueba personal en Android

La APK `TicketTracker-0.1.1-pruebas.apk` de `entregas/` está firmada con la clave debug de este entorno. Es un APK universal para Android 10/API 29 o posterior. No es una publicación en Google Play. La release optimizada se compila por separado y no es el archivo de instalación entregado.

## Instalar

1. Copia la APK al móvil y ábrela desde Archivos o desde la app con la que la hayas recibido.
2. Si Android lo solicita, permite a esa app instalar aplicaciones desconocidas y pulsa Instalar.
3. Abre TicketTracker. Si ya tienes una versión firmada con la misma clave, instala como actualización. Si Android informa de una firma incompatible, conserva antes una exportación y no desinstales sin revisar los datos que puedes perder.

La aplicación empieza con tu inventario real vacío; los productos de las capturas son fixtures de pruebas y no se incluyen en la APK.

## Recorrido sugerido

- Pulsa «+» y registra una compra con dos artículos. Añade fechas de garantía y devolución confirmadas por ti. Guarda, busca desde Productos, edita y vuelve a abrirla.
- Comprueba Garantía activa (hasta su último día), Crítica (vencimiento entre hoy y 30 días) y Devoluciones posibles (fecha de devolución registrada aún vigente). Pulsa cada contador. Las categorías se solapan; el canal de compra no genera automáticamente un derecho de desistimiento.
- Desde «+», escanea un ticket con la cámara e importa una imagen/PDF. Revisa el OCR antes de guardar y abre el original. Un error de OCR conserva el documento. El PDF se reconoce solo en su primera página.
- Abre Suscripciones y Seguros. Registra nombre, importe, periodo y próxima fecha; edita y elimina un registro. Estas dos secciones guardan datos **solo en el móvil**, sin sincronización ni notificaciones por ahora.
- En Más, prueba tema claro/oscuro, tamaño de fuente de Android y recordatorios opt-in de garantías/devoluciones.
- En Más, exporta el ZIP y comprueba que contiene `manifest.json`, tus contratos y los originales adjuntos. El ZIP no está cifrado y todavía no hay un restaurador integrado.
- El acceso por correo y Google está en Más. La APK usa la configuración pública del piloto. Google admite la cuenta piloto autorizada y la firma debug registrada; su login en móvil físico y la recuperación en otra instalación siguen pendientes de validación.

No dependas aún de la sincronización para borrar el último original o cambiar de móvil: el MVP cloud sigue en pruebas. Conserva las exportaciones y los documentos originales. Antes de comunicar un fallo, anota versión de TicketTracker, Android y pasos para reproducirlo, evitando compartir tickets o datos privados innecesarios.
