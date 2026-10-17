# Rendimiento y presupuesto provisional

La entrega inicial mide corrección y compilación. No se han ejecutado Macrobenchmark, Baseline Profiles ni ensayos con miles de compras; no se publican tiempos de producto no medidos.

Presupuestos iniciales para contrastar en un Android físico de gama media: arranque caliente p95 inferior a 500 ms; consulta de 1000 compras p95 inferior a 150 ms; importación de imagen típica p95 inferior a 5 s con OCR; máximo de memoria de procesamiento incremental por debajo de 96 MiB. Son objetivos, no resultados. Los tickets largos y PDFs necesitan corpus independiente.

Medir por formato: bytes originales/optimizados/miniatura, tiempo de copia/decodificación/OCR/hash, errores y cancelaciones, legibilidad, memoria pico y subidas/descargas. La prueba OCR de una imagen sintética demuestra ejecución del motor, no precisión en tickets reales.

Para nube: medir lecturas/escrituras y espacio D1, CPU/latencia del Worker, operaciones/bytes R2 y correo Resend por usuario activo. El piloto aplica límites de 100 MiB por usuario y 1 GiB lógico total, 2000 registros/usuario, 5000 peticiones de datos y 90 correos diarios por proyecto. El feed y la idempotencia requieren política de crecimiento antes de beta. Las cuotas propias no garantizan factura cero de una cuenta compartida.

No se ha activado un plan nuevo ni medido rendimiento de producción. Los costes dependen de los planes y consumos de la cuenta: [D1](https://developers.cloudflare.com/d1/platform/pricing/), [Workers](https://developers.cloudflare.com/workers/platform/pricing/), [R2](https://developers.cloudflare.com/r2/pricing/), [Resend](https://resend.com/pricing).
