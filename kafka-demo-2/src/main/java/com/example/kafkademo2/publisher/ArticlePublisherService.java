package com.example.kafkademo2.publisher;

import com.example.sharedmodel.ArticlePublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class ArticlePublisherService {

    private static final Logger log = LoggerFactory.getLogger(ArticlePublisherService.class);

    private final KafkaTemplate<String, ArticlePublishedEvent> kafkaTemplate;
    private final String topic;

    public ArticlePublisherService(KafkaTemplate<String, ArticlePublishedEvent> kafkaTemplate,
                                   @Value("${app.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(ArticlePublishedEvent event) {
        CompletableFuture<SendResult<String, ArticlePublishedEvent>> future =
                kafkaTemplate.send(topic, event.id(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Published: topic={}, partition={}, offset={}, event={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        event);
            } else {
                log.error("Failed to publish event: {}", event, ex);
            }
        });
    }
}
