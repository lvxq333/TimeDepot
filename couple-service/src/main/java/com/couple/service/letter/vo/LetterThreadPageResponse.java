package com.couple.service.letter.vo;

import java.util.List;

public record LetterThreadPageResponse(List<LetterThreadSummaryView> records,
                                       long total,
                                       int page,
                                       int pageSize) {
}
