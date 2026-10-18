# Prueba personal en Android

La APK `TicketTracker-0.1.3-pruebas.apk` de `entregas/` está firmada con la clave debug de este entorno. Es un APK universal para Android 10/API 29 o posterior. No es una publicación en Google Play. La release optimizada se compila por separado y no es el archivo de instalación entregado.

## Instalar

1. Copia la APK al móvil y ábrela desde Archivos o desde la app con la que la hayas recibido.
2. Si Android lo solicita, permite a esa app instalar aplicaciones desconocidas y pulsa Instalar.
3. Abre TicketTracker. Si ya tienes una versión firmada con la misma clave, instala como actualización. Si Android informa de una firma incompatible, conserva antes una exportación y no desinstales sin revisar los datos que puedes perder.

La aplicación empieza con tu inventario real vacío; los productos de las capturas son fixtures de pruebas y no se incluyen en la APK.

## Recorrido sugerido

- Pulsa «+» y registra una compra con dos artículos. Añade fechas de garantía y devolución confirmadas por ti. Guarda, busca desde Productos, edita y vuelve a abrirla.
- Comprueba Garantía activa (hasta su último día), Crítica (vencimiento entre hoy y 30 días) y Devoluciones posibles (fecha de devolución registrada aún vigente). Pulsa cada contador. Las categorías se solapan; el canal de compra no genera automáticamente un derecho de desistimiento.
- Desde «+», escanea un ticket con la cámara e importa una imagen/PDF. Revisa comercio, fecha, total y artículos propuestos. Si aparecen fechas impresas, usa el botón del producto correspondiente; no se aplican a todos automáticamente. Revisa antes de guardar y abre el documento. Un error de OCR conserva el documento. El PDF se reconoce solo en su primera página.
- Abre Suscripciones y Seguros. Registra nombre, importe, periodo y próxima fecha; edita y elimina un registro. Estas dos secciones guardan datos **solo en el móvil**, sin sincronización ni notificaciones por ahora.
- En Más, prueba tema claro/oscuro, tamaño de fuente de Android y recordatorios opt-in de garantías/devoluciones.
- En Más, exporta el ZIP y comprueba que contiene `manifest.json`, tus contratos y los archivos disponibles. El manifiesto v3 indica si falta el original y solo está la copia optimizada. El ZIP no está cifrado y todavía no hay un restaurador integrado.
- La cuenta se abre desde Inicio o Más. Una sesión muestra identidad, estado de sincronización y alcance de la nube. Google y cerrar sesión quedan en opciones secundarias; los contratos siguen siendo locales.
- Las imágenes nuevas solo envían una copia optimizada de hasta 2 MiB. Originales y miniaturas quedan en el móvil; el PDF se guarda íntegro. En Tu cuenta puedes consultar bytes optimizados y originales antiguos, y optimizar imágenes anteriores después de sincronizar. Si no puede generar una copia aceptable, conserva el original y deja el envío pendiente: no sube la fotografía grande. En una instalación nueva se recupera la copia optimizada, no el original de cámara. El despliegue concreto figura en integrations/OPTIMIZED_IMAGES_DEPLOYMENT.md.
- El acceso por correo y Google está en la pantalla de cuenta. La APK usa la configuración pública del piloto. Google admite la cuenta piloto autorizada y la firma debug registrada; el propietario ya ha comunicado una sesión Google correcta en su móvil. La recuperación en otra instalación sigue pendiente de validación completa.

No dependas aún de la sincronización para borrar el último original o cambiar de móvil: el MVP cloud sigue en pruebas. Conserva las exportaciones y los documentos originales. Antes de comunicar un fallo, anota versión de TicketTracker, Android y pasos para reproducirlo, evitando compartir tickets o datos privados innecesarios.
