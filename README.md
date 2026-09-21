# ms-tallerpro-notify

MS-03 de **TallerPro**: consumidor RabbitMQ que procesa las notificaciones que genera `ms-tallerpro-jobs`.
Spring Boot 4.1 · Java 25 · Spring AMQP · **sin base de datos** (caso, sección 5) · **no público** (no se rutea en el gateway).

```
ms-tallerpro-jobs --outbox--> cmd.direct --q.cmd.email--> notify -> email/push al cliente
                                         --q.cmd.bay---> notify -> ticket de trabajo al mecánico
                                         --q.cmd.quote-> notify -> PDF presupuesto / orden de trabajo
                                    (fallo tras 3 intentos) --> cmd.dead.dlx --> q.cmd.*.dlq
```

Capas: `model` (comando, notificación) → `repository` (memoria: idempotencia + últimas 1000) → `service`
(procesamiento + `EmailSender`) · `listener` (consumidores) · `controller` (endpoint interno) · `config`.

## Colas y comandos (topología del caso, sección 8)

| Cola | Tipos (`tipo` en el body) | Acción |
|---|---|---|
| `q.cmd.email` | `ORDEN_RECEPCIONADA`, `ORDEN_DIAGNOSTICADA`, `VEHICULO_LISTO`, `ORDEN_ENTREGADA`, `ORDEN_ANULADA` | Email al `contacto` del cliente (si no viene, push simulado) |
| `q.cmd.bay` | `TICKET_BAHIA` | Ticket de trabajo al `mecanicoId` en `bahiaId` |
| `q.cmd.quote` | `PRESUPUESTO_INICIAL`, `PRESUPUESTO_ACTUALIZADO`, `ORDEN_TRABAJO_FINAL` | Generación de PDF (simulada) |

Body: JSON plano publicado por jobs, p. ej. `{"tipo":"VEHICULO_LISTO","ordenId":"…","cliente":"Ana","contacto":"ana@correo.cl"}`.
`eventId` viaja en la cabecera AMQP `eventId`; el mismo `eventId` nunca se procesa dos veces (redelivery).

**Fallos:** errores transitorios → 3 reintentos con backoff (1 s, 2 s, 4 s). JSON inválido o tipo desconocido →
rechazo sin reencolar → DLQ `q.cmd.<x>.dlq`. Notify declara la misma topología que jobs (idempotente), así puede
levantarse en cualquier orden.

## Email

Por defecto el correo se **registra en el log** (`LoggingEmailSender`). Para envío real por SMTP:
`SMTP_ENABLED=true SMTP_HOST=smtp.office365.com SMTP_USER=… SMTP_PASSWORD=… SMTP_FROM=no-reply@tallerpro.cl`.

## Endpoint interno (observabilidad, solo Admin)

`GET /api/v1/notificaciones?limite=50` → últimas notificaciones procesadas (en memoria). Útil como evidencia en la
defensa: muestra que los comandos llegaron y se atendieron. No se expone en el gateway.

## Variables

| Variable | Default |
|---|---|
| `SERVER_PORT` | `8083` |
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | `localhost`, `5672`, `guest`, `guest` |
| `SMTP_ENABLED`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM` | `false`, —, `587`, —, —, `no-reply@tallerpro.cl` |
| `TALLERPRO_JWT_ENABLED`, `TALLERPRO_DEV_ROLES` | `true`, `Admin` |
| `AZURE_TENANT_ID`, `AZURE_API_CLIENT_ID` | — |

## Ejecución y pruebas

```bash
docker run -d --name tp-rabbit -p 5672:5672 -p 15672:15672 rabbitmq:3-management   # UI en :15672 (guest/guest)
TALLERPRO_JWT_ENABLED=false ./mvnw spring-boot:run
./mvnw test   # NotificacionServiceTest (idempotencia, tipos, DLQ) + ComandoListenerTest (parseo + endpoint)
```

Prueba manual desde la UI de RabbitMQ: publicar en el exchange `cmd.direct` con routing key `q.cmd.email`, header
`eventId=demo-1` y payload `{"tipo":"VEHICULO_LISTO","ordenId":"<uuid>","cliente":"Ana","contacto":"ana@correo.cl"}`;
en el log aparece `[EMAIL simulado] …` y `GET /api/v1/notificaciones` lo lista.
