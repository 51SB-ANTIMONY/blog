package kr.antimony.blog.controller;

import jakarta.validation.Valid;
import kr.antimony.blog.domain.Article;
import kr.antimony.blog.domain.Category;
import kr.antimony.blog.dto.AddArticleRequest;
import kr.antimony.blog.dto.ArticleResponse;
import kr.antimony.blog.dto.UpdateArticleRequest;
import kr.antimony.blog.service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RequiredArgsConstructor
@RestController
public class BlogApiController {
    private final BlogService blogService;

    @PostMapping("/api/articles")
    public ResponseEntity<ArticleResponse> addArticle(@Valid @RequestBody AddArticleRequest request, Principal principal) {
        Article savedArticle = blogService.save(request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ArticleResponse(savedArticle, principal.getName()));
    }

    // 로그인하지 않아도 볼 수 있다. category가 없으면 전체
    @GetMapping("/api/articles")
    public ResponseEntity<List<ArticleResponse>> findAllArticles(@RequestParam(required = false) Category category,
                                                                 Principal principal) {
        List<ArticleResponse> articles = blogService.findAll(category)
                .stream()
                .map(article -> new ArticleResponse(article, nameOf(principal)))
                .toList();
        return ResponseEntity.ok()
                .body(articles);
    }

    // 로그인하지 않아도 볼 수 있다
    @GetMapping("/api/articles/{id}")
    public ResponseEntity<ArticleResponse> findArticle(@PathVariable long id, Principal principal) {
        Article article = blogService.findById(id);
        return ResponseEntity.ok()
                .body(new ArticleResponse(article, nameOf(principal)));
    }

    @DeleteMapping("/api/articles/{id}")
    public ResponseEntity<Void> deleteArticle(@PathVariable long id) {
        blogService.delete(id);
        return ResponseEntity.noContent()
                .build();
    }

    @PutMapping("/api/articles/{id}")
    public ResponseEntity<ArticleResponse> updateArticle(@PathVariable long id,
                                                         @Valid @RequestBody UpdateArticleRequest request,
                                                         Principal principal) {
        Article updatedArticle = blogService.update(id, request);
        return ResponseEntity.ok()
                .body(new ArticleResponse(updatedArticle, principal.getName()));
    }

    // 로그인한 사용자의 이름(= ID 토큰의 sub). 로그인하지 않았으면 null
    private static String nameOf(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
