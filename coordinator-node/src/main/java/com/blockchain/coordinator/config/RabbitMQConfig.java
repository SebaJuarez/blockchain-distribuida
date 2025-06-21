package com.blockchain.coordinator.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String BLOCKCHAIN_EXCHANGE = "blockchain";
    public static final String BLOCKS_QUEUE = "blocks";

    @Bean
    public FanoutExchange blockchainExchange() {
        return new FanoutExchange(BLOCKCHAIN_EXCHANGE, true, false);
    }

    @Bean
    public Queue blocksQueue() {
        return new Queue(BLOCKS_QUEUE, true);
    }

    @Bean
    public Binding blocksBinding(Queue blocksQueue, FanoutExchange blockchainExchange) {
        return BindingBuilder.bind(blocksQueue).to(blockchainExchange);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitModificatedTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }

    @Bean
    public AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
}