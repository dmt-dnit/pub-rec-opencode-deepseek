package com.example.kafkademo2;

import com.example.sharedmodel.ArticlePublishedEvent;
import com.example.kafkademo2.publisher.ArticlePublisherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = "test-integration-topic-2",
        brokerProperties = {"listeners=PLAINTEXT://localhost:9094", "port=9094"}
)
@ActiveProfiles("test")
class ArticleReceiverIntegrationTest {

    static {
        System.setProperty("app.kafka.topic", "test-integration-topic-2");
        System.setProperty("app.kafka.listen-topic", "test-integration-topic-2");
    }

    @Autowired
    private ArticlePublisherService publisherService;

    @Autowired
    private TestArticleConsumer testConsumer;

    @Test
    void shouldPublishAndReceiveArticleEvent() throws Exception {
        ArticlePublishedEvent event = new ArticlePublishedEvent(
                "id-y", "Cross Repo Test", "Alice", Instant.parse("2026-06-16T12:00:00Z")
        );

        publisherService.publish(event);

        ArticlePublishedEvent received = testConsumer.poll(Duration.ofSeconds(10));
        assertThat(received).isNotNull();
        assertThat(received.id()).isEqualTo("id-y");
        assertThat(received.title()).isEqualTo("Cross Repo Test");
    }
}
