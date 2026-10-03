package kr.antimony.blog.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 글 카테고리. DB와 API에는 이름(DAILY 등)으로 저장하고 주고받는다.
@Getter
@RequiredArgsConstructor
public enum Category {
    DAILY("일상"),
    STUDY("학습"),
    TREE("시냇가에 심은 나무");

    private final String label;
}
