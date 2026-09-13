package com.couple.api.controller;

import com.couple.common.response.ApiResponse;
import com.couple.common.security.UserContext;
import com.couple.service.annotation.dto.AnnotationCreateRequest;
import com.couple.service.annotation.service.AnnotationService;
import com.couple.service.annotation.vo.AnnotationDeleteResponse;
import com.couple.service.annotation.vo.AnnotationListResponse;
import com.couple.service.annotation.vo.AnnotationView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 已送达情绪信的原文高亮批注接口。 */
@RestController
@RequestMapping("/api/annotation")
public class AnnotationController {
    private final AnnotationService annotationService;

    public AnnotationController(AnnotationService annotationService) {
        this.annotationService = annotationService;
    }

    /** 参与任意一方都可以对已送达信件的精确原文片段添加批注。 */
    @PostMapping("/create")
    public ApiResponse<AnnotationView> create(
            @Valid @RequestBody AnnotationCreateRequest request) {
        return ApiResponse.success(annotationService.create(UserContext.requireUserId(), request));
    }

    /** 按原文位置和创建时间查询一封信的全部有效批注。 */
    @GetMapping("/list/{letterId}")
    public ApiResponse<AnnotationListResponse> list(@PathVariable Long letterId) {
        return ApiResponse.success(annotationService.list(UserContext.requireUserId(), letterId));
    }

    /** 作者逻辑删除自己的批注。 */
    @DeleteMapping("/{annotationId}")
    public ApiResponse<AnnotationDeleteResponse> delete(@PathVariable Long annotationId) {
        return ApiResponse.success(annotationService.delete(
                UserContext.requireUserId(), annotationId));
    }
}
