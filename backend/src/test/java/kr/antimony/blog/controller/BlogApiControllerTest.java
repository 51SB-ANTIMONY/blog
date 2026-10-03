package kr.antimony.blog.controller;

import kr.antimony.blog.domain.Article;
import kr.antimony.blog.domain.Category;
import kr.antimony.blog.domain.User;
import kr.antimony.blog.dto.AddArticleRequest;
import kr.antimony.blog.dto.UpdateArticleRequest;
import kr.antimony.blog.repository.BlogRepository;
import kr.antimony.blog.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BlogApiControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    UserRepository userRepository;

    User user;

    @BeforeEach
    void setUp() {
        // 글이 회원을 참조하므로 글을 먼저 지운다.
        blogRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(User.builder()
                .accountsSub("1001")
                .nickname("작성자")
                .build());
    }

    // accounts에서 로그인하고 돌아온 상태. 이름(Authentication.getName())은 ID 토큰의 sub다.
    private static RequestPostProcessor loginAs(User user) {
        return oidcLogin().idToken(token -> token.subject(user.getAccountsSub()));
    }

    @DisplayName("addArticle: 아티클 추가에 성공한다. 작성자는 로그인한 회원이다.")
    @Test
    public void addArticle() throws Exception {
        // given
        final String url = "/api/articles";
        final String title = "title";
        final String content = "content";
        final String requestBody = objectMapper.writeValueAsString(new AddArticleRequest(title, content, Category.STUDY, null));

        // when
        ResultActions result = mockMvc.perform(post(url)
                .with(loginAs(user))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(requestBody));

        // then
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.author").value("작성자"));

        List<Article> articles = blogRepository.findAll();
        assertThat(articles.size()).isEqualTo(1);
        assertThat(articles.get(0).getTitle()).isEqualTo(title);
        assertThat(articles.get(0).getContent()).isEqualTo(content);
        assertThat(articles.get(0).getAuthor().getId()).isEqualTo(user.getId());
    }

    @DisplayName("findAllArticles: 아티클 목록 조회에 성공한다.")
    @Test
    public void findAllArticles() throws Exception {
        // given
        final String url = "/api/articles";
        Article savedArticle = createArticle(user);

        // when
        final ResultActions resultActions = mockMvc.perform(get(url)
                .with(loginAs(user))
                .accept(MediaType.APPLICATION_JSON));

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(savedArticle.getId()))
                .andExpect(jsonPath("$[0].content").value(savedArticle.getContent()))
                .andExpect(jsonPath("$[0].title").value(savedArticle.getTitle()))
                .andExpect(jsonPath("$[0].author").value("작성자"));
    }

    @DisplayName("findArticle: 아티클 단건 조회에 성공한다.")
    @Test
    public void findArticle() throws Exception {
        // given
        final String url = "/api/articles/{id}";
        Article savedArticle = createArticle(user);

        // when
        final ResultActions resultActions = mockMvc.perform(get(url, savedArticle.getId())
                .with(loginAs(user)));

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(savedArticle.getContent()))
                .andExpect(jsonPath("$.title").value(savedArticle.getTitle()))
                .andExpect(jsonPath("$.author").value("작성자"));
    }

    @DisplayName("deleteArticle: 아티클 삭제에 성공한다.")
    @Test
    public void deleteArticle() throws Exception {
        // given
        final String url = "/api/articles/{id}";
        Article savedArticle = createArticle(user);

        // when
        mockMvc.perform(delete(url, savedArticle.getId())
                        .with(loginAs(user))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // then
        assertThat(blogRepository.findAll()).isEmpty();
    }

    @DisplayName("updateArticle: 아티클 수정에 성공한다.")
    @Test
    public void updateArticle() throws Exception {
        // given
        final String url = "/api/articles/{id}";
        Article savedArticle = createArticle(user);
        UpdateArticleRequest request = new UpdateArticleRequest("new title", "new content", Category.TREE, "새 요약");

        // when
        ResultActions result = mockMvc.perform(put(url, savedArticle.getId())
                .with(loginAs(user))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isOk());

        Article article = blogRepository.findById(savedArticle.getId()).get();
        assertThat(article.getTitle()).isEqualTo("new title");
        assertThat(article.getContent()).isEqualTo("new content");
        assertThat(article.getCategory()).isEqualTo(Category.TREE);
        assertThat(article.getSummary()).isEqualTo("새 요약");
    }

    @DisplayName("findArticle: 없는 아티클을 조회하면 404와 에러 메시지를 응답한다.")
    @Test
    public void findArticle_notFound() throws Exception {
        // given
        final String url = "/api/articles/{id}";
        final long missingId = 999_999L;

        // when
        final ResultActions resultActions = mockMvc.perform(get(url, missingId)
                .with(loginAs(user)));

        // then
        resultActions
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("글을 찾을 수 없습니다. (id: " + missingId + ")"));
    }

    @DisplayName("updateArticle: 작성자가 아니면 403을 응답하고 아티클은 바뀌지 않는다.")
    @Test
    public void updateArticle_forbidden() throws Exception {
        // given
        final String url = "/api/articles/{id}";
        Article savedArticle = createArticle(otherUser());
        UpdateArticleRequest request = new UpdateArticleRequest("new title", "new content", Category.TREE, "새 요약");

        // when
        ResultActions result = mockMvc.perform(put(url, savedArticle.getId())
                .with(loginAs(user))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("글 작성자만 수정하거나 삭제할 수 있습니다."));

        Article article = blogRepository.findById(savedArticle.getId()).get();
        assertThat(article.getTitle()).isEqualTo("title");
    }

    @DisplayName("deleteArticle: 작성자가 아니면 403을 응답하고 아티클은 삭제되지 않는다.")
    @Test
    public void deleteArticle_forbidden() throws Exception {
        // given
        final String url = "/api/articles/{id}";
        Article savedArticle = createArticle(otherUser());

        // when
        ResultActions result = mockMvc.perform(delete(url, savedArticle.getId())
                .with(loginAs(user))
                .with(csrf()));

        // then
        result.andExpect(status().isForbidden());
        assertThat(blogRepository.findById(savedArticle.getId())).isPresent();
    }

    @DisplayName("addArticle: 제목과 내용이 비어 있으면 400과 필드별 에러를 응답한다.")
    @Test
    public void addArticle_invalid() throws Exception {
        // given
        final String url = "/api/articles";
        final String requestBody = objectMapper.writeValueAsString(new AddArticleRequest(" ", "", null, null));

        // when
        ResultActions result = mockMvc.perform(post(url)
                .with(loginAs(user))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(requestBody));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.errors.title").value("제목을 입력해 주세요."))
                .andExpect(jsonPath("$.errors.content").value("내용을 입력해 주세요."))
                .andExpect(jsonPath("$.errors.category").value("카테고리를 골라 주세요."));

        assertThat(blogRepository.findAll()).isEmpty();
    }

    @DisplayName("글 읽기는 로그인하지 않아도 된다. 이때 editable은 false")
    @Test
    public void readWithoutLogin() throws Exception {
        Article savedArticle = createArticle(user);

        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(savedArticle.getId()))
                .andExpect(jsonPath("$[0].editable").value(false));
        mockMvc.perform(get("/api/articles/{id}", savedArticle.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false));
    }

    @DisplayName("editable: 작성자가 보면 true, 다른 회원이 보면 false")
    @Test
    public void editable() throws Exception {
        Article savedArticle = createArticle(user);

        mockMvc.perform(get("/api/articles/{id}", savedArticle.getId()).with(loginAs(user)))
                .andExpect(jsonPath("$.editable").value(true));
        mockMvc.perform(get("/api/articles/{id}", savedArticle.getId()).with(loginAs(otherUser())))
                .andExpect(jsonPath("$.editable").value(false));
    }

    @DisplayName("목록: category로 거를 수 있다. 응답에 category, summary, excerpt가 있다")
    @Test
    public void filterByCategory() throws Exception {
        blogRepository.save(Article.builder().author(user).title("일상 글").content("오늘은\n산책을   했다.")
                .category(Category.DAILY).build());
        blogRepository.save(Article.builder().author(user).title("학습 글").content("본문").summary("직접 쓴 요약")
                .category(Category.STUDY).build());

        mockMvc.perform(get("/api/articles").param("category", "STUDY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("학습 글"))
                .andExpect(jsonPath("$[0].category").value("STUDY"))
                .andExpect(jsonPath("$[0].summary").value("직접 쓴 요약"))
                .andExpect(jsonPath("$[0].excerpt").value("직접 쓴 요약"));

        // 요약이 없으면 본문 앞부분 (줄바꿈과 연속 공백은 한 칸으로)
        mockMvc.perform(get("/api/articles").param("category", "DAILY"))
                .andExpect(jsonPath("$[0].summary").doesNotExist())
                .andExpect(jsonPath("$[0].excerpt").value("오늘은 산책을 했다."));

        mockMvc.perform(get("/api/articles"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @DisplayName("요약이 없고 본문이 길면 120자에서 자르고 …를 붙인다")
    @Test
    public void longExcerpt() throws Exception {
        String content = "가".repeat(130);
        blogRepository.save(Article.builder().author(user).title("긴 글").content(content)
                .category(Category.DAILY).build());

        mockMvc.perform(get("/api/articles"))
                .andExpect(jsonPath("$[0].excerpt").value("가".repeat(120) + "…"));
    }

    @DisplayName("/api/me: 로그인하지 않으면 401, 로그인하면 blog 닉네임")
    @Test
    public void me() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").with(loginAs(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("작성자"));
    }

    private Article createArticle(User author) {
        return blogRepository.save(Article.builder()
                .title("title")
                .author(author)
                .content("content")
                .category(Category.DAILY)
                .build());
    }

    private User otherUser() {
        return userRepository.save(User.builder()
                .accountsSub("2002")
                .nickname("다른 회원")
                .build());
    }
}
