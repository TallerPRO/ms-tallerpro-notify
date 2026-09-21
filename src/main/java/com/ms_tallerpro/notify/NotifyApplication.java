package com.ms_tallerpro.notify;

import com.ms_tallerpro.notify.config.NotifyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * ms-tallerpro-notify (MS-03): consumidor RabbitMQ. No expone API publica en el gateway;
 * procesa los comandos que ms-tallerpro-jobs deja en q.cmd.email, q.cmd.bay y q.cmd.quote.
 */
@SpringBootApplication
@EnableConfigurationProperties(NotifyProperties.class)
public class NotifyApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotifyApplication.class, args);
	}

}
