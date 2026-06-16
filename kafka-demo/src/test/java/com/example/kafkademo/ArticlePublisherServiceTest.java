package com.example.kafkademo;

import com.example.sharedmodel.ArticlePublishedEvent;
import com.example.kafkademo.publisher.ArticlePublisherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticlePublisherServiceTest {

    @Mock
    private KafkaTemplate<String, ArticlePublishedEvent> kafkaTemplate;

    @Captor
    private ArgumentCaptor<ArticlePublishedEvent> eventCaptor;

    @Test
    void shouldPublishArticleEvent() {
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(new CompletableFuture<>());

        ArticlePublisherService service = new ArticlePublisherService(kafkaTemplate, "test-topic");

        ArticlePublishedEvent event = new ArticlePublishedEvent(
                "id-1", "Kafka 101", "Alice", Instant.parse("2026-06-15T12:00:00Z")
        );
        service.publish(event);

        verify(kafkaTemplate).send(eq("test-topic"), eq("id-1"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().title()).isEqualTo("Kafka 101");
        assertThat(eventCaptor.getValue().author()).isEqualTo("Alice");
    }
}
