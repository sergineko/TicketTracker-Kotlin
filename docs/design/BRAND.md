# Identidad visual

Referencia entregada por el propietario el 17/10/2026 en `img/`:

- `Imagen de ChatGPT 17 oct 2026, 18_59_16.png`: icono completo con fondo blanco, usado en la cabecera.
- `cb050e17-a9c8-44fc-a945-cdef60c13919.png`: símbolo transparente, usado en el icono adaptativo y como detalle del resumen.
- `TicketTracker_ productos siempre protegidos.png`: promo de referencia; se conserva como concepto, no se publica como anuncio de funciones disponibles.

Los dos iconos originales se copian sin alteraciones a `drawable-nodpi/brand_icon.png` y `brand_mark.png`. El launcher combina símbolo, margen seguro y fondo turquesa; Android 13+ dispone de una silueta vectorial para iconos temáticos. El original del propietario se conserva en `img/`.

La interfaz nativa adopta turquesa, texto azul oscuro, superficies claras, tarjetas redondeadas, acentos violeta/ámbar y botón central de añadir. El inicio muestra productos, compras, garantías con fecha manual vigente, próximos plazos, devoluciones y documentos de Room. Cero registros produce contadores cero y un estado vacío. No se rellenan listas con productos de la promo.

Suscripciones, seguros y servicio técnico del concepto no tienen flujos de producto implementados; no se muestran accesos ficticios. Las fechas no se infieren jurídicamente. Tampoco se incorporan los reclamos de publicación en Play o recuperación universal que aparecen en la imagen de referencia.

La paleta de marca es predeterminada en claro y oscuro. El color dinámico de Android sigue disponible mediante la opción existente de Perfil; una preferencia guardada se respeta. Textos de interfaz en recursos español/inglés; las etiquetas se adaptan al tamaño de fuente. La cabecera permite buscar compras; las tarjetas y la navegación ejecutan flujos reales.

Verificación local en API 36: claro/oscuro, tema oscuro de la app sobre sistema claro, español/inglés y fuente al 130 %. Se ajustaron etiquetas de navegación, plurales y contraste del resumen. Debug y release optimizada compilan; Lint sin errores. Las 16 pruebas instrumentadas pasaron durante el cambio de marca y se repitió el flujo Compose tras los ajustes. Capturas e informes en `.local/`, sin credenciales ni datos privados del propietario.
