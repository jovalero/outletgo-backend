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

    @Column(length = 100)
    private String date;

    @Column(length = 100)
    private String author;

    @Column(columnDefinition = "text")
    private String image;

    @Column(length = 50)
    private String color;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "blog_article_paragraphs", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "paragraph", columnDefinition = "text")
    @com.fasterxml.jackson.annotation.JsonFormat(with = com.fasterxml.jackson.annotation.JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    @Builder.Default
    private List<String> content = new ArrayList<>();

    @com.fasterxml.jackson.annotation.JsonProperty("content")
    public void setContent(Object rawContent) {
        if (rawContent == null) {
            this.content = new ArrayList<>();
        } else if (rawContent instanceof List<?>) {
            List<String> list = new ArrayList<>();
            for (Object item : (List<?>) rawContent) {
                if (item != null) {
                    list.add(item.toString());
                }
            }
            this.content = list;
        } else if (rawContent instanceof String) {
            String str = ((String) rawContent).trim();
            if (str.isEmpty()) {
                this.content = new ArrayList<>();
            } else {
                List<String> list = new ArrayList<>();
                for (String part : str.split("\n")) {
                    if (!part.trim().isEmpty()) {
                        list.add(part.trim());
                    }
                }
                this.content = list;
            }
        }
    }

    @Column(length = 20)
    @Builder.Default
    private String status = "PUBLISHED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
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
        if (date == null || date.trim().isEmpty()) {
            date = java.time.LocalDate.now().toString();
        }
        if (author == null || author.trim().isEmpty()) {
            author = "Equipo OutletGo";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
