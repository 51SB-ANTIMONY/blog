package kr.antimony.service;

import kr.antimony.domain.Article;
import kr.antimony.dto.AddArticleRequest;
import kr.antimony.dto.UpdateArticleRequest;
import kr.antimony.exception.ArticleForbiddenException;
import kr.antimony.exception.ArticleNotFoundException;
import kr.antimony.repository.BlogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class BlogService {
    private final BlogRepository blogRepository;

    public Article save(AddArticleRequest request, String userName) {
        return blogRepository.save(request.toEntity(userName));
    }

    // 최신 글이 먼저 오도록 id 내림차순
    public List<Article> findAll() {
        return blogRepository.findAllByOrderByIdDesc();
    }

    public Article findById(long id) {
        return blogRepository.findById(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
    }

    public void delete(long id) {
        Article article = findById(id);

        authorizeArticleAuthor(article);
        blogRepository.delete(article);
    }

    @Transactional
    public Article update(long id, UpdateArticleRequest request) {
        Article article = findById(id);

        authorizeArticleAuthor(article);
        article.update(request.getTitle(), request.getContent());

        return article;
    }

    private static void authorizeArticleAuthor(Article article) {
        String userName = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!article.getAuthor().equals(userName)) {
            throw new ArticleForbiddenException();
        }
    }
}