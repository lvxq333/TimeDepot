package com.couple.api.controller;

import com.couple.common.response.ApiResponse;
import com.couple.common.security.UserContext;
import com.couple.service.letter.dto.LetterCreateRequest;
import com.couple.service.letter.dto.LetterUpdateRequest;
import com.couple.service.letter.service.LetterService;
import com.couple.service.letter.vo.LetterDetailView;
import com.couple.service.letter.vo.LetterOperationResponse;
import com.couple.service.letter.vo.LetterPageResponse;
import com.couple.service.letter.vo.LetterThreadView;
import com.couple.service.letter.vo.LetterThreadPageResponse;
import com.couple.service.memo.service.MemoService;
import com.couple.service.memo.vo.ConsensusMemoView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 情绪信草稿、发送、查询、已读和解决接口。 */
@RestController
@RequestMapping("/api/letter")
public class LetterController {

    private final LetterService letterService;
    private final MemoService memoService;

    public LetterController(LetterService letterService, MemoService memoService) {
        this.letterService = letterService;
        this.memoService = memoService;
    }

    /** 创建只对发送方可见的情绪信草稿。 */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<LetterOperationResponse>> create(
            @Valid @RequestBody LetterCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED, letterService.create(UserContext.requireUserId(), request)));
    }

    /** 修改当前用户创建的草稿。 */
    @PutMapping("/{letterId}")
    public ApiResponse<LetterOperationResponse> update(
            @PathVariable Long letterId,
            @Valid @RequestBody LetterUpdateRequest request) {
        return ApiResponse.success(letterService.update(UserContext.requireUserId(), letterId, request));
    }

    /** 提交草稿并开始冷静期倒计时。 */
    @PostMapping("/submit/{letterId}")
    public ApiResponse<LetterOperationResponse> submit(@PathVariable Long letterId) {
        return ApiResponse.success(letterService.submit(UserContext.requireUserId(), letterId));
    }

    /** 在冷静期内将信件退回草稿。 */
    @PostMapping("/revert/{letterId}")
    public ApiResponse<LetterOperationResponse> revert(@PathVariable Long letterId) {
        return ApiResponse.success(letterService.revert(UserContext.requireUserId(), letterId));
    }

    /** 冷静期结束后确认发送。 */
    @PostMapping("/send/{letterId}")
    public ApiResponse<LetterOperationResponse> send(@PathVariable Long letterId) {
        return ApiResponse.success(letterService.send(UserContext.requireUserId(), letterId));
    }

    /** 查询当前用户可见的情绪信。 */
    @GetMapping("/list")
    public ApiResponse<LetterPageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String emotionTag) {
        return ApiResponse.success(letterService.list(UserContext.requireUserId(), page, pageSize,
                status, direction, emotionTag));
    }

    /** 按事件分页返回信箱摘要，不加载其他事件的信件正文。 */
    @GetMapping("/threads")
    public ApiResponse<LetterThreadPageResponse> threads(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String emotionTag,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(letterService.listThreads(UserContext.requireUserId(), page, pageSize,
                status, direction, emotionTag, keyword, startDate, endDate));
    }

    /** 查看信件详情；接收方只能查看已经发送给自己的信件。 */
    @GetMapping("/{letterId}")
    public ApiResponse<LetterDetailView> detail(@PathVariable Long letterId) {
        return ApiResponse.success(letterService.detail(UserContext.requireUserId(), letterId));
    }

    /** 查询当前用户可见的同话题信件时间线。 */
    @GetMapping("/{letterId}/thread")
    public ApiResponse<LetterThreadView> thread(@PathVariable Long letterId) {
        return ApiResponse.success(letterService.thread(UserContext.requireUserId(), letterId));
    }

    /** 通过线程中的任意一封可见信件查询该事件的共识备忘录。 */
    @GetMapping("/{letterId}/memo")
    public ApiResponse<ConsensusMemoView> memo(@PathVariable Long letterId) {
        return ApiResponse.success(memoService.getByLetter(UserContext.requireUserId(), letterId));
    }

    /** 当前用户是该信接收方时，首次打开后标记已读。 */
    @PostMapping("/read/{letterId}")
    public ApiResponse<LetterOperationResponse> read(@PathVariable Long letterId) {
        return ApiResponse.success(letterService.read(UserContext.requireUserId(), letterId));
    }

    /** 由该信接收方将单封信标记为已处理；整个线程的结束以备忘录双方确认为准。 */
    @PostMapping("/resolve/{letterId}")
    public ApiResponse<LetterOperationResponse> resolve(@PathVariable Long letterId) {
        return ApiResponse.success(letterService.resolve(UserContext.requireUserId(), letterId));
    }

    /** 逻辑删除当前用户创建的草稿。 */
    @DeleteMapping("/{letterId}")
    public ApiResponse<LetterOperationResponse> delete(@PathVariable Long letterId) {
        return ApiResponse.success(letterService.delete(UserContext.requireUserId(), letterId));
    }
}
