package com.couple.service.memo.vo;

import com.couple.service.letter.vo.LetterDetailView;

import java.util.List;

/** 一个共同备忘事件的时间线、共识正文和承诺信息。 */
public record MemoEventDetailView(MemoEventSummaryView event,
                                  List<LetterDetailView> letters,
                                  ConsensusMemoView memo) {
}
