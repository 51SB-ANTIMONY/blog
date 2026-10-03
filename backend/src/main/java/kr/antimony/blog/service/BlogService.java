package kr.antimony.blog.service;

import kr.antimony.blog.domain.Article;
import kr.antimony.blog.domain.User;
import kr.antimony.blog.dto.AddArticleRequest;
import kr.antimony.blog.dto.UpdateArticleRequest;
import kr.antimony.blog.exception.ArticleForbiddenException;
import kr.antimony.blog.exception.ArticleNotFoundException;
import kr.antimony.blog.repository.BlogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class BlogService {
    private final BlogRepository blogRepository;
    private final UserService userService;

    // accountsSub: 로그인한 사용자의 이름(Authentication.getName()), 즉 ID 토큰의 sub
    public Article save(AddArticleRequest request, String accountsSub) {
        User author = userService.findByAccountsSub(accountsSub);
        return blogRepository.save(request.toEntity(author));
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
        String accountsSub = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!article.getAuthor().getAccountsSub().equals(accountsSub)) {
            throw new ArticleForbiddenException();
        }
    }
}