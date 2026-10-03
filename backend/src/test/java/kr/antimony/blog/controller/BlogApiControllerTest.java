package kr.antimony.blog.controller;

import kr.antimony.blog.domain.Article;
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
        final String requestBody = objectMapper.writeValueAsString(new AddArticleRequest(title, content));

        // when
        ResultActions result = mockMvc.perform(post(url)
                .with(loginAs(user))
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
                        .with(loginAs(user)))
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
        UpdateArticleRequest request = new UpdateArticleRequest("new title", "new content");

        // when
        ResultActions result = mockMvc.perform(put(url, savedArticle.getId())
                .with(loginAs(user))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isOk());

        Article article = blogRepository.findById(savedArticle.getId()).get();
        assertThat(article.getTitle()).isEqualTo("new title");
        assertThat(article.getContent()).isEqualTo("new content");
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
        UpdateArticleRequest request = new UpdateArticleRequest("new title", "new content");

        // when
        ResultActions result = mockMvc.perform(put(url, savedArticle.getId())
                .with(loginAs(user))
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
                .with(loginAs(user)));

        // then
        result.andExpect(status().isForbidden());
        assertThat(blogRepository.findById(savedArticle.getId())).isPresent();
    }

    @DisplayName("addArticle: 제목과 내용이 비어 있으면 400과 필드별 에러를 응답한다.")
    @Test
    public void addArticle_invalid() throws Exception {
        // given
        final String url = "/api/articles";
        final String requestBody = objectMapper.writeValueAsString(new AddArticleRequest(" ", ""));

        // when
        ResultActions result = mockMvc.perform(post(url)
                .with(loginAs(user))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(requestBody));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.errors.title").value("제목을 입력해 주세요."))
                .andExpect(jsonPath("$.errors.content").value("내용을 입력해 주세요."));

        assertThat(blogRepository.findAll()).isEmpty();
    }

    private Article createArticle(User author) {
        return blogRepository.save(Article.builder()
                .title("title")
                .author(author)
                .content("content")
                .build());
    }

    private User otherUser() {
        return userRepository.save(User.builder()
                .accountsSub("2002")
                .nickname("다른 회원")
                .build());
    }
}
