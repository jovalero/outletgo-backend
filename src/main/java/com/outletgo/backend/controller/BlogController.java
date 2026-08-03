package com.outletgo.backend.controller;

import com.outletgo.backend.entity.BlogArticle;
import com.outletgo.backend.entity.BlogCategory;
import com.outletgo.backend.service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/blogs", "/blogs"})
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BlogController {

    private final BlogService blogService;

    @GetMapping({"", "/"})
    public ResponseEntity<List<BlogArticle>> getBlogs(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "category", required = false) String category) {
        return ResponseEntity.ok(blogService.getAllArticles(query, category));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BlogArticle> getBlogById(@PathVariable("id") UUID id) {
        return blogService.getArticleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<BlogCategory>> getCategories() {
        return ResponseEntity.ok(blogService.getAllCategories());
    }
}
