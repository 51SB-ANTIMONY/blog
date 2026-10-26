package kr.antimony.blog.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

    // MySQL 전용 enum 타입 대신 문자열(VARCHAR)로 저장한다. 값이 늘어도 테이블을 바꿀 필요가 없다.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
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

    // 목록에 보일 요약: 요약을 썼으면 그것, 아니면 본문 앞부분
    // (Markdown 기호는 빼고, 줄바꿈과 연속 공백은 한 칸으로)
    public String excerpt() {
        if (summary != null) {
            return summary;
        }
        String text = plainText(content).strip().replaceAll("\\s+", " ");
        return text.length() <= EXCERPT_LENGTH ? text : text.substring(0, EXCERPT_LENGTH) + "…";
    }

    // 본문은 Markdown으로 쓴다. 요약에는 서식 기호 없이 글자만 남긴다.
    static String plainText(String markdown) {
        return markdown
                .replaceAll("(?m)^\\s*```.*$", "")                            // 코드 블록 울타리 (```java)
                .replaceAll("!\\[([^]]*)]\\([^)]*\\)", "$1")                  // 사진 ![설명](주소) → 설명
                .replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1")                   // 링크 [글자](주소) → 글자
                .replaceAll("(?m)^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$", "")      // 구분선 (---, ***)
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s+", "")                    // 소제목 (## 제목)
                .replaceAll("(?m)^(\\s{0,3}>\\s?)+", "")                      // 인용 (> 글)
                .replaceAll("(?m)^\\s*([-*+]|\\d+[.)])\\s+", "")              // 목록 (- 항목, 1. 항목)
                .replaceAll("\\*\\*|__|~~|[*`]", "");                         // 굵게, 기울임, 취소선, 코드
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}