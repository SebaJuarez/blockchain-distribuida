package com.blockchain.miningpool.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration; // Agrega esta importación

@Configuration // Asegúrate de que esta clase sea una clase de configuración
public class RabbitMQConfig {

    // Constantes idénticas a las del Coordinator para asegurar que se refieren a la misma cola/exchange
    public static final String BLOCKCHAIN_EXCHANGE = "blockchain";
    public static final String BLOCKS_QUEUE = "blocks";
    public static final String BLOCKS_ROUTING_KEY = "bl";

    @Bean
    public DirectExchange blockchainExchange() {
        return new DirectExchange(BLOCKCHAIN_EXCHANGE, true, false);
    }

    @Bean
    public Queue blocksQueue() {
        return new Queue(BLOCKS_QUEUE, true); // durable = true
    }

    @Bean
    public Binding blocksBinding(Queue blocksQueue, DirectExchange blockchainExchange) {
        return BindingBuilder.bind(blocksQueue).to(blockchainExchange).with(BLOCKS_ROUTING_KEY);
    }


    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        // Configura el RabbitTemplate para enviar mensajes (si este servicio necesita enviar)
        RabbitTemplate tpl = new RabbitTemplate(cf);
        tpl.setMessageConverter(jsonMessageConverter()); // Usa el mismo convertidor
        return tpl;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf,
            SimpleRabbitListenerContainerFactoryConfigurer configurer
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, cf);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }
}