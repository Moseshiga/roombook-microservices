package dev.roombooking.notification.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RabbitConsumerConfiguration {

    @Bean
    TopicExchange roombookEventsExchange() {
        return new TopicExchange(NotificationMessagingTopology.EVENTS_EXCHANGE, true, false);
    }

    @Bean
    Queue bookingConfirmedQueue() {
        return QueueBuilder.durable(NotificationMessagingTopology.BOOKING_CONFIRMED_QUEUE)
                .deadLetterExchange(NotificationMessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(NotificationMessagingTopology.BOOKING_CONFIRMED_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding bookingConfirmedBinding(
            @Qualifier("bookingConfirmedQueue") Queue bookingConfirmedQueue,
            TopicExchange roombookEventsExchange) {
        return BindingBuilder.bind(bookingConfirmedQueue)
                .to(roombookEventsExchange)
                .with(NotificationMessagingTopology.BOOKING_CONFIRMED_ROUTING_KEY);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(NotificationMessagingTopology.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue bookingConfirmedDeadLetterQueue() {
        return QueueBuilder.durable(NotificationMessagingTopology.BOOKING_CONFIRMED_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding bookingConfirmedDeadLetterBinding(
            @Qualifier("bookingConfirmedDeadLetterQueue") Queue bookingConfirmedDeadLetterQueue,
            DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(bookingConfirmedDeadLetterQueue)
                .to(deadLetterExchange)
                .with(NotificationMessagingTopology.BOOKING_CONFIRMED_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    JacksonJsonMessageConverter rabbitJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
