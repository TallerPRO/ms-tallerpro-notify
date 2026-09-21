package com.ms_tallerpro.notify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Prefijo tallerpro.notify en application.yml. Debe coincidir con tallerpro.jobs.rabbitmq de jobs. */
@ConfigurationProperties(prefix = "tallerpro.notify")
public record NotifyProperties(Rabbit rabbitmq, Email email) {

    public record Rabbit(String exchangeCmd, String exchangeDlx, String queueEmail, String queueBay, String queueQuote) {}

    /** remitente y si el envio real esta habilitado (requiere spring.mail.host). */
    public record Email(String remitente, boolean habilitado) {}
}
