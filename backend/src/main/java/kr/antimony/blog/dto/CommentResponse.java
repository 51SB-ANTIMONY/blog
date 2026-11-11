package kr.antimony.blog.dto;

import kr.antimony.blog.domain.Article;
import kr.antimony.blog.domain.Comment;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Getter
public class CommentResponse {
    private final Long id;
    private final String content; // 비밀 댓글인데 볼 권한이 없으면 null, 삭제된 글도 null
    private final String author; // 작성자 닉네임; 삭제된 댓글이거나 볼 권한이 없는 비밀 댓글이면 null
    private final boolean locked;
    private final boolean deleted;
    private final boolean editable; // 보는 사람이 댓글 작성자인지 -> 수정/삭제 버튼 보일지 여부 결정
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final List<CommentResponse> replies; // 대댓글 목록

    // accountsSub: 로그인한 사용자 ID (accounts ID) - 로그인 안 했을 시 null
    public CommentResponse (Comment comment, Article article, String accountsSub, List<CommentResponse> replies) {
        boolean isCommentAuthor = accountsSub != null && accountsSub.equals(comment.getAuthor().getAccountsSub());
        boolean isArticleAuthor = accountsSub != null && accountsSub.equals(article.getAuthor().getAccountsSub());
        // 대댓글이면 부모 댓글 작성자도 잠긴 대댓글을 볼 수 있다 (자기 댓글에 달린 답글이므로)
        Comment parent = comment.getParent();
        boolean isParentAuthor = accountsSub != null && parent != null
                && accountsSub.equals(parent.getAuthor().getAccountsSub());
        boolean canRead = !comment.isLocked() || isCommentAuthor || isArticleAuthor || isParentAuthor;

        this.id = comment.getId();
        this.content = comment.isDeleted() || !canRead? null: comment.getContent();
        // 볼 수 없는 잠긴 댓글은 누가 썼는지도 보내지 않는다
        this.author = comment.isDeleted() || !canRead? null: comment.getAuthor().getNickname();
        this.locked = comment.isLocked();
        this.deleted = comment.isDeleted();
        this.editable = !comment.isDeleted() && isCommentAuthor;
        this.createdAt = withZone(comment.getCreatedAt());
        this.updatedAt = withZone(comment.getUpdatedAt());
        this.replies = replies;
    }

    // 엔티티의 시각은 서버 시간대 기준이다 (JPA가 DB의 UTC 값을 서버 시간대로 바꿔 읽는다)
    private static OffsetDateTime withZone(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
