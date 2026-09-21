package com.ms_tallerpro.notify.model;

import java.util.Map;
import java.util.UUID;

/**
 * Comando recibido desde ms-tallerpro-jobs por RabbitMQ. El body es un JSON plano
 * generado por jobs (Map), por ejemplo:
 *   {"tipo":"ORDEN_RECEPCIONADA","ordenId":"...","cliente":"Ana Perez","contacto":"ana@correo.cl"}
 *   {"tipo":"TICKET_BAHIA","ordenId":"...","mecanicoId":"...","bahiaId":"..."}
 *   {"tipo":"PRESUPUESTO_INICIAL","ordenId":"...","patente":"ABCD12"}
 */
public record ComandoNotificacion(String eventId, CanalNotificacion canal, String tipo, Map<String, Object> datos) {

    public String texto(String clave) {
        Object v = datos.get(clave);
        return v == null ? null : v.toString();
    }

    public UUID ordenId() {
        String v = texto("ordenId");
        return v == null ? null : UUID.fromString(v);
    }
}
