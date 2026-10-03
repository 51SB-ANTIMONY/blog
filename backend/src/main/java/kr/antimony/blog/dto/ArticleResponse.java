package kr.antimony.blog.dto;

import kr.antimony.blog.domain.Article;
import kr.antimony.blog.domain.Category;
import lombok.Getter;

import java.time.LocalDateTime;

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
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

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
        this.createdAt = article.getCreatedAt();
        this.updatedAt = article.getUpdatedAt();
    }
}
