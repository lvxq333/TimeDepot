package com.couple.service.letter.service;

import com.couple.service.letter.dto.LetterCreateRequest;
import com.couple.service.letter.dto.LetterUpdateRequest;
import com.couple.service.letter.vo.LetterDetailView;
import com.couple.service.letter.vo.LetterOperationResponse;
import com.couple.service.letter.vo.LetterPageResponse;
import com.couple.service.letter.vo.LetterThreadView;

/** 情绪信核心业务，包括草稿、冷静期、发送、已读和解决状态流转。 */
public interface LetterService {
    LetterOperationResponse create(Long userId, LetterCreateRequest request);
    LetterOperationResponse update(Long userId, Long letterId, LetterUpdateRequest request);
    LetterOperationResponse submit(Long userId, Long letterId);
    LetterOperationResponse revert(Long userId, Long letterId);
    LetterOperationResponse send(Long userId, Long letterId);
    LetterPageResponse list(Long userId, int page, int pageSize, String status,
                            String direction, String emotionTag);
    LetterDetailView detail(Long userId, Long letterId);
    LetterThreadView thread(Long userId, Long letterId);
    LetterOperationResponse read(Long userId, Long letterId);
    LetterOperationResponse resolve(Long userId, Long letterId);
    LetterOperationResponse delete(Long userId, Long letterId);
}
