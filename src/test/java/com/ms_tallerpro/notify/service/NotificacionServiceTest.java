package com.ms_tallerpro.notify.service;

import com.ms_tallerpro.notify.model.CanalNotificacion;
import com.ms_tallerpro.notify.model.ComandoNotificacion;
import com.ms_tallerpro.notify.model.EstadoNotificacion;
import com.ms_tallerpro.notify.model.Notificacion;
import com.ms_tallerpro.notify.repository.NotificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Logica de procesamiento de comandos sin broker: idempotencia, tipos y errores que van a DLQ. */
class NotificacionServiceTest {

    private final List<String> correosEnviados = new ArrayList<>();
    private NotificacionService service;

    @BeforeEach
    void setUp() {
        EmailSender senderFalso = (para, asunto, cuerpo) -> {
            correosEnviados.add(para + "|" + asunto);
            return "TEST -> " + para;
        };
        service = new NotificacionService(new NotificacionRepository(), senderFalso);
    }

    @Test
    void emailAlClienteConContactoSeEnvia() {
        UUID orden = UUID.randomUUID();
        Notificacion n = service.procesar(comando("ev-1", CanalNotificacion.EMAIL,
                Map.of("tipo", "VEHICULO_LISTO", "ordenId", orden.toString(), "cliente", "Ana", "contacto", "ana@correo.cl")));

        assertEquals(EstadoNotificacion.ENVIADA, n.estado());
        assertEquals("ana@correo.cl", n.destinatario());
        assertEquals(orden, n.ordenId());
        assertEquals(1, correosEnviados.size());
    }

    @Test
    void emailSinContactoNoFallaYQuedaComoPush() {
        Notificacion n = service.procesar(comando("ev-2", CanalNotificacion.EMAIL,
                Map.of("tipo", "ORDEN_RECEPCIONADA", "ordenId", UUID.randomUUID().toString(), "cliente", "Ana")));

        assertEquals(EstadoNotificacion.ENVIADA, n.estado());
        assertNull(n.destinatario());
        assertEquals(0, correosEnviados.size());
    }

    @Test
    void mismoEventIdNoSeEnviaDosVeces() {
        Map<String, Object> datos = Map.of("tipo", "ORDEN_ENTREGADA", "ordenId", UUID.randomUUID().toString(), "contacto", "a@b.cl");
        service.procesar(comando("ev-3", CanalNotificacion.EMAIL, datos));
        Notificacion segunda = service.procesar(comando("ev-3", CanalNotificacion.EMAIL, datos));

        assertEquals(EstadoNotificacion.DUPLICADA, segunda.estado());
        assertEquals(1, correosEnviados.size());
    }

    @Test
    void ticketDeBahiaYDocumentosSeProcesan() {
        String orden = UUID.randomUUID().toString();
        Notificacion ticket = service.procesar(comando("ev-4", CanalNotificacion.BAHIA,
                Map.of("tipo", "TICKET_BAHIA", "ordenId", orden, "mecanicoId", "mec-1", "bahiaId", "bah-1")));
        Notificacion pdf = service.procesar(comando("ev-5", CanalNotificacion.PRESUPUESTO,
                Map.of("tipo", "PRESUPUESTO_INICIAL", "ordenId", orden, "patente", "ABCD12")));

        assertEquals("mec-1", ticket.destinatario());
        assertEquals(EstadoNotificacion.ENVIADA, pdf.estado());
        assertEquals(2, service.recientes(10).size());
        assertEquals("ev-5", service.recientes(10).get(0).eventId()); // mas reciente primero
    }

    @Test
    void tipoDesconocidoOSinTipoEsInvalido() {
        assertThrows(IllegalArgumentException.class, () -> service.procesar(comando("ev-6", CanalNotificacion.EMAIL,
                Map.of("tipo", "LO_QUE_SEA", "ordenId", UUID.randomUUID().toString()))));
        assertThrows(IllegalArgumentException.class, () -> service.procesar(comando("ev-7", CanalNotificacion.BAHIA,
                Map.of("ordenId", UUID.randomUUID().toString()))));
    }

    private static ComandoNotificacion comando(String eventId, CanalNotificacion canal, Map<String, Object> datos) {
        return new ComandoNotificacion(eventId, canal, (String) datos.get("tipo"), datos);
    }
}
