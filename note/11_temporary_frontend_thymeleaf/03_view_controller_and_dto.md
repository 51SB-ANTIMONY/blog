---
title: 뷰 컨트롤러와 화면용 DTO
date: 2026-10-03
tags: [spring-boot, controller, dto, thymeleaf]
---

# 뷰 컨트롤러와 화면용 DTO

> 컨트롤러 어노테이션은 [Controller에서 쓰는 어노테이션](../06_spring_boot_code/03_controller_annotations.md), DTO는 [스프링 부트 계층 구조](../07_spring_boot_structure/01_layers.md)에서 설명했다.

## `@Controller`와 `@RestController`
기존 `BlogApiController`는 `@RestController`, 새로 만든 `BlogViewController`는 `@Controller`다.

| | `@RestController` | `@Controller` |
|---|---|---|
| 반환값의 의미 | 응답 본문에 담을 데이터 | 템플릿 파일 이름 (뷰 이름) |
| 응답 | JSON | HTML |
| 이 프로젝트 | `BlogApiController` | `BlogViewController` |

> **뷰(View)란?**
> 사용자에게 보여 주는 화면이다. 여기서는 Thymeleaf 템플릿으로 만든 HTML 페이지를 말한다.

`@Controller` 메서드가 문자열 `"articleList"`를 반환하면, Spring은 그 문자열을 뷰 이름으로 보고 `templates/articleList.html`을 찾아 HTML을 만든다.

`@RestController`는 `@Controller`에 `@ResponseBody`를 더한 것이다.

> **`@ResponseBody`란?**
> 메서드의 반환값을 뷰 이름으로 해석하지 말고, JSON 같은 형식으로 바꿔 응답 본문에 그대로 담으라는 표시다.

## `BlogViewController`
```java
@RequiredArgsConstructor
@Controller
public class BlogViewController {
    private final BlogService blogService;

    @GetMapping("/articles")
    public String getArticles(Model model) {
        List<ArticleListViewResponse> articles = blogService.findAll().stream()
                .map(ArticleListViewResponse::new)
                .toList();
        model.addAttribute("articles", articles);
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
```

| URL | 메서드 | 하는 일 | Model에 담는 값 | 템플릿 |
|---|---|---|---|---|
| `GET /articles` | `getArticles` | 모든 글 조회 | `articles` (DTO 목록) | `articleList.html` |
| `GET /articles/{id}` | `getArticle` | 글 하나 조회 | `article` (DTO) | `article.html` |
| `GET /new-article` | `newArticle` | 작성 화면이면 빈 DTO, 수정 화면이면 기존 글 DTO | `article` (DTO) | `newArticle.html` |

### `Model` 매개변수
메서드 매개변수에 `Model`을 적으면 Spring이 빈 `Model` 객체를 넣어 준다. 여기에 `addAttribute(이름, 값)`으로 데이터를 담으면 템플릿에서 `${이름}`으로 꺼낼 수 있다.

### 엔티티 목록을 DTO 목록으로 바꾸기
```java
blogService.findAll().stream()
        .map(ArticleListViewResponse::new)
        .toList();
```
> **Stream이란?**
> 목록의 원소를 하나씩 흘려보내면서 변환, 걸러내기 같은 작업을 이어서 적을 수 있게 해 주는 Java 기능이다.

- `.stream()`: `List<Article>`을 Stream으로 바꾼다.
- `.map(ArticleListViewResponse::new)`: 원소 하나하나를 `new ArticleListViewResponse(article)`로 바꾼다.
  > **메서드 참조(`::`)란?**
  > `article -> new ArticleListViewResponse(article)`을 짧게 쓴 표현이다. `클래스::new`는 그 클래스의 생성자를 가리킨다.
- `.toList()`: 결과를 다시 `List`로 모은다.

### `@RequestParam(required = false)`
```java
public String newArticle(@RequestParam(required = false) Long id, Model model)
```
- `@RequestParam`: `/new-article?id=3`처럼 주소의 `?` 뒤에 붙는 값(쿼리 파라미터)을 받는다.
  > **쿼리 파라미터란?**
  > URL 끝에 `?이름=값` 형태로 붙여 서버에 보내는 값이다. 여러 개면 `&`로 잇는다. 예: `?id=3&page=1`
- `required = false`: 이 값이 없어도 된다는 뜻이다. 없으면 `null`이 들어간다. 기본값은 `true`라서, 생략하면 `/new-article`로 접속했을 때 400 에러가 난다.
- 타입이 `long`이 아니라 `Long`인 이유: `long` 같은 기본 타입은 `null`을 담을 수 없다. `Long`은 객체 타입이라 `null`을 담을 수 있다.

> **`@PathVariable`과 `@RequestParam`의 차이**
> `@PathVariable`은 경로 자체에 들어 있는 값을 받는다. `/articles/3`의 `3`
> `@RequestParam`은 `?` 뒤의 쿼리 파라미터를 받는다. `/new-article?id=3`의 `3`

## 화면용 DTO 두 개
엔티티(`Article`)를 템플릿에 그대로 넘기지 않고, 화면에 필요한 필드만 복사한 DTO를 넘긴다.

### `ArticleListViewResponse` - 목록 화면용
```java
@Getter
public class ArticleListViewResponse {
    private final Long id;
    private final String title;
    private final String content;

    public ArticleListViewResponse(Article article) {
        this.id = article.getId();
        this.title = article.getTitle();
        this.content = article.getContent();
    }
}
```

### `ArticleViewResponse` - 상세/작성 화면용
```java
@NoArgsConstructor
@Getter
public class ArticleViewResponse {
    private Long id;
    private String title;
    private String content;
    private LocalDateTime createdAt;

    public ArticleViewResponse(Article article) {
        this.id = article.getId();
        this.title = article.getTitle();
        this.content = article.getContent();
        this.createdAt = article.getCreatedAt();
    }
}
```

### 두 DTO 비교
| | `ArticleListViewResponse` | `ArticleViewResponse` |
|---|---|---|
| 쓰는 화면 | 목록 | 상세, 작성/수정 |
| 필드 | `id`, `title`, `content` | `id`, `title`, `content`, `createdAt` |
| 필드 선언 | `final` | 일반 필드 |
| 기본 생성자 | 없음 | `@NoArgsConstructor`로 있음 |

> **`final` 필드란?**
> 한 번 값을 넣으면 바꿀 수 없는 필드다. 생성자에서 반드시 값을 넣어야 한다.

`ArticleViewResponse`에 기본 생성자가 필요한 이유는 새 글 작성 화면 때문이다. `new ArticleViewResponse()`로 모든 필드가 `null`인 빈 객체를 만들어 넘기고, 템플릿은 `article.id`가 `null`인지 보고 "등록" 버튼을 보여 준다([Thymeleaf](./02_thymeleaf.md)의 `newArticle.html` 참고).

`final` 필드는 생성자에서 값을 꼭 넣어야 하므로, 빈 객체를 만들 수 있게 `ArticleViewResponse`는 `final`을 쓰지 않았다.
