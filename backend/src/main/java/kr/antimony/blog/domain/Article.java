package kr.antimony.blog.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@EntityListeners(AuditingEntityListener.class)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private Category category;

    // 목록에 보일 요약. 비워 두면 본문 앞부분을 쓴다 (excerpt())
    @Column(name = "summary", length = 300)
    private String summary;

    // 작성자. 작성자 정보가 필요할 때만 읽도록 지연 로딩한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 요약을 쓰지 않았을 때 본문에서 잘라 쓰는 길이
    private static final int EXCERPT_LENGTH = 120;

    @Builder
    public Article(User author, String title, String content, Category category, String summary) {
        this.author = author;
        this.title = title;
        this.content = content;
        this.category = category;
        this.summary = blankToNull(summary);
    }

    public void update(String title, String content, Category category, String summary) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.summary = blankToNull(summary);
    }

    // 목록에 보일 요약: 요약을 썼으면 그것, 아니면 본문 앞부분 (줄바꿈과 연속 공백은 한 칸으로)
    public String excerpt() {
        if (summary != null) {
            return summary;
        }
        String text = content.strip().replaceAll("\\s+", " ");
        return text.length() <= EXCERPT_LENGTH ? text : text.substring(0, EXCERPT_LENGTH) + "…";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}