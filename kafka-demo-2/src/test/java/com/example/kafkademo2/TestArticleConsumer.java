package com.example.kafkademo2;

import com.example.sharedmodel.ArticlePublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Component
class TestArticleConsumer {

    private static final Logger log = LoggerFactory.getLogger(TestArticleConsumer.class);

    private final BlockingQueue<ArticlePublishedEvent> received = new LinkedBlockingQueue<>();

    @KafkaListener(topics = "test-integration-topic-2", groupId = "test-group-2")
    void onEvent(ArticlePublishedEvent event) {
        log.info("[TEST] Received: id={}, title={}", event.id(), event.title());
        received.add(event);
    }

    ArticlePublishedEvent poll(Duration timeout) throws InterruptedException {
        return received.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
}
