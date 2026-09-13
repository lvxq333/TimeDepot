package com.couple.service.memo.service;

import com.couple.service.memo.vo.MemoEventDetailView;
import com.couple.service.memo.vo.MemoEventPageResponse;

/** 按情绪信线程浏览共同备忘事件。 */
public interface MemoEventService {
    MemoEventPageResponse list(Long userId, int page, int pageSize);
    MemoEventDetailView detail(Long userId, String threadId);
}
