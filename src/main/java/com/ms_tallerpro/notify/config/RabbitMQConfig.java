package com.ms_tallerpro.notify.config;

import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Misma topologia que declara ms-tallerpro-jobs (T-02.10 / caso seccion 8): 3 colas de
 * comando sobre cmd.direct, cada una con su DLQ via cmd.dead.dlx. Declararla aqui
 * tambien permite levantar notify antes que jobs (las declaraciones son idempotentes).
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public Declarables topologiaComandos(NotifyProperties props) {
        NotifyProperties.Rabbit r = props.rabbitmq();
        DirectExchange cmd = new DirectExchange(r.exchangeCmd(), true, false);
        DirectExchange dlx = new DirectExchange(r.exchangeDlx(), true, false);

        List<Declarable> declarables = new ArrayList<>(List.of(cmd, dlx));
        for (String cola : List.of(r.queueEmail(), r.queueBay(), r.queueQuote())) {
            Queue principal = QueueBuilder.durable(cola)
                    .withArgument("x-dead-letter-exchange", r.exchangeDlx())
                    .withArgument("x-dead-letter-routing-key", cola)
                    .build();
            Queue dlq = QueueBuilder.durable(cola + ".dlq").build();
            declarables.add(principal);
            declarables.add(dlq);
            declarables.add(BindingBuilder.bind(principal).to(cmd).with(cola));
            declarables.add(BindingBuilder.bind(dlq).to(dlx).with(cola));
        }
        return new Declarables(declarables);
    }
}
