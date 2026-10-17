> Actualización posterior del propietario (17/10/2026): D1 + Worker sustituyen Supabase; R2 privado dedicado y acceso por código de correo + Google. Las menciones originales a PostgreSQL/Auth/Storage se conservan como histórico. Véase ADR 0002.

# Especificación de origen



Transcripción del Word aportado por el propietario. Las actualizaciones de infraestructura prevalecen sobre las decisiones anteriores.



IDEA

Quiero una app pura de Android que sirva para subir los tickets de compra y poder indicarle o tener un inventario de cuando caduca la garantía de la compra y los plazos de devolución. Debe de ser moderna, facil de usar, intuitiva, integrada con otras apps (gmail entre otros para obtener tickets de otras aplicaciones) y que tenga su parte personalizada, es decir, que los usuarios tengan su cuenta y no pierdan la información si desinstalan la app o se cambian de movil. Debe de estar optimizada la subida de los tickets (ocupar lo mínimo posible con transformación de ficheros) y fluida. Debe de ser para versiones modernas de Android donde el mercado sea más voluminoso evitando aquellos que no soporten Kotlin ni Jetpack Compose UI con Material You de Material 3. Indicame el Prompt completo para que elabore la IA todo el plan y empiece a codificar la App con la estructura que indico. Debe de ser un estilo moderno.

CONCEPTO

Te propongo desarrollar una aplicación 100 % nativa para Android, pensada para convertirse en un producto comercial de Sergiotech, con una experiencia de usuario moderna, rápida y muy cuidada.

La idea es ir más allá de una simple aplicación para guardar tickets: crear un gestor inteligente de compras, facturas, garantías y devoluciones, que permita recuperar cualquier justificante, saber qué productos siguen protegidos y recibir avisos antes de perder un derecho de devolución o de que expire una garantía.

Esta sección actualiza decisiones del documento original; las funciones de tickets, garantías, devoluciones, OCR, Gmail, UX, seguridad y pruebas se conservan y deben implementarse conforme al prompt maestro.

Orden real de lectura: 1) Room y almacenamiento privado del móvil; 2) caché local de documentos y miniaturas; 3) API autorizada de Supabase y Cloudflare R2 bajo demanda. La CDN de Cloudflare no será una caché compartida de documentos privados por defecto. PostgreSQL es la autoridad remota de metadatos y R2 la de objetos sincronizados.

Principio de independencia: ningún ViewModel, caso de uso ni entidad de negocio dependerá directamente de clases de Supabase o Cloudflare. La aplicación usará contratos internos (IdentityGateway, PurchaseRepository, DocumentStore, SyncGateway) y adaptadores intercambiables; el esquema de datos, exportación, pruebas de migración y compatibilidad de API quedan versionados.

Objetivo estratégico: evitar costes fijos adicionales en el arranque, proteger a los proyectos existentes y diseñar desde el día uno la migración posterior hacia servicios gestionados o infraestructura más potente, cuando los ingresos de la cartera de proyectos permitan sostenerla.

Decisión: lanzar TicketTracker reutilizando Supabase autohospedado para identidad y metadatos; Cloudflare R2 Standard privado para documentos; Android/Room como primera capa de consulta; caché local limitada; sincronización incremental. La nube sirve para durabilidad y recuperación, no para cada apertura de pantalla.

ACTUALIZACIÓN DE INFRAESTRUCTURA Y EVOLUCIÓN (OCTUBRE 2026)

1. Decisiones tecnológicas recomendadas

Estas serían mis decisiones iniciales para el proyecto:

La elección de Android 10 como versión mínima es intencionada: permite aprovechar tecnologías modernas sin excluir innecesariamente a usuarios de móviles que siguen siendo plenamente funcionales. Compose no requiere por sí mismo excluir tantas versiones antiguas; el corte se establece por estrategia de producto, mantenimiento y experiencia de usuario.

Además, Google Play exige desde el 31 de agosto de 2026 que las nuevas aplicaciones y actualizaciones tengan como objetivo Android 16 (API 36) o superior.

La experiencia que buscaría

Inicio con resumen y alertas

Inventario de compras

Referencias visuales de dirección estética, no pantallas definitivas de la aplicación.

Hay dos aspectos que considero fundamentales desde el inicio:

Separar el ticket de los artículos comprados. Un ticket puede contener diez productos con distintas garantías, devoluciones o fechas de entrega. Modelarlo todo como una única compra sería un error de diseño.

Integrar Gmail de forma responsable. El inicio de sesión con Google no da acceso automáticamente al correo. Para buscar justificantes dentro de Gmail, Google puede exigir verificación OAuth y una evaluación de seguridad por utilizar permisos restringidos. Debemos diseñar una alternativa funcional mediante compartir correos o archivos mientras se prepara esa integración.

En cuanto a las garantías, por ejemplo, en España la garantía legal general de los bienes de consumo nuevos es de tres años desde la entrega y, en determinadas compras a distancia, existe un derecho de desistimiento de 14 días naturales desde la recepción. No debemos aplicar esos plazos indiscriminadamente a todas las compras.

2. Prompt maestro para la IA de desarrollo

Este prompt está diseñado especialmente para un agente de programación como Codex, capaz de inspeccionar un repositorio, generar archivos, ejecutar compilaciones y continuar trabajando de forma autónoma.

No se limita a pedir un plan. Le exige diseñar la arquitectura, documentar las decisiones, generar el proyecto y comenzar la implementación real, sin quedarse en prototipos visuales.

PROMPT MAESTRO — DESARROLLO DE APLICACIÓN ANDROID NATIVA PARA GESTIÓN INTELIGENTE DE COMPRAS, TICKETS, GARANTÍAS Y DEVOLUCIONES

0. TU ROL Y OBJETIVO

Actúa como un equipo multidisciplinar senior compuesto por:

Principal Android Engineer.

Kotlin / Jetpack Compose Specialist.

Android UX/UI Designer.

Software Architect.

Backend Engineer especializado en Supabase.

Security & Privacy Engineer.

Mobile Performance Engineer.

QA Automation Engineer.

Product Manager.

Especialista en integración de servicios Google.

Tu misión es diseñar, planificar y comenzar a desarrollar una aplicación Android nativa de calidad comercial, preparada para distribuirse mediante Google Play bajo el desarrollador Sergiotech.

No quiero únicamente una propuesta conceptual, un mockup, una aplicación de demostración o una arquitectura sin implementar.

Quiero un proyecto real, modular, mantenible, seguro, visualmente excelente, funcional, con pruebas automatizadas y diseñado para evolucionar como producto comercial.

Debes trabajar de forma autónoma, tomando decisiones técnicas justificadas, sin interrumpir continuamente el desarrollo para solicitar aprobaciones menores.

No publiques, despliegues servicios de pago, crees recursos externos de producción ni realices operaciones irreversibles sin autorización explícita.

1. VISIÓN DEL PRODUCTO

Desarrollar una aplicación que permita a cualquier usuario registrar, digitalizar, organizar, consultar y recuperar sus compras y justificantes.

Debe ofrecer:

Digitalización de tickets físicos.

Importación de tickets electrónicos.

Almacenamiento organizado de facturas, recibos y justificantes.

Inventario de artículos comprados.

Control individual de garantías.

Control de los plazos de devolución.

Alertas y recordatorios.

Reconocimiento automático de información.

Sincronización entre dispositivos.

Cuenta personal con autenticación segura.

Integraciones con Gmail y otras aplicaciones.

Estadísticas de compras.

Buscador avanzado.

Experiencia offline.

Herramientas para reclamaciones y seguimiento de incidencias.

El objetivo es reducir al mínimo las acciones necesarias para guardar una compra.

Principio fundamental:

Un usuario debería poder guardar un ticket, reconocer su contenido y registrar los productos en pocos segundos.

La aplicación nunca debe inventar datos que el reconocimiento automático no haya identificado con suficiente confianza.

1.1 Identidad del producto

Desarrollador: Sergiotech.

Plataforma: Android nativo.

Nombre de la aplicación: TicketTracker.

Subtítulo descriptivo: Tickets, garantías y devoluciones

Eslogan: Tus compras, siempre bajo control

Package name provisional: es.sergiotech.tickettracker.

Idioma inicial: español e inglés.

Idiomas previstos: otros idiomas posteriormente.

Mercado inicial: España.

Preparación arquitectónica para internacionalización.

Todos los paquetes Android de Sergiotech deben comenzar por es.sergiotech..

Nombre de trabajo: TicketTracker. Antes del lanzamiento, verificar disponibilidad registral (OEPM/EUIPO), posibles conflictos comerciales y disponibilidad en Google Play; no interpretar una búsqueda de nombre como autorización legal.

No generar branding definitivo sin una validación previa del nombre.

2. STACK TECNOLÓGICO OBLIGATORIO

2.1 Aplicación Android

Utilizar exclusivamente tecnologías nativas:

Kotlin.

Jetpack Compose.

Material Design 3.

Material You.

Kotlin Coroutines.

Kotlin Flow / StateFlow.

AndroidX.

Navigation Compose.

Hilt para inyección de dependencias.

Room para persistencia local.

DataStore para preferencias.

WorkManager para trabajos persistentes.

CameraX para captura.

Android Photo Picker y Storage Access Framework.

ML Kit para OCR.

Credential Manager para autenticación compatible.

Android Keystore cuando corresponda.

Gradle Kotlin DSL.

Version Catalogs.

KSP donde resulte adecuado.

Seleccionar versiones estables y compatibles, consultando su documentación actual.

No utilizar dependencias obsoletas, experimentales sin necesidad o versiones inventadas.

No usar Flutter, React Native, Ionic, interfaces WebView como pantalla principal ni componentes XML tradicionales para construir las pantallas de negocio.

2.2 Compatibilidad Android

Configurar inicialmente:

minSdk = 29.

targetSdk >= 36.

compileSdk compatible con el target seleccionado.

Antes de fijar definitivamente estos valores, comprobar los requisitos actuales de Google Play y el soporte real de las dependencias.

No incrementar innecesariamente la versión mínima.

El objetivo es compatibilidad amplia con dispositivos Android modernos, sin mantener tecnologías antiguas por motivos puramente históricos.

Optimizar especialmente para dispositivos Android de gama media.

2.3 Backend

Backend inicial decidido: Supabase Self-Hosted existente, sometido a auditoría previa y con una capa de adaptación migrable.

PostgreSQL.

Supabase Auth.

Supabase Storage.

Row Level Security.

Edge Functions cuando estén justificadas.

Migraciones SQL versionadas.

Diseñar el proyecto para que el proveedor backend quede desacoplado de la lógica de dominio.

No utilizar credenciales administrativas ni claves service_role dentro del APK.

Existe una instalación Supabase Self-Hosted compartible, pero su versión, capacidad, aislamiento, autenticación, copias y aptitud para producción están pendientes de auditoría. Sin acceso o credenciales, utilizar adaptadores locales verificables sin tocar producción.

Evaluar esquema PostgreSQL dedicado tickettracker, RLS y roles aislados, límites de recursos y separación de secretos; si la auditoría detecta riesgos para otros proyectos, valorar instancia independiente. Compartir esquema no equivale a aislamiento completo.

2.4 Arquitectura

Aplicar Clean Architecture con:

Presentation.

Domain.

Data.

Core.

Infrastructure.

Patrones:

MVVM.

Unidirectional Data Flow.

Repository Pattern.

Use Cases.

Dependency Injection.

Single Source of Truth.

Offline-first.

Evitar abstracciones innecesarias que aumenten el mantenimiento sin mejorar la calidad.

3. DISEÑO VISUAL Y EXPERIENCIA DE USUARIO

Esta es una prioridad máxima del proyecto.

La aplicación debe transmitir:

Modernidad.

Confianza.

Simplicidad.

Profesionalidad.

Seguridad.

Rapidez.

Orden.

Sensación de producto premium.

No quiero un aspecto genérico generado automáticamente.

Diseñar un sistema visual coherente basado en Material 3 y Material You, con identidad propia.

3.1 Diseño

Implementar:

Dynamic Color en dispositivos compatibles.

Paleta propia como alternativa.

Modo claro.

Modo oscuro.

Modo automático.

Tipografía cuidada.

Escalas tipográficas consistentes.

Componentes reutilizables.

Tarjetas con jerarquía visual.

Navegación intuitiva.

Transiciones suaves.

Estados vacíos diseñados.

Skeleton loading.

Microinteracciones discretas.

Animaciones respetuosas con las preferencias de accesibilidad.

Efectos visuales que no perjudiquen el rendimiento.

No sobrecargar la interfaz con degradados, sombras o animaciones innecesarias.

3.2 Navegación principal

Proponer una navegación inferior con cuatro áreas:

Inicio

Panel general de actividad y alertas.

Mis compras

Inventario completo de compras y productos.

Documentos

Tickets, facturas y justificantes digitalizados.

Perfil

Cuenta, preferencias, integraciones y configuración.

Incorporar una acción principal especialmente visible para añadir una compra o escanear un ticket.

Evaluar un Floating Action Button de Material 3 o una acción equivalente.

3.3 Pantalla de inicio

Mostrar información útil, sin saturación:

Mensaje de bienvenida contextual.

Número de compras guardadas.

Artículos con garantías activas.

Devoluciones próximas a vencer.

Garantías próximas a finalizar.

Compras recientes.

Acceso rápido al escáner.

Acceso a importación de documentos.

Estado de sincronización cuando sea relevante.

Las alertas urgentes deben destacar sobre las estadísticas secundarias.

No convertir la pantalla principal en un panel financiero complejo.

3.4 Pantalla de detalle de compra

Debe incluir:

Nombre del establecimiento.

Fecha de compra.

Fecha de entrega cuando corresponda.

Importe total.

Moneda.

Tipo de compra.

Número de pedido.

Documentación asociada.

Productos incluidos.

Estado de devolución.

Estado de garantía.

Notas personales.

Acciones rápidas.

Información de confianza de los datos extraídos.

Permitir editar cualquier campo.

3.5 Pantalla de producto

Cada artículo podrá tener:

Nombre.

Marca.

Modelo.

Categoría.

Precio.

Cantidad.

Número de serie opcional.

Fecha de adquisición.

Fecha de recepción o entrega.

Establecimiento.

Garantía legal aplicable.

Garantía comercial adicional.

Fecha de finalización.

Política de devolución.

Fecha límite de devolución.

Documentos asociados.

Estado del producto.

Historial de reclamaciones.

Fotografías opcionales.

Notas.

Permitir que varios artículos pertenezcan a una misma compra.

Un ticket no equivale necesariamente a un solo producto.

4. SISTEMA INTELIGENTE DE CAPTURA DE TICKETS

Implementar múltiples métodos de importación.

4.1 Cámara

Utilizar CameraX para:

Fotografiar tickets.

Detectar automáticamente los bordes cuando sea viable.

Permitir recorte manual.

Corregir perspectiva.

Corregir orientación.

Mejorar contraste.

Reducir sombras cuando sea viable.

Procesar tickets largos.

Capturar varias páginas.

Previsualizar antes del guardado.

Repetir una fotografía.

Unificar varias imágenes en un documento.

No bloquear el hilo principal.

El usuario debe poder cancelar el proceso.

4.2 Importación de documentos

Aceptar:

JPEG.

PNG.

WebP.

HEIC cuando el sistema pueda decodificarlo.

PDF.

Archivos de imagen compatibles.

Documentos compartidos desde otras aplicaciones.

Utilizar Storage Access Framework y Android Sharesheet.

No solicitar permisos generales de almacenamiento cuando sean evitables.

Proteger el procesamiento frente a archivos corruptos, maliciosos, enormes o incompatibles.

4.3 Procesamiento automático

Crear un pipeline:

Validar el archivo.

Detectar formato.

Verificar límites de tamaño.

Decodificar eficientemente.

Corregir orientación.

Detectar región útil.

Aplicar recorte.

Mejorar legibilidad.

Ejecutar OCR.

Extraer información estructurada.

Evaluar confianza.

Generar documento optimizado.

Calcular huella de duplicado.

Guardar localmente.

Programar sincronización.

Mostrar confirmación editable.

Separar el procesamiento de imagen, OCR, extracción semántica y almacenamiento.

Cada fase debe poder probarse independientemente.

5. OPTIMIZACIÓN EXTREMA DEL ALMACENAMIENTO

Quiero minimizar el consumo de almacenamiento local y cloud sin destruir la calidad documental.

Implementar compresión inteligente adaptativa.

5.1 Imágenes

Evaluar:

WebP.

JPEG.

AVIF cuando exista soporte práctico.

Compresión con pérdida y sin pérdida.

Redimensionamiento adaptativo.

Escala de grises para documentos adecuados.

Eliminación de márgenes sobrantes.

Calidad variable en función del contenido.

No usar un porcentaje de compresión fijo para todas las imágenes.

Evaluar nitidez, tamaño y calidad del texto.

La compresión no puede hacer ilegible el ticket.

5.2 PDFs

Diferenciar entre:

PDF con texto embebido.

PDF escaneado.

PDF mixto.

Documento firmado electrónicamente.

PDF con características que deban preservarse.

No rasterizar indiscriminadamente PDFs vectoriales.

Preservar firmas electrónicas y estructura documental cuando su modificación pueda invalidar el documento.

Si el documento original posee valor probatorio o una firma, conservar el original y generar una copia optimizada separada cuando proceda.

5.3 Estrategia de conservación

Diseñar tres representaciones cuando corresponda:

Original.

Documento optimizado.

Miniatura.

No conservar todas obligatoriamente.

Definir una política basada en:

Tipo documental.

Legibilidad.

Valor del original.

Existencia de firma.

Preferencias del usuario.

Coste de almacenamiento.

Permitir al usuario configurar la conservación del original, informándole de las implicaciones.

No eliminar originales silenciosamente.

5.4 Duplicados

Detectar duplicados mediante:

Hash criptográfico para archivos idénticos.

Hash perceptual para imágenes visualmente similares.

Coincidencia de establecimiento.

Fecha.

Importe.

Identificador de factura.

Número de pedido.

No fusionar compras automáticamente por coincidencias débiles.

Mostrar la sospecha de duplicado y permitir revisión.

5.5 Objetivos de rendimiento

Establecer presupuestos medibles para:

Tiempo de importación.

Tiempo de OCR.

Memoria máxima.

Tamaño medio de documentos.

Consumo de datos.

Fallos de procesamiento.

Tiempo hasta la disponibilidad local.

Medir resultados en dispositivos reales representativos.

No prometer reducciones de tamaño arbitrarias ni degradar la calidad documental para cumplir un porcentaje.

6. RECONOCIMIENTO INTELIGENTE DE TICKETS

Utilizar inicialmente ML Kit OCR en el dispositivo.

Favorecer procesamiento local para mejorar privacidad, latencia y costes.

6.1 Campos

Extraer cuando sea posible:

Comercio.

CIF/NIF del establecimiento.

Fecha.

Hora.

Número de ticket.

Número de factura.

Importe total.

Impuestos.

Moneda.

Productos.

Cantidades.

Precios unitarios.

Descuentos.

Tipo de compra.

Número de pedido.

No confundir el total con subtotales, impuestos o importes de descuento.

6.2 Interpretación

Crear un motor de extracción estructurada con reglas verificables.

Cuando sea necesario, diseñar una interfaz para futuros modelos inteligentes, pero no introducir dependencias de IA generativa de pago sin justificación.

El sistema deberá:

Asociar textos y campos.

Detectar formatos regionales.

Distinguir fechas ambiguas.

Manejar varios idiomas.

Identificar múltiples productos.

Evitar inventar información.

Registrar puntuaciones de confianza.

6.3 Confirmación

Tras procesar el ticket, mostrar:

"Hemos encontrado estos datos"

Presentar campos precompletados con edición rápida.

Señalar únicamente los valores dudosos.

No obligar a reescribir información correctamente detectada.

Si el OCR falla, mantener el documento y ofrecer registro manual.

7. MODELO DE COMPRAS E INVENTARIO

Separar las siguientes entidades:

Usuarios.

Perfiles.

Comercios.

Compras.

Líneas de compra.

Productos o activos personales.

Documentos.

Garantías.

Políticas de devolución.

Devoluciones.

Reclamaciones.

Notificaciones.

Integraciones.

Eventos de sincronización.

Una compra podrá incluir varios productos.

Un producto podrá tener múltiples documentos.

Un documento podrá estar asociado a una compra y a distintos productos.

Una devolución podrá ser parcial.

Una reclamación podrá afectar únicamente a determinados artículos.

Contemplar compras conjuntas, descuentos globales, cantidades múltiples y devoluciones parciales.

8. MOTOR DE GARANTÍAS

Desarrollar un motor configurable por jurisdicción.

8.1 España

Para las categorías a las que resulte aplicable, contemplar inicialmente la garantía legal general de tres años para bienes de consumo nuevos desde su entrega, según la normativa vigente.

No aplicar automáticamente esta regla a todas las transacciones.

Considerar:

Bienes nuevos.

Bienes de segunda mano.

Compras entre particulares.

Servicios.

Contenidos digitales.

Compras empresariales.

Productos excluidos.

Garantías comerciales ampliadas.

Garantías del fabricante.

Reparaciones o sustituciones.

Suspensión o alteración de plazos cuando proceda.

Las reglas legales deben estar versionadas y documentadas.

8.2 Cálculo

El motor deberá distinguir:

Fecha de compra.

Fecha de recepción.

Fecha de entrega.

Fecha de activación, cuando sea legalmente relevante.

Fecha de inicio de garantía.

Fecha prevista de vencimiento.

Extensiones o modificaciones.

No asumir que la fecha del ticket siempre coincide con el inicio de la garantía.

8.3 Presentación

Mostrar estados:

Garantía activa.

Próxima a caducar.

Caducada.

Pendiente de determinar.

Requiere revisión.

Permitir consultar por qué se calculó cada fecha.

No presentar estimaciones como conclusiones jurídicas definitivas.

9. SISTEMA DE DEVOLUCIONES

El sistema de devolución es independiente de la garantía.

Distinguir:

Desistimiento legal.

Devolución comercial voluntaria.

Cambio de producto.

Reclamación por falta de conformidad.

Política específica del vendedor.

Contemplar inicialmente el derecho general de desistimiento de 14 días naturales para determinadas compras a distancia en España, sujeto a las excepciones aplicables.

No atribuir automáticamente ese derecho a todas las compras en establecimientos físicos.

9.1 Datos

Canal de compra.

Comercio.

Fecha de entrega.

Política aplicable.

Plazo.

Fecha de vencimiento.

Producto.

Estado.

Justificante.

Número de devolución.

Reembolso esperado.

Reembolso realizado.

9.2 Estados

Dentro del plazo.

Próxima a vencer.

Fuera de plazo.

Devolución solicitada.

En trámite.

Devuelta.

Reembolsada.

Rechazada.

Sin información suficiente.

Permitir reglas específicas configurables por comercio, verificadas y modificables.

10. SISTEMA DE NOTIFICACIONES

Crear recordatorios configurables.

Ejemplos:

Devolución próxima a finalizar.

Garantía próxima a caducar.

Reembolso pendiente.

Reclamación sin resolver.

Documento pendiente de sincronización.

Integración que requiere reconexión.

10.1 Configuración

Permitir al usuario seleccionar anticipación:

1 día.

3 días.

7 días.

15 días.

30 días.

Personalizada.

No todos los tipos de eventos tendrán necesariamente las mismas opciones.

10.2 Implementación

Usar notificaciones locales y WorkManager para mantenimiento y programación persistente.

No asumir que WorkManager garantiza ejecución exacta al minuto.

Si un recordatorio realmente requiere una alarma exacta, evaluar las APIs y restricciones vigentes y justificar su uso.

Solicitar permisos de notificación de forma contextual.

Respetar preferencias del usuario y configuraciones de ahorro energético.

Evitar notificaciones duplicadas.

11. CUENTAS, IDENTIDAD Y SINCRONIZACIÓN

Los usuarios deben poder cambiar de móvil o reinstalar la aplicación sin perder sus datos ya sincronizados.

11.1 Autenticación

Incluir:

Google Sign-In mediante APIs vigentes.

Email y contraseña o método passwordless.

Recuperación de cuenta.

Cierre de sesión.

Eliminación de cuenta.

Gestión de sesión.

Reautenticación para acciones sensibles.

Diseñar las identidades para que un mismo usuario no termine accidentalmente con dos inventarios independientes al cambiar de método de acceso.

11.2 Sincronización

Implementar estrategia offline-first.

La base de datos local debe permitir consultar y modificar compras sin conexión.

Los cambios se enviarán al backend posteriormente.

Contemplar:

Cola persistente de operaciones.

Identificadores estables.

Idempotencia.

Reintentos.

Backoff exponencial.

Cambios concurrentes.

Resolución de conflictos.

Borrados seguros.

Tumbas lógicas cuando correspondan.

Descarga de cambios remotos.

Estado de sincronización observable.

Evitar sobrescrituras silenciosas.

11.3 Primera sincronización

Cuando el usuario instale la app en otro teléfono:

Inicia sesión.

Se recupera su perfil.

Se descargan metadatos.

Aparece su inventario.

Los documentos se descargan bajo demanda.

Se precargan miniaturas prioritarias.

La aplicación continúa operativa.

No descargar innecesariamente todos los documentos completos durante el inicio de sesión.

12. INTEGRACIÓN CON GMAIL

La integración con Gmail es una característica importante, pero debe implementarse respetando los requisitos de Google.

Distinguir claramente:

Autenticación con Google.

Autorización de acceso a Gmail.

No asumir que son el mismo permiso.

12.1 Integración directa

Diseñar un conector de Gmail basado en OAuth 2.0.

Investigar los scopes mínimos compatibles con el objetivo.

Evaluar las restricciones de Gmail API, los requisitos de verificación y las posibles evaluaciones externas de seguridad.

No utilizar métodos no oficiales ni accesos automatizados con contraseñas.

12.2 Detección de justificantes

Cuando el usuario autorice la conexión:

Buscar correos de compras.

Detectar recibos.

Detectar facturas.

Detectar confirmaciones de pedidos.

Localizar adjuntos.

Identificar comerciantes.

Obtener importes y fechas.

Reconocer números de pedido.

Importar documentos seleccionados.

Asociarlos a compras existentes.

12.3 Privacidad

Priorizar la búsqueda y lectura limitada a lo estrictamente necesario.

No analizar conversaciones personales ajenas al objetivo.

No enviar contenido completo de correos a servicios de IA externos sin consentimiento y justificación.

Evitar almacenar correos completos cuando solo sea necesario conservar una factura.

No diseñar la función sobre una promesa de aprobación OAuth todavía inexistente.

12.4 Duplicados

Una factura importada desde Gmail podría estar ya almacenada manualmente.

Detectar y mostrar coincidencias antes de guardar.

12.5 Fase alternativa

Mientras no exista autorización para la integración directa:

Permitir compartir un correo mediante las capacidades disponibles de Android.

Importar adjuntos descargados.

Importar PDF desde Gmail.

Procesar archivos recibidos por Android Sharesheet.

Admitir reenvío a una dirección dedicada únicamente en una fase futura, si se habilita infraestructura específica.

La aplicación debe ser plenamente funcional sin acceso directo a Gmail.

13. INTEGRACIONES ADICIONALES

Diseñar una arquitectura de conectores extensible.

Priorizar:

Gmail.

Android Sharesheet.

Android Photo Picker.

Gestor de archivos.

Google Drive mediante selección de archivos.

Importación manual de PDF.

Aplicaciones de comercio que permitan compartir facturas.

Navegadores móviles.

Otras aplicaciones de correo mediante documentos compartidos.

No asumir que todas las tiendas o aplicaciones ofrecen APIs públicas.

Preparar conectores futuros para plataformas comerciales únicamente cuando existan vías autorizadas de integración.

No utilizar scraping autenticado como solución improvisada.

14. BÚSQUEDA E INVENTARIO

Implementar búsqueda rápida y potente.

Permitir búsquedas por:

Nombre del producto.

Marca.

Modelo.

Tienda.

Categoría.

Importe.

Fecha.

Número de pedido.

Número de factura.

Texto reconocido del ticket.

Estado de garantía.

Estado de devolución.

Evaluar Room FTS para indexación local.

Diseñar filtros combinables.

Incluir ordenación por:

Fecha.

Precio.

Comercio.

Caducidad.

Nombre.

Evitar consultas costosas ejecutadas en el hilo principal.

15. ESTADÍSTICAS

Crear una sección ligera de información útil:

Total gastado por periodo.

Número de compras.

Comercios frecuentes.

Categorías.

Compras por mes.

Artículos con garantía activa.

Devoluciones realizadas.

Importe recuperado mediante devoluciones.

No utilizar estadísticas de gasto como excusa para solicitar acceso a cuentas bancarias.

Las estadísticas deberán utilizar los datos registrados en la aplicación.

16. SEGURIDAD Y PROTECCIÓN DE DATOS

La privacidad es un requisito central.

Los justificantes pueden contener información identificativa, direcciones, números de pedido, datos fiscales y otros datos sensibles.

16.1 Backend

Aplicar:

Row Level Security en todas las tablas privadas.

Políticas por propietario.

Storage privado.

Rutas de almacenamiento aisladas.

Autorización por usuario.

Validación de entradas.

Límites de tamaño.

Control de acceso a archivos.

URLs firmadas de duración limitada.

Protección frente a enumeración de identificadores.

Auditoría de operaciones relevantes.

No confiar únicamente en controles de autorización del cliente Android.

16.2 Cliente

Implementar:

Gestión segura de credenciales.

Exclusión de secretos de repositorios.

Protección de datos temporales.

Borrado de cachés sensibles.

Manejo seguro de URI.

Protección frente a exposición mediante logs.

Eliminación de metadatos innecesarios.

Seguridad de componentes exportados.

Configuración segura de Network Security.

Evaluar cifrado adicional en función del modelo de amenazas.

16.3 Privacidad y RGPD

Preparar:

Política de privacidad.

Términos de uso.

Información sobre tratamiento de datos.

Derecho de acceso.

Exportación de información.

Rectificación.

Supresión.

Gestión de consentimiento.

Revocación de integraciones.

Política de conservación.

Eliminación de cuenta.

Borrado de documentos en backend según el proceso establecido.

No declarar cumplimiento jurídico definitivo sin revisión.

17. RENDIMIENTO Y FLUIDEZ

La aplicación debe ser rápida incluso con miles de compras.

17.1 Interfaz

Optimizar:

Recomposiciones de Compose.

Estabilidad de estados.

LazyColumn.

Paginación.

Renderizado de imágenes.

Carga progresiva.

Navegación.

Animaciones.

Consumo de memoria.

Evitar ejecutar OCR, compresión y operaciones de red en el hilo principal.

17.2 Almacenamiento

Miniaturas cacheadas.

Descarga bajo demanda.

Caché con límites.

Eliminación segura de temporales.

Procesamiento en streaming cuando sea adecuado.

Tamaños acotados de bitmaps.

Liberación correcta de recursos.

17.3 Medición

Incluir:

Macrobenchmark.

Baseline Profiles.

Pruebas de arranque.

Medición de jank.

Pruebas de desplazamiento.

Pruebas de memoria.

Pruebas de importación masiva.

Establecer objetivos preliminares razonables y validarlos mediante benchmarks reproducibles.

18. ACCESIBILIDAD E INTERNACIONALIZACIÓN

Cumplir buenas prácticas Android:

TalkBack.

Etiquetas semánticas.

Tamaños táctiles adecuados.

Contraste.

Escalado de fuentes.

Navegación por teclado cuando corresponda.

Lectores de pantalla.

Estados comprensibles sin depender exclusivamente de colores.

Soporte para pantallas pequeñas y grandes.

Preparar:

Textos mediante recursos.

Formatos regionales.

Monedas.

Zonas horarias.

Diferencias entre fechas locales y marcas temporales.

Plurales.

Distintos formatos legales por país.

Nunca codificar textos de interfaz directamente en todos los componentes.

19. MODELO DE DATOS

Diseñar antes de implementar la capa de persistencia.

Como mínimo, definir entidades equivalentes a:

UserProfile

Merchant

Purchase

PurchaseLine

Product

Document

DocumentVariant

Warranty

ReturnPolicy

ReturnCase

Claim

NotificationPreference

IntegrationConnection

SyncOperation

AuditEvent

Estas denominaciones son orientativas.

Definir:

Campos.

Tipos.

Relaciones.

Índices.

Restricciones.

Claves.

Timestamps.

Estados.

Políticas de borrado.

Versionado.

Migraciones.

No duplicar información sin justificación.

Diseñar para futuras extensiones sin sacrificar simplicidad.

20. ESTRUCTURA DEL REPOSITORIO

Utilizar un proyecto Gradle modular.

Estructura de referencia:

tickettracker/

├── app/

├── core/

│   ├── common/

│   ├── model/

│   ├── designsystem/

│   ├── ui/

│   ├── database/

│   ├── datastore/

│   ├── network/

│   ├── security/

│   ├── testing/

│   ├── documents/

│   ├── ocr/

│   └── sync/

├── domain/

│   ├── purchases/

│   ├── warranties/

│   ├── returns/

│   └── documents/

├── feature/

│   ├── onboarding/

│   ├── auth/

│   ├── home/

│   ├── purchases/

│   ├── purchase-detail/

│   ├── products/

│   ├── scanner/

│   ├── documents/

│   ├── warranties/

│   ├── returns/

│   ├── search/

│   ├── notifications/

│   ├── integrations/

│   ├── statistics/

│   └── settings/

├── sync/

├── backend/

│   └── supabase/

│       ├── migrations/

│       ├── policies/

│       ├── tests/

│       └── functions/

├── benchmark/

├── docs/

├── gradle/

├── build-logic/

├── .github/

├── README.md

└── settings.gradle.kts

Esta estructura es una propuesta inicial, no una obligación de crear decenas de módulos vacíos.

Evalúa dependencias, complejidad y tiempos de compilación.

Crear cada módulo cuando aporte separación real de responsabilidades.

21. SISTEMA DE PRUEBAS

La aplicación deberá contar con pruebas desde las primeras fases.

21.1 Unitarias

Probar:

Cálculo de garantías.

Cálculo de devoluciones.

Reglas regionales.

Modelos.

Validadores.

Casos de uso.

Extracción de datos.

Duplicados.

Resolución de conflictos.

Reintentos.

21.2 Integración

Probar:

Room.

Migraciones.

Repositorios.

Sincronización.

Backend.

Supabase Storage con backend S3/R2 y pruebas de permisos, integridad y cargas reanudables.

Permisos.

Sesiones.

RLS mediante tests de aislamiento de usuarios.

21.3 UI

Implementar pruebas Compose:

Onboarding.

Login.

Navegación.

Añadir compra.

Guardar documento.

Editar producto.

Filtrar inventario.

Configurar alertas.

Cerrar sesión.

21.4 Casos adversos

Probar:

Sin conexión.

Conexión intermitente.

Archivo corrupto.

OCR sin resultados.

PDF muy grande.

Importación cancelada.

Poco almacenamiento.

Aplicación cerrada durante subida.

Sesión caducada.

Cambio de dispositivo.

Compras simultáneamente editadas.

Dos usuarios distintos.

Documentos duplicados.

Fallos parciales de sincronización.

No considerar una funcionalidad terminada únicamente porque compile.

22. CI/CD Y CALIDAD

Preparar GitHub Actions u otra solución equivalente.

Pipeline:

Validación Gradle.

Formato.

Análisis estático.

Tests unitarios.

Tests de integración disponibles.

Compilación debug.

Comprobaciones de seguridad.

Verificación de dependencias.

Generación de artefactos de prueba.

Evaluar herramientas como:

Detekt.

KtLint o alternativa coherente.

Android Lint.

Dependabot.

Gradle dependency verification.

No generar automatizaciones que requieran credenciales inexistentes sin proporcionar previamente una configuración clara.

Nunca introducir claves privadas en el repositorio.

23. DOCUMENTACIÓN

Generar documentación en español.

Como mínimo:

docs/

├── product/

│   ├── PRODUCT_VISION.md

│   ├── REQUIREMENTS.md

│   ├── USER_STORIES.md

│   └── ROADMAP.md

├── architecture/

│   ├── ARCHITECTURE.md

│   ├── ADR/

│   ├── DATA_MODEL.md

│   ├── SYNC_STRATEGY.md

│   └── SECURITY.md

├── design/

│   ├── DESIGN_SYSTEM.md

│   ├── NAVIGATION.md

│   └── USER_FLOWS.md

├── integrations/

│   ├── GMAIL.md

│   └── IMPORTS.md

├── quality/

│   ├── TEST_STRATEGY.md

│   └── PERFORMANCE.md

└── DELIVERY_STATUS.md

Mantener un documento central de seguimiento.

Registrar:

Trabajo completado.

Trabajo pendiente.

Bloqueos.

Decisiones.

Riesgos.

Pruebas ejecutadas.

Resultados.

Siguiente actividad.

Distinguir siempre IMPLEMENTED, VERIFIED, BLOCKED y NOT_STARTED.

No marcar como verificada una funcionalidad que no ha sido realmente probada.

Cloudflare R2 precios y cuotas: https://developers.cloudflare.com/r2/pricing/

Supabase actualización autohospedada: https://supabase.com/docs/guides/self-hosting/updating

Supabase S3 backend autohospedado: https://supabase.com/docs/guides/self-hosting/self-hosted-s3

23 BIS.11 Fuentes oficiales de implementación (revisar versiones vigentes)

No declarar READY una función cloud sin evidencias: pruebas de límites por usuario, RLS, acceso cruzado denegado, retorno tras caída, documento optimizado legible, control de caché, integridad R2/Postgres y medición de consumo. Toda operación en infraestructura compartida de producción requiere inventario de cambios, backup, ventana de reversión y aprobación explícita.

Añadir a Fase 0 la auditoría del Supabase existente, benchmark base, estrategia de R2, amenazas, presupuesto y plan de reversión. Añadir a Fase 2 contratos migrables, RLS y pruebas multi-tenant. Añadir a Fase 4 análisis de calidad documental y costes reales. Añadir a Fase 6 sincronización con pruebas offline y cambio de móvil. Añadir a Fase 9 ensayo de backup/restore, incident response, control de costes y migración simulada.

23 BIS.10 Cambios obligatorios en roadmap y definición de terminado

Criterio de salida: una migración completa de ensayo deberá demostrar recuperación del inventario y documentos de dos usuarios distintos sin filtraciones, conflictos resueltos, hashes coincidentes, sesiones renovables, notificaciones intactas y rollback operativo.

Etapa M4 — Estabilización: conciliación final de filas/objetos, monitoreo de errores y desincronización, retirada diferida de origen, borrado conforme a política de retención y documentación de resultados. El usuario conserva la misma aplicación, cuenta e inventario salvo cambios de autenticación técnicamente necesarios y comunicados.

Etapa M3 — Transición sin pérdida: sincronización inicial, replicación lógica/CDC o dual-write controlado solo si es técnicamente viable, control de versiones de API, congelación breve de escrituras cuando sea necesaria, cambio de rutas mediante dominio propio, verificación, observabilidad reforzada y rollback ensayado. No asumir migración instantánea de Supabase Auth o Storage entre proveedores.

Etapa M2 — Ensayo: backup consistente y exportación; desplegar entorno destino aislado; migrar esquema, roles/políticas, usuarios/identidades y archivos/manifiestos; verificar recuentos, integridad y aislamiento; ejecutar tests automáticos, carga y restauración; estimar ventana de cambio.

Etapa M1 — Disparadores: capacidad del VPS, latencias p95, incidentes, carga de otros proyectos, crecimiento del volumen documental, coste total o necesidad de SLA. Evaluar PostgreSQL gestionado, Supabase gestionado, PostgreSQL dedicado u otras alternativas; elegir por pruebas y TCO, no por marca.

Etapa M0 — Preparación desde MVP: contratos por proveedor, esquema versionado, dominios propios, UUID estables, exportaciones, scripts reproducibles, migraciones up/down cuando sean seguras, monitorización y tests contra adaptadores alternativos.

23 BIS.9 Plan de migración progresiva cuando existan ingresos

Definir backups separados de PostgreSQL (datos, roles, esquemas, configuraciones, secretos necesarios y punto de restauración) y de R2 (versionado/replicación/copia independiente según criticidad y presupuesto). Realizar restores reales periódicos en un entorno aislado. Documentar RPO/RTO objetivos antes del lanzamiento y comprobar integridad mediante checksums. El simple uso de R2 no equivale a disponer de backup.

23 BIS.8 Backup y recuperación de desastres

Instrumentar métricas sin PII: usuarios activos, tickets/usuario, KB/ticket por formato, ratio de compresión, almacenamiento remoto, subidas, descargas, cache hit local, lecturas/escrituras Postgres, latencias p50/p95, tasa de error, cola de sincronización y coste estimado por 1.000 usuarios. Presupuestos, alarmas y cuotas server-side: las alertas no constituyen un límite financiero garantizado.

R2 Standard: prever almacenamiento GB-mes, operaciones A/B, versiones, duplicados, copias, tráfico que pase por el VPS, Workers si se usan y costes del servidor. Sus precios y cuotas deben leerse en documentación oficial en el momento del despliegue; la gratuidad de egress de R2 no implica que el conjunto sea gratis.

23 BIS.7 Optimización de costes y observabilidad

La caché compartida CDN de recibos privados queda desactivada en el MVP. Usar almacenamiento interno de Android, políticas no-store en respuestas sensibles cuando proceda, cifrado en reposo según evaluación de amenazas, y caché etiquetada por usuario/version/hash. Limpiar cuentas anteriores al cambiar de identidad para impedir fugas cruzadas.

Buckets R2 siempre privados: evitar dominio público accesible sin autorización. Si hay URLs temporales, caducidad corta, alcance exacto por objeto/método, registro mínimo y revocación según necesidades. RLS en tablas y Storage, autorización en servidor, tokens tratados como secretos, HTTPS extremo a extremo, controles antiabuso y límites de tamaño y frecuencia por usuario.

23 BIS.6 Seguridad, privacidad y caché

Máquina de estados del documento: LOCAL_ONLY → QUEUED → UPLOADING → REMOTE_OBJECT_PENDING_COMMIT → SYNCED_VERIFIED; en errores RETRYABLE_ERROR o ACTION_REQUIRED. Confirmar tamaño/hash y autorización del objeto; limpiar objetos huérfanos con jobs seguros. Evitar duplicidad sin fusionar por hashes perceptuales débiles.

Usar outbox persistente en Room para operaciones offline y cursor incremental remoto (secuencia asignada en transacciones de servidor). Las mutaciones serán idempotentes, con revisión esperada, conflictos detectables, reintentos exponenciales y estados visibles. No depender solo de updated_at: pueden perderse cambios entre transacciones concurrentes. Tratar borrados mediante tombstones con retención definida.

23 BIS.5 Protocolo de sincronización

Separar identidad interna del usuario de identificadores específicos del proveedor. Crear plan de equivalencias de identidad para futuros cambios de Auth sin perder la propiedad de documentos. Los datos deben poder exportarse en formatos estándar (SQL/CSV/JSON según tipo), con un manifiesto de documentos y verificaciones de integridad.

Mantener UUID estables para UserProfile, Purchase, PurchaseLine, Product, Document, DocumentVariant, Warranty, ReturnPolicy, ReturnCase, Claim, SyncOperation y AuditEvent. Añadir revisiones monotónicas por entidad, idempotency_key, deleted_at, created_at, updated_at, checksum SHA-256, mime_type, byte_size, storage_key opaco, estado de subida y retention_policy. Nunca guardar URLs temporales como claves permanentes.

23 BIS.4 Modelo de datos y versionado

Proponer esquema tickettracker con migraciones versionadas, roles mínimos y pruebas RLS con usuarios diferentes. Validar grants, search_path, exposición de esquemas de API, dependencia de auth.users, índices user_id, límites por usuario y cuotas. Si el riesgo de interferencia es alto, usar instancia independiente y una planificación de coste de infraestructura antes de conectar usuarios reales.

Auditar antes de desplegar: recursos CPU/RAM/IOPS, espacio y crecimiento de Postgres, pools, límites de conexiones, versión de PostgreSQL y componentes Supabase, uptime, certificados TLS, actualizaciones, alarmas, retención de logs y compatibilidad de Storage/R2. Documentar carga de los otros proyectos y evitar modificaciones globales sin autorización.

23 BIS.3 Multi-proyecto, aislamiento y disponibilidad

Diseñar dominios y contratos bajo control de Sergiotech (por ejemplo, endpoints API propios), con DNS configurable, versionado de API, mecanismos para redirigir a un nuevo backend y una política de compatibilidad con versiones Android antiguas. No fijar URLs de proveedor en lógica de negocio.

Supabase Storage con backend S3-compatible R2 es la vía inicial preferente. Verificar soporte exacto en la versión instalada, variables del servicio Storage, generación de URLs, límites de carga, compatibilidad TUS y compatibilidad de RLS. Un futuro camino de URLs prefirmadas emitidas por una API segura puede descargar al VPS de tráfico si los benchmarks lo justifican; nunca debe entregar claves permanentes al cliente.

Android nativo → capa Data con Room, caché privada, cola de operaciones e interfaces → HTTPS/TLS → Supabase Self-Hosted (Auth, PostgREST/RPC controladas, PostgreSQL+RLS) → Supabase Storage API → Cloudflare R2 Standard privado como backend S3. Cloudflare puede proteger la exposición de API con DNS/WAF/rate limits bien probados. NO exponer directamente PostgreSQL ni credenciales de R2 al APK.

23 BIS.2 Topología de producción inicial

La sincronización verificada es requisito para prometer recuperación al cambiar de dispositivo. No eliminar el último archivo válido durante limpieza de caché, errores de subida ni cierre de sesión sin política explícita. La instancia remota es la fuente autoritativa compartida, no una simple caché.

MOBILE-FIRST: Room es la fuente observable para la interfaz y mantiene las operaciones offline. Backend-first no está permitido en consultas rutinarias; la sincronización actualiza Room en segundo plano. CLOUD-LAST: documentos grandes se descargan solo cuando el usuario los abre o los fija offline. CACHE-SECOND: memoria y disco privados con LRU, límite por dispositivo e invalidación por versión/hash.

23 BIS.1 Principios no negociables

23 BIS. ARQUITECTURA INFRAESTRUCTURAL Y MIGRACIÓN FUTURA

24. PLAN DE DESARROLLO

Trabajar mediante fases incrementales.

Fase 0 — Análisis y planificación

Examinar el repositorio.

Identificar herramientas disponibles.

Verificar requisitos Android.

Definir alcance MVP.

Diseñar arquitectura.

Auditar Supabase Self-Hosted, integración R2, seguridad, capacidad actual, backups y costes; definir salida futura del proveedor.

Diseñar entidades.

Identificar riesgos legales.

Analizar restricciones OAuth.

Definir roadmap.

Generar documentación.

No extender indefinidamente esta fase.

Fase 1 — Fundación Android

Implementar:

Proyecto Gradle.

Kotlin.

Compose.

Material 3.

Hilt.

Navegación.

Design System.

Temas.

Recursos.

Configuración.

Calidad de código.

CI inicial.

Resultado esperado: aplicación ejecutable con navegación básica y componentes reales.

Fase 2 — Persistencia y cuentas

Implementar:

Room.

Modelos.

Repositorios.

Adaptadores intercambiables para autenticación, metadatos y documentos; implementación inicial Supabase Self-Hosted + Cloudflare R2.

Autenticación.

Perfil.

Sesiones.

Migraciones.

Reglas de seguridad.

Mantener una modalidad local de desarrollo cuando el backend no esté disponible.

Fase 3 — Compras e inventario

Implementar:

Alta manual de compra.

Listado.

Detalle.

Productos.

Edición.

Eliminación.

Búsqueda básica.

Relaciones entre documentos y productos.

Resultado: inventario funcional.

Fase 4 — Digitalización

Implementar:

Cámara.

Selector de archivos.

Previsualización.

Procesamiento.

OCR.

Extracción estructurada.

Confirmación editable.

Compresión.

Guardado local.

Resultado: capturar un ticket real y convertirlo en una compra.

Fase 5 — Garantías y devoluciones

Implementar:

Motor de reglas.

Plazos.

Fechas.

Estados.

Alertas.

Pantallas específicas.

Pruebas jurídicas y temporales.

Resultado: seguimiento completo de productos.

Fase 6 — Sincronización

Implementar:

Subida de metadatos.

Subida de documentos.

Descarga bajo demanda.

Reintentos.

Gestión de conflictos.

Recuperación de cuenta.

Cambio de dispositivo.

Resultado: datos disponibles entre instalaciones.

Fase 7 — Integraciones

Implementar:

Android Sharesheet.

Importación desde otros gestores.

Importación desde Gmail mediante archivos compartidos.

Preparación de Gmail API.

Integración OAuth directa únicamente cuando sea viable y autorizada.

Fase 8 — Calidad y experiencia

Implementar:

Animaciones.

Skeletons.

Estados vacíos.

Accesibilidad.

Optimización.

Benchmarks.

Corrección de errores.

Pruebas en dispositivos.

Fase 9 — Preparación de beta

Preparar:

Android App Bundle.

Iconografía.

Capturas.

Ficha Google Play.

Política de privacidad.

Data Safety.

Documentación legal.

Pruebas internas.

Checklist de publicación.

No publicar sin autorización.

25. CRITERIOS DE ACEPTACIÓN DEL MVP

El MVP debe permitir completar el siguiente escenario:

Un usuario instala la aplicación.

Crea su cuenta.

Accede a una interfaz moderna.

Fotografía un ticket.

El sistema reconoce establecimiento, fecha e importe cuando son legibles.

Identifica los artículos que pueda reconocer.

El usuario revisa y corrige.

Guarda la compra.

El ticket se optimiza.

El inventario se actualiza.

Se calcula la garantía aplicable con la información disponible.

Se calcula el plazo de devolución cuando procede.

Se programan alertas.

El usuario puede localizar la compra.

Puede consultar el ticket.

La información se sincroniza.

Cierra sesión.

Inicia sesión desde otro dispositivo.

Recupera compras y documentos sincronizados.

Puede importar otra factura desde Android Sharesheet.

Este flujo debe tener pruebas reales de extremo a extremo.

No considerar el MVP completo si solo existe una interfaz conectada a datos simulados.

26. REQUISITOS DE CALIDAD DEL CÓDIGO

Exigir:

Código idiomático Kotlin.

Arquitectura consistente.

Componentes pequeños.

Responsabilidades claras.

Estados inmutables cuando corresponda.

Errores tipados.

Resultados explícitos.

Tratamiento adecuado de excepciones.

Cancelación cooperativa de coroutines.

Ausencia de operaciones pesadas en main thread.

Inyección de dependencias.

Tests mantenibles.

Documentación de decisiones complejas.

Prohibido:

Simular resultados reales sin identificar mocks.

Introducir TODOs ocultos en caminos críticos.

Ignorar excepciones.

Almacenar secretos en código.

Utilizar servicios externos sin consentimiento.

Generar código que no compile y marcarlo como terminado.

Omitir verificaciones por comodidad.

Afirmar pruebas que no se hayan ejecutado.

Mezclar indiscriminadamente lógica de negocio con composables.

27. AUTONOMÍA Y FORMA DE TRABAJO

Debes ejecutar el proyecto por etapas, sin detenerte después de redactar el plan.

En cada etapa:

Inspecciona el código existente.

Define el objetivo concreto.

Implementa.

Compila cuando el entorno lo permita.

Ejecuta pruebas.

Corrige errores.

Actualiza documentación.

Registra resultados.

Continúa con el siguiente incremento autorizado.

No solicites confirmación para:

Crear carpetas.

Generar archivos de código.

Refactorizar código recién creado.

Añadir pruebas.

Corregir problemas de compilación.

Documentar.

Diseñar componentes.

Implementar funcionalidad local.

Ejecutar comandos seguros de validación.

Sí debes solicitar autorización para:

Contratar servicios.

Crear recursos externos con coste.

Publicar en Google Play.

Realizar migraciones destructivas sobre datos existentes.

Eliminar información real.

Configurar credenciales de producción.

Acceder a cuentas personales.

Activar permisos OAuth sensibles o restringidos.

Cambiar servicios de producción.

Hacer push a repositorios remotos cuando no esté previamente autorizado.

No bloquear todo el desarrollo porque alguna integración externa esté pendiente.

Implementa una alternativa local verificable y documenta la dependencia.

28. QUÉ DEBES HACER AHORA

Comienza inmediatamente.

Antes de codificar los conectores remotos, audita la instalación Supabase autohospedada disponible y el plan de R2; no ejecutes cambios en producción sin autorización. Diseña los contratos de datos de modo que la migración posterior del backend no obligue a actualizar o reinstalar la aplicación.

Primero:

Inspecciona el workspace y determina si existe algún proyecto iniciado.

Segundo:

Elabora una propuesta arquitectónica definitiva y un roadmap detallado.

Tercero:

Diseña el sistema de datos y la estructura de módulos.

Cuarto:

Define el Design System y la navegación de la aplicación.

Quinto:

Asegura que Room, el motor OCR y los casos de uso funcionan localmente aunque Supabase o R2 estén caídos; aporta tests con adaptadores fake y contratos independientes.

Genera el proyecto Android real.

Sexto:

Implementa la primera versión ejecutable con:

Application.

MainActivity.

Jetpack Compose.

Material 3.

Theme.

Navigation.

HomeScreen.

PurchasesScreen.

DocumentsScreen.

ProfileScreen.

Componentes reutilizables.

Estados de carga, vacío y error.

Base para Room.

Configuración de Hilt.

Tests iniciales.

Séptimo:

Compila, prueba y corrige.

Octavo:

Continúa implementando el primer flujo funcional de registro manual de compras y documentos, siguiendo el plan.

No entregues únicamente capturas de pantalla.

No construyas una aplicación ficticia o visualmente completa pero sin funcionalidades reales.

No detengas el trabajo al finalizar la planificación.

Al concluir cada ejecución, informa:

Archivos creados.

Funcionalidades operativas.

Compilaciones.

Tests.

Problemas.

Riesgos.

Trabajo pendiente.

Próxima fase.

Si existe un bloqueo externo, documenta cómo resolverlo y continúa con las tareas independientes.

OBJETIVO FINAL: DESARROLLAR UNA APLICACIÓN ANDROID NATIVA DE CALIDAD COMERCIAL, PREPARADA PARA EVOLUCIONAR HASTA UNA PUBLICACIÓN REAL EN GOOGLE PLAY, BAJO LA IDENTIDAD DE SERGIOTECH.

3. Cómo organizaría el lanzamiento

Aunque el prompt contempla todo el producto, evitaría desarrollar Gmail, OCR, inventario, sincronización y estadísticas simultáneamente. El primer hito debe ser una aplicación útil, no un conjunto de pantallas sin conexión entre sí.

MVP — Primera versión funcional

Escanear tickets, registrar compras, identificar productos, gestionar garantías y devoluciones, iniciar sesión y recuperar datos desde otro móvil.

Versión 1.1 — Integraciones

Importación mediante otras aplicaciones, Gmail directo cuando sea viable, detección de duplicados y mejoras del reconocimiento automático.

Versión 1.2 — Inteligencia y automatización

Clasificación avanzada de productos, estadísticas, reconocimiento mejorado de comercios y automatización de nuevas compras.

Una decisión importante: el modelo de negocio

Yo plantearía una aplicación con modalidad gratuita y una posible modalidad Premium, pero no implementaría aún los pagos.

La parte gratuita debería ser lo suficientemente útil para generar adopción y confianza. Las funcionalidades avanzadas podrían incluir automatizaciones, mayor almacenamiento y otras capacidades que realmente ocasionen costes de infraestructura.

La compresión de documentos y el OCR local ayudarían especialmente a controlar el coste por usuario, que será uno de los factores decisivos si la aplicación consigue crecer.

Mi recomendación es utilizar este prompt como la especificación fundacional del repositorio. Obliga a la IA a construir una base real, deja la integración directa con Gmail correctamente condicionada y evita dos problemas habituales de los desarrollos asistidos por IA: una arquitectura excesiva sin funcionalidad y una interfaz vistosa que no tiene backend ni pruebas.

## Tabla tecnológica del documento

Componente | Tecnología
Plataforma | Android nativo, sin Flutter ni React Native
Lenguaje | Kotlin
Interfaz | Jetpack Compose + Material 3 / Material You
Android mínimo | Android 10 (API 29)
Android objetivo | API 36 como mínimo
Arquitectura | Clean Architecture + MVVM + UDF
Persistencia local | Room + DataStore
Backend | Supabase Self-Hosted (PostgreSQL, Auth, RLS), con adaptadores migrables
Sincronización | Offline-first: Room → sincronización incremental → Supabase; documentos en R2
Lectura de tickets | Google ML Kit OCR
Documentos | Compresión local adaptativa; almacenamiento privado Cloudflare R2 Standard; miniaturas y descarga bajo demanda
Inicio de sesión | Google y correo electrónico
Integraciones | Gmail, Android Share Sheet, documentos y archivos
Seguridad | RLS, OAuth 2.0, cifrado y gestión segura de sesiones
Notificaciones | Locales, programadas por vencimiento
