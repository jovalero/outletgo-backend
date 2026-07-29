package com.outletgo.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "blog_articles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlogArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, length = 100)
    private String date;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(columnDefinition = "text")
    private String image;

    @Column(length = 50)
    private String color;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "blog_article_paragraphs", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "paragraph", columnDefinition = "text")
    @Builder.Default
    private List<String> content = new ArrayList<>();

    @Column(length = 20)
    @Builder.Default
    private String status = "PUBLISHED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "PUBLISHED";
        }
        if (color == null) {
            color = "#2B8FD4";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
