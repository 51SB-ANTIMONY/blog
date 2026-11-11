package kr.antimony.blog.controller;

import kr.antimony.blog.domain.Article;
import kr.antimony.blog.domain.Category;
import kr.antimony.blog.domain.Comment;
import kr.antimony.blog.domain.User;
import kr.antimony.blog.dto.AddCommentRequest;
import kr.antimony.blog.dto.UpdateCommentRequest;
import kr.antimony.blog.repository.BlogRepository;
import kr.antimony.blog.repository.CommentRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CommentApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    UserRepository userRepository;

    // 글 작성자, 댓글 작성자, 그 밖의 회원
    User articleAuthor;
    User commenter;
    User other;
    Article article;

    @BeforeEach
    void setUp() {
        // 댓글 → 글 → 회원 순서로 지운다 (댓글이 글과 회원을 참조한다)
        // 댓글은 한 번의 DELETE로 지운다. 하나씩 지우면 부모를 지울 때 DB가 답글을 먼저 지워(ON DELETE CASCADE) 답글 삭제가 실패한다
        commentRepository.deleteAllInBatch();
        blogRepository.deleteAll();
        userRepository.deleteAll();
        articleAuthor = saveUser("1001", "글쓴이");
        commenter = saveUser("1002", "댓글러");
        other = saveUser("1003", "다른회원");
        article = blogRepository.save(Article.builder()
                .author(articleAuthor)
                .title("title")
                .content("content")
                .category(Category.DAILY)
                .build());
    }

    private User saveUser(String sub, String nickname) {
        return userRepository.save(User.builder().accountsSub(sub).nickname(nickname).build());
    }

    private static RequestPostProcessor loginAs(User user) {
        return oidcLogin().idToken(token -> token.subject(user.getAccountsSub()));
    }

    private Comment saveComment(User author, Comment parent, String content, boolean locked) {
        return commentRepository.save(Comment.builder()
                .article(article)
                .parent(parent)
                .author(author)
                .content(content)
                .locked(locked)
                .build());
    }

    private ResultActions postComment(User user, AddCommentRequest request) throws Exception {
        return mockMvc.perform(post("/api/articles/{id}/comments", article.getId())
                .with(loginAs(user))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions getComments(User viewer) throws Exception {
        var request = get("/api/articles/{id}/comments", article.getId());
        return mockMvc.perform(viewer == null ? request : request.with(loginAs(viewer)));
    }

    @DisplayName("addComment: 댓글 작성에 성공한다. 작성자는 로그인한 회원이다.")
    @Test
    void addComment() throws Exception {
        postComment(commenter, new AddCommentRequest("좋은 글이에요", false, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("좋은 글이에요"))
                .andExpect(jsonPath("$.author").value("댓글러"))
                .andExpect(jsonPath("$.editable").value(true));

        assertThat(commentRepository.findAll()).hasSize(1);
    }

    @DisplayName("addComment: 로그인하지 않으면 401")
    @Test
    void addComment_unauthorized() throws Exception {
        mockMvc.perform(post("/api/articles/{id}/comments", article.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(new AddCommentRequest("내용", false, null))))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("addComment: 내용이 비어 있으면 400")
    @Test
    void addComment_blank() throws Exception {
        postComment(commenter, new AddCommentRequest(" ", false, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").value("내용을 입력해 주세요."));
    }

    @DisplayName("addComment: 대댓글에 대댓글을 달면 400")
    @Test
    void addComment_replyToReply() throws Exception {
        Comment root = saveComment(commenter, null, "댓글", false);
        Comment reply = saveComment(other, root, "답글", false);

        postComment(commenter, new AddCommentRequest("답글의 답글", false, reply.getId()))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("addComment: 다른 글의 댓글에 답글을 달면 400")
    @Test
    void addComment_parentInOtherArticle() throws Exception {
        Article otherArticle = blogRepository.save(Article.builder()
                .author(articleAuthor)
                .title("other")
                .content("other")
                .category(Category.DAILY)
                .build());
        Comment parentElsewhere = commentRepository.save(Comment.builder()
                .article(otherArticle)
                .author(other)
                .content("다른 글의 댓글")
                .build());

        postComment(commenter, new AddCommentRequest("답글", false, parentElsewhere.getId()))
                .andExpect(status().isBadRequest());
        assertThat(commentRepository.findAll()).hasSize(1);
    }

    @DisplayName("addComment: 삭제된 댓글에 답글을 달면 400")
    @Test
    void addComment_parentDeleted() throws Exception {
        Comment root = saveComment(other, null, "댓글", false);
        mockMvc.perform(delete("/api/comments/{id}", root.getId())
                        .with(loginAs(other))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        postComment(commenter, new AddCommentRequest("답글", false, root.getId()))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("findAllComments: 로그인하지 않아도 보이고, 대댓글은 부모 댓글 아래에 먼저 쓴 순서로 묶인다.")
    @Test
    void findAllComments() throws Exception {
        Comment first = saveComment(commenter, null, "첫 댓글", false);
        saveComment(other, null, "두 번째 댓글", false);
        saveComment(articleAuthor, first, "첫 답글", false);
        saveComment(other, first, "두 번째 답글", false);

        getComments(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("첫 댓글"))
                .andExpect(jsonPath("$[0].editable").value(false))
                .andExpect(jsonPath("$[0].replies.length()").value(2))
                .andExpect(jsonPath("$[0].replies[0].content").value("첫 답글"))
                .andExpect(jsonPath("$[0].replies[1].content").value("두 번째 답글"))
                .andExpect(jsonPath("$[1].content").value("두 번째 댓글"))
                .andExpect(jsonPath("$[1].replies.length()").value(0));
    }

    @DisplayName("findAllComments: 없는 글이면 404")
    @Test
    void findAllComments_notFound() throws Exception {
        mockMvc.perform(get("/api/articles/{id}/comments", article.getId() + 1000))
                .andExpect(status().isNotFound());
    }

    @DisplayName("잠긴 댓글: 댓글 작성자와 글 작성자는 보고, 다른 회원과 로그인하지 않은 사람에게는 내용과 작성자를 가린다.")
    @Test
    void lockedComment() throws Exception {
        saveComment(commenter, null, "비밀 댓글", true);

        getComments(commenter)
                .andExpect(jsonPath("$[0].content").value("비밀 댓글"))
                .andExpect(jsonPath("$[0].author").value("댓글러"));
        getComments(articleAuthor)
                .andExpect(jsonPath("$[0].content").value("비밀 댓글"))
                .andExpect(jsonPath("$[0].author").value("댓글러"));
        getComments(other)
                .andExpect(jsonPath("$[0].locked").value(true))
                .andExpect(jsonPath("$[0].content").value(nullValue()))
                .andExpect(jsonPath("$[0].author").value(nullValue()));
        getComments(null)
                .andExpect(jsonPath("$[0].content").value(nullValue()))
                .andExpect(jsonPath("$[0].author").value(nullValue()));
    }

    @DisplayName("잠긴 대댓글: 부모 댓글 작성자도 볼 수 있다.")
    @Test
    void lockedReply_parentAuthorCanRead() throws Exception {
        Comment root = saveComment(commenter, null, "댓글", false);
        saveComment(other, root, "비밀 답글", true);

        getComments(commenter)
                .andExpect(jsonPath("$[0].replies[0].content").value("비밀 답글"))
                .andExpect(jsonPath("$[0].replies[0].author").value("다른회원"));
    }

    @DisplayName("updateComment: 작성자는 내용과 잠금을 바꿀 수 있다.")
    @Test
    void updateComment() throws Exception {
        Comment comment = saveComment(commenter, null, "처음", false);

        mockMvc.perform(put("/api/comments/{id}", comment.getId())
                        .with(loginAs(commenter))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(new UpdateCommentRequest("고침", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("고침"))
                .andExpect(jsonPath("$.locked").value(true));

        Comment updated = commentRepository.findById(comment.getId()).orElseThrow();
        assertThat(updated.getContent()).isEqualTo("고침");
        assertThat(updated.isLocked()).isTrue();
    }

    @DisplayName("updateComment: 남의 댓글을 수정하면 403")
    @Test
    void updateComment_forbidden() throws Exception {
        Comment comment = saveComment(commenter, null, "처음", false);

        mockMvc.perform(put("/api/comments/{id}", comment.getId())
                        .with(loginAs(other))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(new UpdateCommentRequest("고침", false))))
                .andExpect(status().isForbidden());

        assertThat(commentRepository.findById(comment.getId()).orElseThrow().getContent()).isEqualTo("처음");
    }

    @DisplayName("updateComment: 삭제된 댓글을 수정하면 400")
    @Test
    void updateComment_deleted() throws Exception {
        Comment comment = saveComment(commenter, null, "처음", false);
        mockMvc.perform(delete("/api/comments/{id}", comment.getId())
                        .with(loginAs(commenter))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/comments/{id}", comment.getId())
                        .with(loginAs(commenter))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(new UpdateCommentRequest("고침", false))))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("deleteComment: 삭제하면 행은 남고 내용과 작성자가 비며, 답글은 그대로 남는다.")
    @Test
    void deleteComment() throws Exception {
        Comment root = saveComment(commenter, null, "댓글", false);
        saveComment(other, root, "답글", false);

        mockMvc.perform(delete("/api/comments/{id}", root.getId())
                        .with(loginAs(commenter))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        getComments(null)
                .andExpect(jsonPath("$[0].deleted").value(true))
                .andExpect(jsonPath("$[0].content").value(nullValue()))
                .andExpect(jsonPath("$[0].author").value(nullValue()))
                .andExpect(jsonPath("$[0].editable").value(false))
                .andExpect(jsonPath("$[0].replies[0].content").value("답글"));
    }

    @DisplayName("deleteComment: 남의 댓글을 삭제하면 403")
    @Test
    void deleteComment_forbidden() throws Exception {
        Comment comment = saveComment(commenter, null, "댓글", false);

        mockMvc.perform(delete("/api/comments/{id}", comment.getId())
                        .with(loginAs(other))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(commentRepository.findById(comment.getId()).orElseThrow().isDeleted()).isFalse();
    }

    @DisplayName("글을 삭제하면 그 글의 댓글과 답글도 함께 지워진다.")
    @Test
    void deleteArticle_deletesComments() throws Exception {
        Comment root = saveComment(commenter, null, "댓글", false);
        saveComment(other, root, "답글", false);

        mockMvc.perform(delete("/api/articles/{id}", article.getId())
                        .with(loginAs(articleAuthor))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(blogRepository.findAll()).isEmpty();
        assertThat(commentRepository.findAll()).isEmpty();
    }
}
