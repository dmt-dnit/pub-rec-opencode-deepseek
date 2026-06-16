package com.example.kafkademo2.receiver;

import com.example.sharedmodel.ArticlePublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class ArticleReceiver {

    private static final Logger log = LoggerFactory.getLogger(ArticleReceiver.class);

    private final String topic;
    private final SimpMessagingTemplate messagingTemplate;

    public ArticleReceiver(@Value("${app.kafka.listen-topic}") String topic,
                           SimpMessagingTemplate messagingTemplate) {
        this.topic = topic;
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(topics = "${app.kafka.listen-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onArticlePublished(ArticlePublishedEvent event) {
        log.info("Received: topic={}, id={}, title={}, author={}, publishedAt={}",
                topic,
                event.id(),
                event.title(),
                event.author(),
                event.publishedAt());
        messagingTemplate.convertAndSend("/topic/messages", event);
    }
}
