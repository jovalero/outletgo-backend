package com.outletgo.backend.repository;

import com.outletgo.backend.entity.BlogArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BlogArticleRepository extends JpaRepository<BlogArticle, UUID> {

    @Query("SELECT b FROM BlogArticle b WHERE " +
           "(:category IS NULL OR :category = '' OR :category = 'Todos' OR LOWER(b.category) = LOWER(:category)) AND " +
           "(:query IS NULL OR :query = '' OR LOWER(b.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(b.author) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<BlogArticle> searchArticles(@Param("query") String query, @Param("category") String category);
}
