package kr.antimony.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.antimony.blog.domain.Category;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class UpdateArticleRequest {
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
}
