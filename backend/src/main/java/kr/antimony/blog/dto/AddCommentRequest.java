package kr.antimony.blog.dto;

import jakarta.validation.constraints.NotBlank;
import kr.antimony.blog.domain.Article;
import kr.antimony.blog.domain.Comment;
import kr.antimony.blog.domain.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class AddCommentRequest {
    @NotBlank(message = "내용을 입력해 주세요.")
    private String content;

    // 비밀 댓글인가. 보내지 않으면 false
    private boolean locked;

    // 대댓글이면 부모 댓글 id, 일반 댓글이면 null
    private Long parentId;

    // article, parent, author는 서비스가 DB에서 찾아서 넘긴다
    public Comment toEntity(Article article, Comment parent, User author) {
        return Comment.builder()
                .article(article)
                .parent(parent)
                .author(author)
                .content(content)
                .locked(locked)
                .build();
    }
}
