# ADR 0001 Base local y módulos iniciales

Decisión aceptada el 17/10/2026: comenzar con una app útil offline, cinco módulos y contratos de proveedor independientes. El repositorio estaba vacío y existe Supabase compartido cuya exposición móvil y R2 no están preparados.

Room alimenta las pantallas. Las compras manuales y los documentos se implementan de extremo a extremo local antes de Auth. La outbox forma parte de las transacciones para preparar la sincronización. El modo local se identifica visiblemente y no promete recuperación tras desinstalar.

AGP 9.1.0, Gradle 9.3.1 y Kotlin integrado 2.2.10 son una combinación estable documentada y disponible en el entorno. KSP 2.3.6 y Hilt 2.59.2 soportan esta generación de AGP. Compose y las dependencias están fijados en catálogo; no se usan versiones dinámicas. Se revisarán actualizaciones por grupos después de establecer esta base comprobada.

El esquema remoto será exclusivamente `tickettracker`. Cambiar el almacenamiento global de Supabase a R2 afectaría a otros proyectos, por lo que requiere una decisión y un ensayo propios. La versión inicial solo conserva originales; no se habilita limpieza que pueda destruir el último documento válido.
