package com.ms_tallerpro.notify.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementacion por defecto (desarrollo, evaluaciones sin SMTP): el correo se
 * registra en el log con todos sus datos en lugar de enviarse.
 *
 * Condicion complementaria a la de SmtpEmailSender (misma propiedad, valor opuesto):
 * siempre existe exactamente un EmailSender, decidido solo por configuracion. Spring Boot
 * recomienda @ConditionalOnMissingBean solo en auto-configuraciones, no en componentes.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "tallerpro.notify.email", name = "habilitado", havingValue = "false", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    @Override
    public String enviar(String destinatario, String asunto, String cuerpo) {
        log.info("[EMAIL simulado] para={} asunto=\"{}\" cuerpo=\"{}\"", destinatario, asunto, cuerpo);
        return "LOG -> " + destinatario;
    }

    @Override
    public String enviarConAdjunto(String destinatario, String asunto, String cuerpo, String nombreAdjunto, byte[] adjunto) {
        log.info("[EMAIL simulado] para={} asunto=\"{}\" cuerpo=\"{}\" adjunto={} ({} bytes)",
                destinatario, asunto, cuerpo, nombreAdjunto, adjunto == null ? 0 : adjunto.length);
        return "LOG -> " + destinatario;
    }
}
