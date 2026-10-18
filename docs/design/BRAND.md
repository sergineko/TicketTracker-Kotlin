# Identidad visual

Referencia entregada por el propietario el 17/10/2026 en `img/`:

- `Imagen de ChatGPT 17 oct 2026, 18_59_16.png`: icono completo con fondo blanco, conservado como recurso original.
- `cb050e17-a9c8-44fc-a945-cdef60c13919.png`: símbolo transparente, usado en el icono adaptativo y la cabecera.
- `TicketTracker_ productos siempre protegidos.png`: promo de referencia; se conserva como concepto, no se publica como anuncio de funciones disponibles.

Los dos iconos originales se copian sin alteraciones a `drawable-nodpi/brand_icon.png` y `brand_mark.png`. El launcher combina símbolo, margen seguro y fondo turquesa; Android 13+ dispone de una silueta vectorial para iconos temáticos. El original del propietario se conserva en `img/`.

El inicio reproduce la composición del teléfono de la referencia: cabecera compacta con marca, búsqueda y campana; tarjeta principal turquesa con título en cápsula blanca, total y valor por moneda; tarjetas violeta Suscripciones y naranja Seguros con contadores en superficies translúcidas; tres pestañas redondeadas y filas blancas compactas con icono, fecha y estado. El botón «+» central elevado abre registro manual, cámara, importación y contratos. El valor suma totales de compra separados por moneda, sin sumar monedas distintas.

Por petición posterior del propietario, los tres bloques principales son Garantía activa, Crítica y Devoluciones posibles. Son filtros funcionales sobre fechas manuales de Room y pueden solaparse; crítica significa vencimiento dentro de 30 días. No se muestra En servicio. Las devoluciones requieren una fecha introducida por el usuario, sin inferir derecho de desistimiento. Suscripciones y seguros permiten alta, edición y borrado lógico local (ADR 0003); sus pantallas indican que todavía no se sincronizan ni generan avisos. Cero registros produce contadores cero. No se rellenan listas con productos de la promo.

Las miniaturas de productos son iconos neutros, porque todavía no se adjuntan fotografías a productos. El naranja usa texto oscuro para mejorar el contraste. No se incorporan los reclamos de publicación en Play o recuperación universal de la imagen de referencia.

La paleta de marca es predeterminada en claro y oscuro. El color dinámico de Android sigue disponible mediante la opción existente de Perfil; una preferencia guardada se respeta. Textos de interfaz en recursos español/inglés; las etiquetas se adaptan al tamaño de fuente. La cabecera permite buscar compras; las tarjetas y la navegación ejecutan flujos reales.

La validación vigente y sus límites están en `docs/DELIVERY_STATUS.md`. Las capturas pobladas proceden de fixtures marcados «prueba», creados y retirados por una prueba instrumentada; no son datos del propietario ni contenido de demostración de la APK. Capturas e informes en `.local/`, sin credenciales.

En 0.1.2 la cabecera y el acceso a la cuenta usan el símbolo transparente sobre un fondo turquesa nativo que se adapta al tema; el fondo de arranque también tiene variante nocturna. La cuenta conectada tiene una tarjeta de identidad y estado real de sync; las opciones de acceso son secundarias. El botón Google usa el recurso oficial sin alterar, procedente de https://developers.google.com/static/identity/images/g-logo.png, siguiendo https://developers.google.com/identity/branding-guidelines. El recurso solo identifica ese acceso. El cierre aplica ClearCredentialStateRequest según la documentación oficial de Credential Manager.
