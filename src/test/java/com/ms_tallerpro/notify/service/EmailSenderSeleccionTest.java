package com.ms_tallerpro.notify.service;

import com.ms_tallerpro.notify.config.NotifyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Siempre debe existir exactamente un EmailSender: SMTP con habilitado=true y el de log
 * en cualquier otro caso (incluida la propiedad ausente).
 */
class EmailSenderSeleccionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(JavaMailSender.class, JavaMailSenderImpl::new)
            .withBean(NotifyProperties.class, () -> new NotifyProperties(null,
                    new NotifyProperties.Email("no-reply@example.com", true)))
            .withUserConfiguration(LoggingEmailSender.class, SmtpEmailSender.class);

    @Test
    void smtpHabilitadoUsaSoloSmtp() {
        runner.withPropertyValues("tallerpro.notify.email.habilitado=true").run(ctx -> {
            assertThat(ctx).hasSingleBean(EmailSender.class);
            assertThat(ctx).hasSingleBean(SmtpEmailSender.class);
        });
    }

    @Test
    void smtpDeshabilitadoUsaSoloLog() {
        runner.withPropertyValues("tallerpro.notify.email.habilitado=false").run(ctx -> {
            assertThat(ctx).hasSingleBean(EmailSender.class);
            assertThat(ctx).hasSingleBean(LoggingEmailSender.class);
        });
    }

    @Test
    void sinPropiedadUsaLogPorDefecto() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(EmailSender.class);
            assertThat(ctx).hasSingleBean(LoggingEmailSender.class);
        });
    }
}
