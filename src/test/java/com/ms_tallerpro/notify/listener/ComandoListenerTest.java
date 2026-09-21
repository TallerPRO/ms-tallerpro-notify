package com.ms_tallerpro.notify.listener;

import com.ms_tallerpro.notify.model.CanalNotificacion;
import com.ms_tallerpro.notify.service.NotificacionService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contexto completo sin broker (listeners con auto-startup=false): se prueba el parseo
 * del body tal como lo publica ms-tallerpro-jobs, el rechazo a DLQ y el endpoint interno.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ComandoListenerTest {

    @Autowired
    private ComandoListener listener;

    @Autowired
    private NotificacionService service;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void procesaElBodyJsonQuePublicaJobs() throws Exception {
        String orden = UUID.randomUUID().toString();
        String body = "{\"tipo\":\"TICKET_BAHIA\",\"ordenId\":\"" + orden + "\",\"mecanicoId\":\"mec-9\",\"bahiaId\":\"bah-9\"}";

        listener.bahia(body, "ev-listener-1");

        assertEquals("ev-listener-1", service.recientes(1).get(0).eventId());
        assertEquals(CanalNotificacion.BAHIA, service.recientes(1).get(0).canal());

        // Endpoint interno: solo Admin
        mockMvc.perform(get("/api/v1/notificaciones")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notificaciones").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Auditor"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/notificaciones").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("TICKET_BAHIA"))
                .andExpect(jsonPath("$[0].ordenId").value(orden));
    }

    @Test
    void jsonInvalidoOTipoDesconocidoVanADlq() {
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.email("esto no es json", "ev-x"));
        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> listener.presupuesto("{\"tipo\":\"RARO\",\"ordenId\":\"" + UUID.randomUUID() + "\"}", "ev-y"));
    }
}
