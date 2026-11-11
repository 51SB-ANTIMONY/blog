package kr.antimony.blog.domain;

import jakarta.persistence.*;
import kr.antimony.blog.exception.InvalidCommentException;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    // 댓글이 달린 글
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    // 부모 댓글 (null인 경우 그냥 기본 댓글, null이 아닌 경우 대댓글)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    //댓글 작성자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "locked", nullable = false)
    private boolean locked;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public Comment(Article article, Comment parent, User author, String content, boolean locked) {
        if(parent != null && parent.getParent() != null) {
            throw new InvalidCommentException("대댓글에는 대댓글을 달 수 없습니다.");
        }
        this.article = article;
        this.parent = parent;
        this.author = author;
        this.content = content;
        this.locked = locked;
    }

    public void update(String content, boolean locked) {
        if(this.deleted) {
            throw new InvalidCommentException("삭제된 댓글은 수정할 수 없습니다.");
        }
        this.content = content;
        this.locked = locked;
    }

    public void markDeleted() {
        this.content = null;
        this.deleted = true;
    }

}
