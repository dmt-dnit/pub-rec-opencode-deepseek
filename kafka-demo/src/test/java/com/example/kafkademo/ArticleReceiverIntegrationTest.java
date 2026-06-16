package com.example.kafkademo;

import com.example.sharedmodel.ArticlePublishedEvent;
import com.example.kafkademo.publisher.ArticlePublisherService;
import com.example.kafkademo.receiver.ArticleReceiver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = "test-integration-topic",
        brokerProperties = {"listeners=PLAINTEXT://localhost:9093", "port=9093"}
)
@ActiveProfiles("test")
class ArticleReceiverIntegrationTest {

    static {
        System.setProperty("app.kafka.topic", "test-integration-topic");
        System.setProperty("app.kafka.listen-topic", "test-integration-topic");
    }

    @Autowired
    private ArticlePublisherService publisherService;

    @Autowired
    private TestArticleConsumer testConsumer;

    @Test
    void shouldPublishAndReceiveArticleEvent() throws Exception {
        ArticlePublishedEvent event = new ArticlePublishedEvent(
                "id-x", "Integration Test", "Bob", Instant.parse("2026-06-15T12:00:00Z")
        );

        publisherService.publish(event);

        ArticlePublishedEvent received = testConsumer.poll(Duration.ofSeconds(10));
        assertThat(received).isNotNull();
        assertThat(received.id()).isEqualTo("id-x");
        assertThat(received.title()).isEqualTo("Integration Test");
    }
}
