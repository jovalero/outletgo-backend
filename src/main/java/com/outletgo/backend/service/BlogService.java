package com.outletgo.backend.service;

import com.outletgo.backend.entity.BlogArticle;
import com.outletgo.backend.entity.BlogCategory;
import com.outletgo.backend.repository.BlogArticleRepository;
import com.outletgo.backend.repository.BlogCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BlogService {

    private final BlogArticleRepository blogArticleRepository;
    private final BlogCategoryRepository blogCategoryRepository;

    public List<BlogArticle> getAllArticles(String query, String category) {
        return blogArticleRepository.searchArticles(query, category);
    }

    public Optional<BlogArticle> getArticleById(UUID id) {
        return blogArticleRepository.findById(id);
    }

    @Transactional
    public BlogArticle createArticle(BlogArticle article) {
        return blogArticleRepository.save(article);
    }

    @Transactional
    public Optional<BlogArticle> updateArticle(UUID id, BlogArticle updatedData) {
        return blogArticleRepository.findById(id).map(existing -> {
            existing.setTitle(updatedData.getTitle());
            existing.setCategory(updatedData.getCategory());
            existing.setAuthor(updatedData.getAuthor());
            existing.setDate(updatedData.getDate());
            existing.setImage(updatedData.getImage());
            existing.setColor(updatedData.getColor());
            existing.setContent(updatedData.getContent());
            existing.setStatus(updatedData.getStatus());
            return blogArticleRepository.save(existing);
        });
    }

    @Transactional
    public boolean deleteArticle(UUID id) {
        if (blogArticleRepository.existsById(id)) {
            blogArticleRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public List<BlogCategory> getAllCategories() {
        return blogCategoryRepository.findAll();
    }

    @Transactional
    public BlogCategory createCategory(String name) {
        if (blogCategoryRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("La categoría ya existe: " + name);
        }
        BlogCategory category = BlogCategory.builder()
                .name(name.trim())
                .build();
        return blogCategoryRepository.save(category);
    }

    @Transactional
    public boolean deleteCategory(UUID id) {
        if (blogCategoryRepository.existsById(id)) {
            blogCategoryRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
