package com.ms_tallerpro.notify.listener;

import com.ms_tallerpro.notify.model.CanalNotificacion;
import com.ms_tallerpro.notify.model.ComandoNotificacion;
import com.ms_tallerpro.notify.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Consumidores de las colas de comando. jobs publica el body como JSON (String) y el
 * eventId en la cabecera "eventId" (OutboxPublisherService).
 *
 * Manejo de fallos (ADR-06): errores transitorios se reintentan segun
 * spring.rabbitmq.listener.simple.retry; un mensaje invalido (JSON corrupto, tipo
 * desconocido) se rechaza sin reencolar y RabbitMQ lo mueve a la DLQ correspondiente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComandoListener {

    private final NotificacionService service;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${tallerpro.notify.rabbitmq.queue-email}")
    public void email(@Payload String body, @Header(name = "eventId", required = false) String eventId) {
        atender(CanalNotificacion.EMAIL, body, eventId);
    }

    @RabbitListener(queues = "${tallerpro.notify.rabbitmq.queue-bay}")
    public void bahia(@Payload String body, @Header(name = "eventId", required = false) String eventId) {
        atender(CanalNotificacion.BAHIA, body, eventId);
    }

    @RabbitListener(queues = "${tallerpro.notify.rabbitmq.queue-quote}")
    public void presupuesto(@Payload String body, @Header(name = "eventId", required = false) String eventId) {
        atender(CanalNotificacion.PRESUPUESTO, body, eventId);
    }

    void atender(CanalNotificacion canal, String body, String eventId) {
        ComandoNotificacion comando = parsear(canal, body, eventId);
        try {
            service.procesar(comando);
        } catch (IllegalArgumentException ex) {
            log.error("Comando invalido en {} (eventId={}): {} -> DLQ", canal, comando.eventId(), ex.getMessage());
            throw new AmqpRejectAndDontRequeueException(ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private ComandoNotificacion parsear(CanalNotificacion canal, String body, String eventId) {
        Map<String, Object> datos;
        try {
            datos = objectMapper.readValue(body, Map.class);
        } catch (Exception ex) {
            log.error("Body no es JSON valido en {}: {} -> DLQ", canal, ex.getMessage());
            throw new AmqpRejectAndDontRequeueException("JSON invalido", ex);
        }
        String id = (eventId != null && !eventId.isBlank()) ? eventId
                : datos.get("eventId") != null ? datos.get("eventId").toString()
                : UUID.randomUUID().toString();
        String tipo = datos.get("tipo") == null ? null : datos.get("tipo").toString();
        return new ComandoNotificacion(id, canal, tipo, datos);
    }
}
