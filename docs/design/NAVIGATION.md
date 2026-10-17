# Navegación

Inicio resume compras y vencimientos y permite importar, fotografiar o registrar manualmente. Mis compras muestra inventario, búsqueda y filtros. Documentos guarda los justificantes aunque no se vinculen a una compra. Perfil controla apariencia, recordatorios, exportación y muestra las integraciones pendientes.

Destinos secundarios: detalle de compra, editor nuevo/existente y cámara. El detalle abre los originales mediante FileProvider. La cámara permite revisar y repetir la fotografía antes de importarla. La salida del editor avisa de los cambios no guardados. Cancelar un formulario no elimina documentos ya importados.

La pila de navegación se conserva durante recreaciones de Activity. El ViewModel mantiene el borrador durante cambios de configuración; su recuperación tras muerte del proceso aún requiere SavedStateHandle o persistencia de borradores. No se presenta esa recuperación como implementada.
