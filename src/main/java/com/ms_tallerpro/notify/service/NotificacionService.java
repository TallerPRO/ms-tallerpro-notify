package com.ms_tallerpro.notify.service;

import com.ms_tallerpro.notify.model.CanalNotificacion;
import com.ms_tallerpro.notify.model.ComandoNotificacion;
import com.ms_tallerpro.notify.model.EstadoNotificacion;
import com.ms_tallerpro.notify.model.Notificacion;
import com.ms_tallerpro.notify.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Procesa los comandos de las 3 colas (caso, seccion 8):
 *  - q.cmd.email : email/push al cliente (recepcionada, diagnosticada, lista para retiro, entregada, anulada)
 *  - q.cmd.bay   : ticket de trabajo / asignacion de bahia al mecanico
 *  - q.cmd.quote : generacion de PDF (presupuesto u orden de trabajo)
 *
 * Idempotente por eventId: un redelivery de RabbitMQ no genera un segundo envio.
 * Un comando con datos invalidos lanza IllegalArgumentException -> el listener lo
 * rechaza sin reencolar y termina en la DLQ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository repository;
    private final EmailSender emailSender;

    public Notificacion procesar(ComandoNotificacion comando) {
        if (comando.eventId() == null || comando.eventId().isBlank()) {
            throw new IllegalArgumentException("Comando sin eventId");
        }
        if (comando.tipo() == null || comando.tipo().isBlank()) {
            throw new IllegalArgumentException("Comando sin tipo (eventId=" + comando.eventId() + ")");
        }

        Optional<Notificacion> previa = repository.porEventId(comando.eventId());
        if (previa.isPresent()) {
            log.info("Comando duplicado ignorado (eventId={}, tipo={})", comando.eventId(), comando.tipo());
            return repository.guardar(conEstado(previa.get(), EstadoNotificacion.DUPLICADA));
        }

        Notificacion resultado = switch (comando.canal()) {
            case EMAIL -> notificarCliente(comando);
            case BAHIA -> emitirTicketBahia(comando);
            case PRESUPUESTO -> generarDocumento(comando);
        };
        return repository.guardar(resultado);
    }

    public List<Notificacion> recientes(int limite) {
        return repository.recientes(limite);
    }

    // ---------- q.cmd.email ----------

    private Notificacion notificarCliente(ComandoNotificacion c) {
        String cliente = Optional.ofNullable(c.texto("cliente")).orElse("cliente");
        String contacto = c.texto("contacto");
        String asunto;
        String cuerpo;
        switch (c.tipo()) {
            case "ORDEN_RECEPCIONADA" -> {
                asunto = "TallerPro: recibimos tu vehiculo";
                cuerpo = "Hola %s, tu orden %s fue recepcionada. Te avisaremos cuando tengamos el diagnostico.".formatted(cliente, c.ordenId());
            }
            case "ORDEN_DIAGNOSTICADA" -> {
                asunto = "TallerPro: diagnostico listo";
                cuerpo = "Diagnostico de la orden %s: %s".formatted(c.ordenId(), Optional.ofNullable(c.texto("diagnostico")).orElse("(sin detalle)"));
            }
            case "VEHICULO_LISTO" -> {
                asunto = "TallerPro: tu vehiculo esta listo para retiro";
                cuerpo = "La orden %s esta LISTA PARA RETIRO. Puedes pasar a buscar tu vehiculo.".formatted(c.ordenId());
            }
            case "ORDEN_ENTREGADA" -> {
                asunto = "TallerPro: vehiculo entregado";
                cuerpo = "La orden %s fue entregada. Gracias por preferirnos.".formatted(c.ordenId());
            }
            case "ORDEN_ANULADA" -> {
                asunto = "TallerPro: orden anulada";
                cuerpo = "La orden %s fue anulada. Motivo: %s".formatted(c.ordenId(), Optional.ofNullable(c.texto("motivo")).orElse("no informado"));
            }
            default -> throw new IllegalArgumentException("Tipo de email desconocido: " + c.tipo());
        }

        if (contacto == null || contacto.isBlank()) {
            // jobs no informo contacto: push/log unicamente, no es un error de negocio
            log.warn("[PUSH simulado] orden={} tipo={} sin contacto de cliente; asunto=\"{}\"", c.ordenId(), c.tipo(), asunto);
            return nueva(c, null, asunto, EstadoNotificacion.ENVIADA, "PUSH (sin contacto)");
        }
        String detalle = emailSender.enviar(contacto, asunto, cuerpo);
        return nueva(c, contacto, asunto, EstadoNotificacion.ENVIADA, detalle);
    }

    // ---------- q.cmd.bay ----------

    private Notificacion emitirTicketBahia(ComandoNotificacion c) {
        if (!"TICKET_BAHIA".equals(c.tipo())) {
            throw new IllegalArgumentException("Tipo de ticket desconocido: " + c.tipo());
        }
        String mecanicoId = c.texto("mecanicoId");
        String mecanico = Optional.ofNullable(c.texto("mecanicoNombre")).orElse("mecanico");
        // El codigo ("A-01") es lo que el mecanico reconoce; si jobs no lo pudo
        // leer del catalogo, se cae al UUID antes que dejar el aviso sin bahia.
        String bahia = Optional.ofNullable(c.texto("bahiaCodigo")).orElse(c.texto("bahiaId"));
        String patente = Optional.ofNullable(c.texto("patente")).orElse("(sin patente)");
        String contacto = c.texto("contacto");

        String diagnostico = c.texto("diagnostico");
        String observaciones = c.texto("observaciones");

        String asunto = "Tienes un trabajo asignado en la bahia %s".formatted(bahia);
        StringBuilder cuerpo = new StringBuilder(
                "Hola %s, se te asigno la orden %s (vehiculo %s) en la bahia %s. El vehiculo ya esta en el puesto."
                        .formatted(mecanico, c.ordenId(), patente, bahia));
        // El mecanico necesita el detalle del trabajo en el mismo aviso.
        if (diagnostico != null && !diagnostico.isBlank()) {
            cuerpo.append("\n\nDiagnostico: ").append(diagnostico);
        }
        if (observaciones != null && !observaciones.isBlank()) {
            cuerpo.append("\nObservaciones: ").append(observaciones);
        }

        if (contacto == null || contacto.isBlank()) {
            // Sin correo del mecanico el ticket sigue siendo valido: queda en el
            // log/tablero del taller, igual que antes de tener catalogo de mecanicos.
            log.info("[TICKET BAHIA] mecanico={} bahia={} orden={} (sin correo)", mecanicoId, bahia, c.ordenId());
            return nueva(c, mecanicoId, asunto, EstadoNotificacion.ENVIADA, "Ticket impreso/push al mecanico");
        }

        String detalle = emailSender.enviar(contacto, asunto, cuerpo.toString());
        log.info("[TICKET BAHIA] aviso enviado a {} (bahia={}, orden={})", contacto, bahia, c.ordenId());
        return nueva(c, contacto, asunto, EstadoNotificacion.ENVIADA, detalle);
    }

    // ---------- q.cmd.quote ----------

    private Notificacion generarDocumento(ComandoNotificacion c) {
        String asunto = switch (c.tipo()) {
            case "PRESUPUESTO_INICIAL" -> "Presupuesto inicial orden %s (patente %s)".formatted(c.ordenId(), c.texto("patente"));
            case "PRESUPUESTO_ACTUALIZADO" -> "Presupuesto actualizado orden %s (%s repuestos)".formatted(c.ordenId(), c.texto("repuestos"));
            case "ORDEN_TRABAJO_FINAL" -> "Orden de trabajo final %s".formatted(c.ordenId());
            default -> throw new IllegalArgumentException("Tipo de documento desconocido: " + c.tipo());
        };
        log.info("[PDF generado] {}", asunto);
        return nueva(c, null, asunto, EstadoNotificacion.ENVIADA, "PDF generado (simulado)");
    }

    private static Notificacion nueva(ComandoNotificacion c, String destinatario, String asunto,
                                      EstadoNotificacion estado, String detalle) {
        return new Notificacion(c.eventId(), c.canal(), c.tipo(), c.ordenId(), destinatario, asunto, estado, detalle, Instant.now());
    }

    private static Notificacion conEstado(Notificacion n, EstadoNotificacion estado) {
        return new Notificacion(n.eventId(), n.canal(), n.tipo(), n.ordenId(), n.destinatario(), n.asunto(), estado, n.detalle(), Instant.now());
    }
}
