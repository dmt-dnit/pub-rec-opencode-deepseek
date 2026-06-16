package com.example.sharedmodel;

import java.time.Instant;

public record ArticlePublishedEvent(
        String id,
        String title,
        String author,
        Instant publishedAt
) {}
