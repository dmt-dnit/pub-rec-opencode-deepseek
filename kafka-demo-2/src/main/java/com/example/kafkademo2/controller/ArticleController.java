package com.example.kafkademo2.controller;

import com.example.sharedmodel.ArticlePublishedEvent;
import com.example.kafkademo2.publisher.ArticlePublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private static final Logger log = LoggerFactory.getLogger(ArticleController.class);

    private final ArticlePublisherService publisherService;

    public ArticleController(ArticlePublisherService publisherService) {
        this.publisherService = publisherService;
    }

    @PostMapping("/publish")
    public ArticlePublishedEvent publish(@RequestBody ArticlePublishedEvent event,
                                         @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt != null ? jwt.getSubject() : "anonymous";
        log.info("Publish requested by: {}", userEmail);

        ArticlePublishedEvent enriched = new ArticlePublishedEvent(
                event.id() != null ? event.id() : UUID.randomUUID().toString(),
                event.title(),
                event.author(),
                event.publishedAt()
        );
        publisherService.publish(enriched);
        return enriched;
    }
}
