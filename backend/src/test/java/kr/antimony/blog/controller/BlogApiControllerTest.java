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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    private WebApplicationContext context;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    UserRepository userRepository;

    User user;

    @BeforeEach
    public void mockMvcSetUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .build();
        blogRepository.deleteAll();
    }


    @BeforeEach
    void setSecurityContext() {
        userRepository.deleteAll();
        user = userRepository.save(User.builder()
                .uid("user")
                .email("user@gmail.com")
                .password("test")
                .nickname("user")
                .build());

        // 실제 요청에서는 TokenAuthenticationFilter가 토큰의 sub(이메일)를 사용자 이름으로 넣는다.
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(), null, user.getAuthorities()));
    }


    @DisplayName("addArticle: 아티클 추가에 성공한다.")
    @Test
    public void addArticle() throws Exception {
        // given
        final String url = "/api/articles";
        final String title = "title";
        final String content = "content";
        final AddArticleRequest userRequest = new AddArticleRequest(title, content);

        final String requestBody = objectMapper.writeValueAsString(userRequest);

        Principal principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("username");

        // when
        ResultActions result = mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .principal(principal)
                .content(requestBody));

        // then
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.author").value("username"));

        List<Article> articles = blogRepository.findAll();

        assertThat(articles.size()).isEqualTo(1);
        assertThat(articles.get(0).getTitle()).isEqualTo(title);
        assertThat(articles.get(0).getContent()).isEqualTo(content);
    }

    @DisplayName("findAllArticles: 아티클 목록 조회에 성공한다.")
    @Test
    public void findAllArticles() throws Exception {
        // given
        final String url = "/api/articles";
        Article savedArticle = createDefaultArticle();

        // when
        final ResultActions resultActions = mockMvc.perform(get(url)
                .accept(MediaType.APPLICATION_JSON));

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(savedArticle.getId()))
                .andExpect(jsonPath("$[0].content").value(savedArticle.getContent()))
                .andExpect(jsonPath("$[0].title").value(savedArticle.getTitle()))
                .andExpect(jsonPath("$[0].author").value(savedArticle.getAuthor()));
    }

    @DisplayName("findArticle: 아티클 단건 조회에 성공한다.")
    @Test
    public void findArticle() throws Exception {
        // given
        final String url = "/api/articles/{id}";
        Article savedArticle = createDefaultArticle();

        // when
        final ResultActions resultActions = mockMvc.perform(get(url, savedArticle.getId()));

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(savedArticle.getContent()))
                .andExpect(jsonPath("$.title").value(savedArticle.getTitle()));
    }


    @DisplayName("deleteArticle: 아티클 삭제에 성공한다.")
    @Test
    public void deleteArticle() throws Exception {
        // given
        final String url = "/api/articles/{id}";
        Article savedArticle = createDefaultArticle();

        // when
        mockMvc.perform(delete(url, savedArticle.getId()))
                .andExpect(status().isNoContent());

        // then
        List<Article> articles = blogRepository.findAll();

        assertThat(articles).isEmpty();
    }


    @DisplayName("updateArticle: 아티클 수정에 성공한다.")
    @Test
    public void updateArticle() throws Exception {
        // given
        final String url = "/api/articles/{id}";
        Article savedArticle = createDefaultArticle();

        final String newTitle = "new title";
        final String newContent = "new content";

        UpdateArticleRequest request = new UpdateArticleRequest(newTitle, newContent);

        // when
        ResultActions result = mockMvc.perform(put(url, savedArticle.getId())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isOk());

        Article article = blogRepository.findById(savedArticle.getId()).get();

        assertThat(article.getTitle()).isEqualTo(newTitle);
        assertThat(article.getContent()).isEqualTo(newContent);
    }

    @DisplayName("findArticle: 없는 아티클을 조회하면 404와 에러 메시지를 응답한다.")
    @Test
    public void findArticle_notFound() throws Exception {
        // given
        final String url = "/api/articles/{id}";
        final long missingId = 999_999L;

        // when
        final ResultActions resultActions = mockMvc.perform(get(url, missingId));

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
        Article savedArticle = blogRepository.save(Article.builder()
                .title("title")
                .author("other@gmail.com")
                .content("content")
                .build());

        UpdateArticleRequest request = new UpdateArticleRequest("new title", "new content");

        // when
        ResultActions result = mockMvc.perform(put(url, savedArticle.getId())
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
        Article savedArticle = blogRepository.save(Article.builder()
                .title("title")
                .author("other@gmail.com")
                .content("content")
                .build());

        // when
        ResultActions result = mockMvc.perform(delete(url, savedArticle.getId()));

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

        Principal principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("username");

        // when
        ResultActions result = mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .principal(principal)
                .content(requestBody));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.errors.title").value("제목을 입력해 주세요."))
                .andExpect(jsonPath("$.errors.content").value("내용을 입력해 주세요."));

        assertThat(blogRepository.findAll()).isEmpty();
    }

    private Article createDefaultArticle() {
        return blogRepository.save(Article.builder()
                .title("title")
                .author(user.getEmail())
                .content("content")
                .build());
    }
}