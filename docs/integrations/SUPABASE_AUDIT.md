> Histórico: sustituido como destino del producto por D1 (ADR 0002). Solo acredita la auditoría en lectura del VPS; no autoriza cambios. El antiguo borrador Supabase se retiró.

# Auditoría inicial de Supabase compartido

Fecha: 17/10/2026. Consultas SQL con `default_transaction_read_only=on` y timeout 30 s mediante el acceso SSH ya preparado por el propietario. También se consultaron imágenes/estado de contenedores, memoria, disco y cuatro variables no secretas de Storage. No se modificó configuración, esquema, usuarios ni datos de producción.

## Evidencia observada

| Componente | Valor observado |
| --- | --- |
| PostgreSQL | 15.8; TLS activo; mínimo TLS 1.2; max_connections 100 |
| Base principal | Aproximadamente 11 MB en esta consulta |
| Esquemas de negocio | `savetempo` existente; `tickettracker` aún no existe; sin tablas de negocio en public/savetempo |
| Auth | GoTrue v2.186.0; sin usuarios registrados en el momento de lectura |
| PostgREST | v14.6 |
| Storage | v1.44.2; backend S3 apuntando al MinIO interno; bucket de backend `stub` |
| Kong | 3.9.1; puerto publicado únicamente en localhost del VPS |
| Otros componentes | Studio 2026.03.16, Realtime 2.76.5, Edge Runtime 1.71.2, Supavisor 2.7.4 |
| Servicios | 14 contenedores del proyecto activos; la mayoría declaran healthcheck healthy |
| Memoria puntual | 3828 MiB total, 1588 MiB disponible; swap en uso 2318 MiB |
| Disco puntual | 116 GB, 79 GB disponibles en el volumen consultado |

La carpeta de infraestructura documenta backups diarios cifrados con retención local de 14 días y una copia descargada a Windows con integridad verificada. Esto es evidencia documental previa, no un restore ensayado en esta tarea. La copia externa automática diaria sigue pendiente según ese registro.


La integración vigente usa D1/Worker y un bucket R2 propio. No se ejecutarán las propuestas anteriores de esquema, RLS o Storage en esta instalación.
