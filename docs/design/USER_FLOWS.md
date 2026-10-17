# Flujos

## Registro manual

Inicio → Añadir compra → comercio/fecha/importe → uno o varios artículos → fechas opcionales y justificantes → Guardar → Room y outbox en la misma transacción → inventario y detalle.

## Importación

SAF/Compartir/Cámara → copia privada acotada → formato y decodificación → OCR → SHA original y miniatura → detección exacta por identidad → documento persistido → revisión de campos sugeridos → artículos introducidos por el usuario → asociación a compra.

El fallo de OCR permite conservar el documento y registrar manualmente. Fechas conflictivas o totales sin etiqueta permanecen sin extraer. Los artículos no se inventan a partir de texto incierto. No existe todavía extracción semántica de líneas de ticket.

## Consulta y protección

Inventario → búsqueda local → detalle → original → visor elegido por Android. Perfil → Exportar → destino elegido por SAF → ZIP con manifiesto y verificaciones de integridad. El destino recibe información privada sin cifrar, advertida antes de la acción.
