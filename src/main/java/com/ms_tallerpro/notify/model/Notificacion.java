package com.ms_tallerpro.notify.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de una notificacion procesada. ms-tallerpro-notify no tiene base de datos
 * (caso, seccion 5), asi que se conserva en memoria solo para idempotencia y evidencia.
 */
public record Notificacion(
        String eventId,
        CanalNotificacion canal,
        String tipo,
        UUID ordenId,
        String destinatario,
        String asunto,
        EstadoNotificacion estado,
        String detalle,
        Instant fecha
) {}
