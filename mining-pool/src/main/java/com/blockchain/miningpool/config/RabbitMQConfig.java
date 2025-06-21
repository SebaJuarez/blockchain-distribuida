package com.blockchain.miningpool.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.AcknowledgeMode;

@Configuration
public class RabbitMQConfig {

    // Fanout del coordinador
    public static final String BLOCKCHAIN_EXCHANGE    = "blockchain";
    public static final String BLOCKS_CONTROL_QUEUE   = "blocks";

    // Cola interna de subtareas
    public static final String POOL_TASKS_EXCHANGE    = "pool.tasks";
    public static final String POOL_TASKS_QUEUE       = "pool_tasks";
    public static final String POOL_TASKS_ROUTING_KEY = "pool.task";

    // Fanout interno de control para cancelaciones
    public static final String POOL_CONTROL_EXCHANGE  = "pool.control";
    public static final String POOL_CONTROL_QUEUE     = "pool_control";

    @Bean
    public FanoutExchange blockchainExchange() {
        return new FanoutExchange(BLOCKCHAIN_EXCHANGE, true, false);
    }

    @Bean
    public Queue blocksControlQueue() {
        return new Queue(BLOCKS_CONTROL_QUEUE, true);
    }

    @Bean
    public Binding controlBinding(Queue blocksControlQueue, FanoutExchange blockchainExchange) {
        return BindingBuilder.bind(blocksControlQueue).to(blockchainExchange);
    }

    @Bean
    public DirectExchange poolTasksExchange() {
        return new DirectExchange(POOL_TASKS_EXCHANGE, true, false);
    }

    @Bean
    public Queue poolTasksQueue() {
        return new Queue(POOL_TASKS_QUEUE, true);
    }

    @Bean
    public Binding tasksBinding(Queue poolTasksQueue, DirectExchange poolTasksExchange) {
        return BindingBuilder
                .bind(poolTasksQueue)
                .to(poolTasksExchange)
                .with(POOL_TASKS_ROUTING_KEY);
    }

    @Bean
    public FanoutExchange poolControlExchange() {
        return new FanoutExchange(POOL_CONTROL_EXCHANGE, true, false);
    }

    @Bean
    public Queue poolControlQueue() {
        return new Queue(POOL_CONTROL_QUEUE, true);
    }

    @Bean
    public Binding poolControlBinding(Queue poolControlQueue, FanoutExchange poolControlExchange) {
        return BindingBuilder.bind(poolControlQueue).to(poolControlExchange);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        RabbitTemplate tpl = new RabbitTemplate(cf);
        tpl.setMessageConverter(jsonMessageConverter());
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