package kr.antimony.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.antimony.blog.domain.Article;
import kr.antimony.blog.domain.Category;
import kr.antimony.blog.domain.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class AddArticleRequest {
    @NotBlank(message = "제목을 입력해 주세요.")
    @Size(max = 255, message = "제목은 255자 이하로 입력해 주세요.")
    private String title;

    @NotBlank(message = "내용을 입력해 주세요.")
    private String content;

    @NotNull(message = "카테고리를 골라 주세요.")
    private Category category;

    // 선택. 비워 두면 목록에 본문 앞부분이 보인다
    @Size(max = 300, message = "요약은 300자 이하로 입력해 주세요.")
    private String summary;

    public Article toEntity(User author) {
        return Article.builder()
                .title(title)
                .content(content)
                .author(author)
                .category(category)
                .summary(summary)
                .build();
    }
}