package com.outletgo.backend.controller;

import com.outletgo.backend.entity.BlogArticle;
import com.outletgo.backend.entity.BlogCategory;
import com.outletgo.backend.service.BlogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/blogs", "/admin/blogs"})
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminBlogController {

    private final BlogService blogService;

    @PostMapping
    public ResponseEntity<?> createBlog(@RequestBody BlogArticle article) {
        try {
            BlogArticle created = blogService.createArticle(article);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("Error creating blog article: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getClass().getName() + " - " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<BlogArticle> updateBlog(@PathVariable("id") UUID id, @RequestBody BlogArticle article) {
        return blogService.updateArticle(id, article)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBlog(@PathVariable("id") UUID id) {
        if (blogService.deleteArticle(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/categories")
    public ResponseEntity<BlogCategory> createCategory(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(blogService.createCategory(name));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable("id") UUID id) {
        if (blogService.deleteCategory(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
