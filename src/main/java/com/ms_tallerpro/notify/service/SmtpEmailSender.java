package com.ms_tallerpro.notify.service;

import com.ms_tallerpro.notify.config.NotifyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Envio real por SMTP. Activo solo con tallerpro.notify.email.habilitado=true (y spring.mail.host). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tallerpro.notify.email", name = "habilitado", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final NotifyProperties props;

    @Override
    public String enviar(String destinatario, String asunto, String cuerpo) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(props.email().remitente());
        mensaje.setTo(destinatario);
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpo);
        mailSender.send(mensaje);
        log.info("Email enviado por SMTP a {}: {}", destinatario, asunto);
        return "SMTP -> " + destinatario;
    }
}
