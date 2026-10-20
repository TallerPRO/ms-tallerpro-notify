package com.ms_tallerpro.notify.service;

import com.ms_tallerpro.notify.config.NotifyProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
        return enviarConAdjunto(destinatario, asunto, cuerpo, null, null);
    }

    @Override
    public String enviarConAdjunto(String destinatario, String asunto, String cuerpo, String nombreAdjunto, byte[] adjunto) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            // multipart=true solo hace falta cuando hay adjunto; para texto simple
            // alcanza con un mensaje normal, pero MimeMessageHelper soporta ambos casos igual.
            MimeMessageHelper helper = new MimeMessageHelper(mime, adjunto != null);
            helper.setFrom(props.email().remitente());
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(cuerpo);
            if (adjunto != null) {
                helper.addAttachment(nombreAdjunto, new ByteArrayResource(adjunto));
            }
            mailSender.send(mime);
        } catch (MailException | MessagingException ex) {
            throw new MailSendException("No se pudo enviar el correo a " + destinatario, ex);
        }
        log.info("Email enviado por SMTP a {}: {}{}", destinatario, asunto,
                adjunto == null ? "" : " (con adjunto " + nombreAdjunto + ")");
        return "SMTP -> " + destinatario;
    }
}
