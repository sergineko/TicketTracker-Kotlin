# ADR 0007 Facturas, campos por columnas y PDF multipágina

20/10/2026. El propietario confirma la mejora de Mercadona y aporta una factura simplificada digital con otro formato. Este incremento amplía reglas generales, sin condiciones por nombre de comercio ni promesa de lectura universal.

La extracción normaliza ligaduras Unicode, fechas civiles con puntos/barras/guiones o ISO y espacios de OCR dentro de fechas y decimales. Acepta separadores de miles manteniendo importes en unidades menores enteras. Fechas etiquetadas de factura/compra tienen prioridad frente a pago/entrega; fechas inválidas o contradictorias quedan vacías. Un saldo pendiente o importe a pagar no sustituye al total de compra identificado.

Los encabezados impresos determinan cantidad antes/después de descripción, posición, código de producto, IVA porcentual, precio unitario y total. Se unen descripciones partidas y filas de precio separadas. Si falta el glifo de cantidad, solo se recupera mediante un cociente entero exacto entre total y precio unitario positivo, con cabecera compatible y sin descuento explícito. Discrepancias conservan el producto sin inventar un precio. Gastos de envío/descuentos no se convierten en productos del inventario. No se deduplican líneas repetidas legítimas.

Las posiciones de palabras asocian etiquetas y valores de columnas próximas. El texto conserva las filas y añade pares etiqueta/valor reordenados y comprobados; no se traduce texto comercial ni se consulta un servicio externo. No se mezcla dirección del cliente con comercio ni fecha de pago con entrega. Entrega y referencia explícitas completan solo campos vacíos. El campo compatible `orderNumber` admite referencia de pedido o factura; se prefiere pedido y se usa factura como alternativa etiquetada, sin cambiar Room ni el contrato remoto.

Este ADR sustituye el límite de una página de ADR 0006: PDF reconoce hasta diez páginas y 100000 caracteres secuencialmente. Renderiza para OCR hasta 3200 píxeles en el lado largo y ocho millones de píxeles por página. Android 35+ compara OCR y texto digital; a igualdad de datos, prefiere el digital para conservar la escritura. Android 29–34 usa OCR local. Página vacía/ilegible o límite alcanzado activan lectura parcial; el PDF original se conserva íntegro. No se interpretan automáticamente políticas jurídicas.

Gmail sigue separado del login. La segunda cuenta indicada por el propietario tiene inventario privado aprobado y lista verificada en modo Prueba. Añadir usuarios de prueba no equivale a completar la verificación pública ni acredita lectura real desde el móvil.

La validación usa fixtures sintéticos de varios formatos/PDF multipágina y ejecución expresa con documentos privados fuera de Git/CI. No se envían documentos a OCR/IA externos ni se modifican Worker, D1, R2, planes o secretos. El alcance es una propuesta editable de campos de compra; layouts desconocidos, texto ilegible y campos ambiguos requieren revisión.

Fuentes: [texto PDF nativo](https://developer.android.com/reference/android/graphics/pdf/content/PdfPageTextContent), [usuarios de prueba OAuth](https://support.google.com/cloud/answer/15549945?hl=en), [verificación de alcances restringidos](https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification).
