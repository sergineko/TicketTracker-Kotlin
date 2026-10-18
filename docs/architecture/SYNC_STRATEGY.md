# Sincronización y compatibilidad

La outbox se escribe junto a las compras/documentos en Room. El sincronizador envía primero documentos y después compras, con API v1, UUID, operationId y expected_revision. El Worker deriva el propietario de la sesión, valida datos y referencias y confirma compare-and-swap, feed e idempotencia en un batch D1. Cada petición inicia una sesión D1 en el primario.

El ACK borra únicamente la operación confirmada. Una edición realizada durante la petición permanece en cola con la nueva revisión base. Una pérdida de conexión conserva operación y archivos; repetir la misma petición devuelve la misma aceptación. Cambiar el payload con la misma clave se rechaza.

La subida valida propietario, MIME, tamaño y SHA-256 de cada variante. R2 recibe una clave opaca privada y checksum. Cada nueva imagen solo reserva/sube OPTIMIZED; PDF solo ORIGINAL. Se conserva hash y tamaño de procedencia. Solo tras verificar la copia cloud se finaliza el documento como SYNCED_VERIFIED (ADR 0005). No se borran originales locales. Descargar usa el Worker autenticado, no URL pública; se valida el hash antes del reemplazo atómico.

El feed contiene hasta 25 cambios por página y tombstones. Su cursor se confirma en la misma transacción Room que aplica la página. Se rechaza un propietario ajeno o versión desconocida sin avanzar cursor. Las compras/documentos remotos se consultan siempre desde Room. Las copias optimizadas de imágenes y los PDF íntegros se recuperan bajo demanda; las miniaturas se regeneran localmente. No se promete recuperar el original de cámara desde R2.

Un 409 conserva la versión local y almacena la remota. En Perfil se puede mantener la compra local o aceptar la remota; un tombstone remoto no se resucita con la acción local. Los conflictos documentales requieren revisión y no eliminan archivos. La sesión caducada conserva el acceso offline y exige nuevo login para sincronizar.

WorkManager usa red conectada, backoff exponencial y como máximo cinco reintentos inmediatos; también revisa cada seis horas. El cierre de sesión se bloquea mientras quedan operaciones/conflictos. Se revoca la sesión remota antes de ocultar la cuenta; si falla la red se conserva para reintentar.

Las pruebas locales verifican API D1/R2 emulados y transporte Android sustituido por un adaptador de prueba. No acreditan recuperación en producción. Antes de declararla operativa se debe completar el ensayo real con dos instalaciones, una caída durante la subida, conflictos, aislamiento y hashes descargados.

El ZIP v1 incluye manifiesto y hashes, sin importador aún. El feed y las claves de idempotencia no se purgan: antes de beta se necesita política de compactación compatible con clientes antiguos y copia independiente de D1/R2 con restore ensayado.
