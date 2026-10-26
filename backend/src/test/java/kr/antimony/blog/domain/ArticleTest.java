package kr.antimony.blog.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleTest {

    private static String excerptOf(String content) {
        return Article.builder().title("제목").content(content).category(Category.DAILY).build().excerpt();
    }

    @DisplayName("요약: 소제목, 인용, 목록 기호를 뺀다")
    @Test
    void blockSymbols() {
        assertThat(excerptOf("# 큰 제목\n### 작은 제목\n> > 겹친 인용\n- 하나\n* 둘\n+ 셋\n1. 넷\n2) 다섯"))
                .isEqualTo("큰 제목 작은 제목 겹친 인용 하나 둘 셋 넷 다섯");
    }

    @DisplayName("요약: 굵게, 기울임, 취소선, 코드 기호를 빼고 글자는 남긴다")
    @Test
    void inlineSymbols() {
        assertThat(excerptOf("**굵게** *기울임* __굵게__ ~~취소~~ `코드`"))
                .isEqualTo("굵게 기울임 굵게 취소 코드");
    }

    @DisplayName("요약: 링크와 사진은 글자(설명)만 남긴다")
    @Test
    void linksAndImages() {
        assertThat(excerptOf("[블로그](https://blog.antimony.kr) ![교토의 저녁](/images/kyoto.jpg)"))
                .isEqualTo("블로그 교토의 저녁");
    }

    @DisplayName("요약: 코드 블록 울타리와 구분선은 빼고 코드 내용은 남긴다")
    @Test
    void fencesAndRules() {
        assertThat(excerptOf("```java\nint a = 1;\n```\n---\n끝"))
                .isEqualTo("int a = 1; 끝");
    }

    @DisplayName("요약: 줄바꿈 표시(줄 끝의 \\)는 빼고 줄바꿈은 한 칸으로")
    @Test
    void lineBreaks() {
        assertThat(excerptOf("첫 줄\\\n\\\n둘째 줄")).isEqualTo("첫 줄 둘째 줄");
    }

    @DisplayName("요약: 낱말 속 밑줄(snake_case)과 문장 속 숫자는 그대로 둔다")
    @Test
    void keepsPlainText() {
        assertThat(excerptOf("변수 user_name을 2026년에 바꿨다. 3.5배 빨라졌다."))
                .isEqualTo("변수 user_name을 2026년에 바꿨다. 3.5배 빨라졌다.");
    }

    @DisplayName("요약: 직접 쓴 요약이 있으면 본문 대신 그것을 그대로 쓴다")
    @Test
    void summaryFirst() {
        Article article = Article.builder().title("제목").content("## 본문").summary("**직접** 쓴 요약")
                .category(Category.DAILY).build();

        assertThat(article.excerpt()).isEqualTo("**직접** 쓴 요약");
    }
}
