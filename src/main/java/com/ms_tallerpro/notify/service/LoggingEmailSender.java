package com.ms_tallerpro.notify.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Implementacion por defecto (desarrollo, evaluaciones sin SMTP): el correo se
 * registra en el log con todos sus datos en lugar de enviarse.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(SmtpEmailSender.class)
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
