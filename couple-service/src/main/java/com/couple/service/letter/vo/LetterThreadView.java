package com.couple.service.letter.vo;

import java.util.List;

/** 同一话题下当前用户可见的信件时间线。 */
public record LetterThreadView(String threadId,
                               String title,
                               List<LetterDetailView> letters) {
}
