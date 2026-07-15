package be.dnit.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaTopicConfig {

    private final String topic;
    private final String listenTopic;

    public KafkaTopicConfig(@Value("${app.kafka.topic}") String topic,
                            @Value("${app.kafka.listen-topic}") String listenTopic) {
        this.topic = topic;
        this.listenTopic = listenTopic;
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return new NewTopic(topic, 1, (short) 1);
    }

    @Bean
    public NewTopic inventoryEventsDltTopic() {
        return new NewTopic(listenTopic + ".DLT", 1, (short) 1);
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (cr, e) -> new org.apache.kafka.common.TopicPartition(cr.topic() + ".DLT", cr.partition()));
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(4000L);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
