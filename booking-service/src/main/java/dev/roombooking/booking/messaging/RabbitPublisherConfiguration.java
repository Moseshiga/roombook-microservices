package dev.roombooking.booking.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RabbitPublisherConfiguration {

    @Bean
    TopicExchange roombookEventsExchange() {
        return new TopicExchange(BookingMessagingTopology.EVENTS_EXCHANGE, true, false);
    }

    @Bean
    JacksonJsonMessageConverter rabbitJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
