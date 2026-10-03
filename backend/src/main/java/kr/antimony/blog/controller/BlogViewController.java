package kr.antimony.blog.controller;

import lombok.RequiredArgsConstructor;
import kr.antimony.blog.domain.Article;
import kr.antimony.blog.dto.ArticleListViewResponse;
import kr.antimony.blog.dto.ArticleViewResponse;
import kr.antimony.blog.service.BlogService;
import kr.antimony.blog.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RequiredArgsConstructor
@Controller
public class BlogViewController {
    private final BlogService blogService;
    private final UserService userService;

    // accounts에서 로그아웃한 뒤 돌아오는 주소(http://localhost:8080/)
    @GetMapping("/")
    public String home() {
        return "redirect:/articles";
    }
    @GetMapping("/articles")
    public String getArticles(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        List<ArticleListViewResponse> articles = blogService.findAll().stream()
                .map(ArticleListViewResponse::new)
                .toList();

        model.addAttribute("articles", articles);
        // 로그인했으면 닉네임, 아니면 null (화면에서 로그인 링크를 보여 준다)
        model.addAttribute("nickname", oidcUser == null ? null
                : userService.findByAccountsSub(oidcUser.getSubject()).getNickname());

        return "articleList";
    }

    @GetMapping("/articles/{id}")
    public String getArticle(@PathVariable Long id, Model model) {
        Article article = blogService.findById(id);
        model.addAttribute("article", new ArticleViewResponse(article));
        return "article";
    }

    @GetMapping("/new-article")
    public String newArticle(@RequestParam(required = false) Long id, Model model) {
        if (id == null) {
            model.addAttribute("article", new ArticleViewResponse());
        } else {
            Article article = blogService.findById(id);
            model.addAttribute("article", new ArticleViewResponse(article));
        }

        return "newArticle";
    }
}