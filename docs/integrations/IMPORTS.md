# Importación documental

Vías implementadas: SAF OpenDocument, Android ACTION_SEND para imagen/PDF y fotografía con CameraX. La Photo Picker específica y ACTION_SEND_MULTIPLE siguen pendientes. No se solicitan permisos generales de almacenamiento; la cámara pide su permiso al entrar en el flujo.

El pipeline acepta content URI y copia como máximo 25 MiB a un UUID opaco dentro de files/vault. Requiere al menos 80 MiB disponibles antes de comenzar, valida cabecera PDF o decodificador de imagen y rechaza dimensiones superiores a 400 millones de píxeles. Las imágenes se muestrean a una arista aproximada máxima de 2600 px, se corrige EXIF y se genera miniatura de 320 px. La copia JPEG solo se conserva si es menor del 90% del original; el original se conserva siempre.

Para PDF: PdfRenderer valida y renderiza la primera página a vista previa acotada para OCR. No se rasteriza el archivo guardado, no se alteran firmas y no se afirma verificar firmas. PDF protegido o corrupto se rechaza. El documento completo original se abre en un visor externo con autorización temporal de lectura.

ML Kit latino viene integrado, sin esperar una descarga inicial del modelo. La extracción determinista reconoce candidatos de comercio, fecha dd/MM/aaaa e importe etiquetado TOTAL. No reconoce todas las tipologías, no extrae artículos todavía y no inventa campos ausentes. La confianza heurística no es una probabilidad calibrada; la revisión se pide para todos los campos extraídos.

Limitaciones: conservación de original y copia optimizada consume espacio adicional; no hay todavía LRU ni deduplicación perceptual, recorte, detección de bordes, corrección de perspectiva, varios tickets/páginas o clasificación de firma PDF. No eliminar originales hasta contar con política y sincronización verificadas.
