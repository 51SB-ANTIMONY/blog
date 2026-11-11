package kr.antimony.blog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class UpdateCommentRequest {
    @NotBlank(message = "내용을 입력해 주세요.")
    private String content;

    // 비밀 댓글인가. 보내지 않으면 false
    private boolean locked;
}
