package kr.antimony.blog.dto;

import kr.antimony.blog.domain.Article;
import kr.antimony.blog.domain.Category;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
public class ArticleResponse {
    private final Long id;
    private final String title;
    private final String content;
    private final Category category;
    // 작성자가 쓴 요약 (없으면 null). 수정 화면에서 그대로 보여 준다
    private final String summary;
    // 목록에 보일 요약 (요약이 없으면 본문 앞부분)
    private final String excerpt;
    // 작성자 닉네임
    private final String author;
    // 로그인한 사용자가 이 글의 작성자인가. 화면에서 수정·삭제 버튼을 보여 줄지 정한다
    private final boolean editable;
    // 작성·수정 시각. 시간대(+09:00 등)를 붙여 보내서, 화면이 서버 시간대와 상관없이 한국 시간으로 바꿀 수 있게 한다
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    // accountsSub: 로그인한 사용자의 sub. 로그인하지 않았으면 null
    public ArticleResponse(Article article, String accountsSub) {
        this.id = article.getId();
        this.title = article.getTitle();
        this.content = article.getContent();
        this.category = article.getCategory();
        this.summary = article.getSummary();
        this.excerpt = article.excerpt();
        this.author = article.getAuthor().getNickname();
        this.editable = accountsSub != null && accountsSub.equals(article.getAuthor().getAccountsSub());
        this.createdAt = withZone(article.getCreatedAt());
        this.updatedAt = withZone(article.getUpdatedAt());
    }

    // 엔티티의 시각은 서버 시간대 기준이다 (JPA가 DB의 UTC 값을 서버 시간대로 바꿔 읽는다)
    private static OffsetDateTime withZone(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
