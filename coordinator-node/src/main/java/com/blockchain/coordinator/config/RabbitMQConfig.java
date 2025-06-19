package com.blockchain.coordinator.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String BLOCKCHAIN_EXCHANGE = "blockchain";
    public static final String TRANSACTIONS_QUEUE = "transactions";
    public static final String TRANSACTIONS_ROUTING_KEY = "tx";

    public static final String BLOCKS_QUEUE = "blocks";
    public static final String BLOCKS_ROUTING_KEY = "bl";

    @Bean
    public DirectExchange blockchainExchange() {
        return new DirectExchange(BLOCKCHAIN_EXCHANGE, true, false);
    }

    @Bean
    public Queue transactionsQueue() {
        return new Queue(TRANSACTIONS_QUEUE, true); // durable = true
    }

    @Bean
    public Binding transactionsBinding(Queue transactionsQueue, DirectExchange blockchainExchange) {
        return BindingBuilder.bind(transactionsQueue).to(blockchainExchange).with(TRANSACTIONS_ROUTING_KEY);
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
    public AmqpTemplate rabbitModificatedTemplate(ConnectionFactory connectionFactory) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}