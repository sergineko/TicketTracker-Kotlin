# ADR 0008 Lectura por columnas y registro compacto

21/10/2026. El propietario solicita compatibilidad dinámica con facturas de otros establecimientos y un formulario más ágil. Se amplía el reconocimiento local por estructura; no se introducen reglas por marca, servicios de IA externos, nuevas credenciales ni cambios de infraestructura.

## Lectura adaptable

Cada cabecera reconocida construye un plan de columnas de descripción/concepto/artículo/servicio, cantidad, precio unitario, importe, código, posición, IVA y descuento. El orden procede de las etiquetas impresas en español o inglés, incluidas columnas de precio o importe anteriores a la descripción. El código de producto, impuesto o descuento no se convierte en el nombre de un artículo. Un plan ambiguo no resuelve importes por intuición. Las rutas anteriores permanecen para formatos compatibles con columnas omitidas y descripciones partidas.

Fechas con meses escritos en español/inglés complementan las fechas numéricas; se mantienen años de cuatro cifras y validación civil estricta. Se admiten etiquetas alternativas de total y de emisor, incluidos comercios con números. El saldo pendiente sigue subordinado al total de compra.

Las filas de tabla que no pueden convertirse se señalan en la revisión y no impiden leer las siguientes. No se promete compatibilidad universal: columnas desconocidas, cantidades fraccionarias y texto ilegible pueden necesitar registro manual. Las cantidades del modelo continúan siendo enteras; una fila fraccionaria no se transforma silenciosamente en una cantidad redondeada. Los límites PDF de ADR 0007 y la conservación del original no cambian.

## Formulario

El registro empieza con escanear/importar y permite abrir el justificante asociado desde la revisión. Comercio, fecha e importe son el primer bloque. La moneda ocupa un selector compacto; canal, entrega, referencia y notas son desplegables. Los artículos importados muestran nombre, cantidad y precio en filas plegables. Garantía y devolución se abren por artículo, con fechas impresas aplicables solo mediante una elección explícita.

Una única acción de guardar permanece visible. Los errores aparecen en el campo afectado y abren la sección correspondiente. Corregirlos no pliega el artículo ni descarta otros campos. Las fechas manuales admiten ISO, DD/MM/AAAA o DD.MM.AAAA y se guardan como LocalDate. La suma de líneas solo se ofrece cuando todos sus precios/cantidades son válidos; reemplaza el total únicamente por una acción del usuario. Descuentos, portes e impuestos pueden justificar una diferencia, que no bloquea el guardado.

No cambian Room v4, DTO cloud, D1, Worker, R2 ni los permisos de Google. Se conservan los identificadores de líneas/productos al editar o quitar otros artículos. La validación incluye sectores y órdenes de columnas sintéticos, OCR real de PDF, interacción Compose, claro/oscuro, fuente al 130 %, guardado Room y regresiones anteriores. El borrador sigue sin persistir tras la muerte del proceso; no se declara terminado el MVP cloud.
