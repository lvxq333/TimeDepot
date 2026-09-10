package com.couple.api.controller;

import com.couple.api.annotation.NoAuth;
import com.couple.common.response.ApiResponse;
import com.couple.common.security.UserContext;
import com.couple.service.user.dto.PartnerRemarkUpdateRequest;
import com.couple.service.user.dto.UserBindRequest;
import com.couple.service.user.dto.UserLoginRequest;
import com.couple.service.user.dto.UserProfileUpdateRequest;
import com.couple.service.user.dto.UserRegisterRequest;
import com.couple.service.user.dto.UserUnbindRequest;
import com.couple.service.user.service.UserService;
import com.couple.service.user.vo.EntitlementsResponse;
import com.couple.service.user.vo.CoupleBindRequestView;
import com.couple.service.user.vo.PartnerRemarkResponse;
import com.couple.service.user.vo.UserBindResponse;
import com.couple.service.user.vo.UserLoginResponse;
import com.couple.service.user.vo.UserProfileResponse;
import com.couple.service.user.vo.UserProfileUpdateResponse;
import com.couple.service.user.vo.UserRegisterResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用户账户、资料、伴侣关系和权益接口。 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 注册新用户并返回配对码。 */
    @NoAuth
    @PostMapping("/register")
    public ApiResponse<UserRegisterResponse> register(
            @Valid @RequestBody UserRegisterRequest request) {
        return ApiResponse.success(userService.register(request));
    }

    /** 校验用户名和密码并签发 JWT。 */
    @NoAuth
    @PostMapping("/login")
    public ApiResponse<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest request) {
        return ApiResponse.success(userService.login(request));
    }

    /** 使用对方配对码发送绑定申请，发送后不会立即建立关系。 */
    @PostMapping("/bind-requests")
    public ResponseEntity<ApiResponse<CoupleBindRequestView>> createBindRequest(
            @Valid @RequestBody UserBindRequest request) {
        CoupleBindRequestView result = userService.createBindRequest(
                UserContext.requireUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    /** 查询当前用户收到的绑定申请。 */
    @GetMapping("/bind-requests/received")
    public ApiResponse<List<CoupleBindRequestView>> receivedBindRequests() {
        return ApiResponse.success(userService.getReceivedBindRequests(UserContext.requireUserId()));
    }

    /** 查询当前用户发出的绑定申请。 */
    @GetMapping("/bind-requests/sent")
    public ApiResponse<List<CoupleBindRequestView>> sentBindRequests() {
        return ApiResponse.success(userService.getSentBindRequests(UserContext.requireUserId()));
    }

    /** 接收方同意申请并正式建立双向伴侣关系。 */
    @PostMapping("/bind-requests/{requestId}/accept")
    public ApiResponse<UserBindResponse> acceptBindRequest(@PathVariable Long requestId) {
        return ApiResponse.success(userService.acceptBindRequest(
                UserContext.requireUserId(), requestId));
    }

    /** 接收方拒绝申请，不改变双方伴侣关系。 */
    @PostMapping("/bind-requests/{requestId}/reject")
    public ApiResponse<CoupleBindRequestView> rejectBindRequest(@PathVariable Long requestId) {
        return ApiResponse.success(userService.rejectBindRequest(
                UserContext.requireUserId(), requestId));
    }

    /** 查询当前登录用户的完整资料。 */
    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> profile() {
        return ApiResponse.success(userService.getProfile(UserContext.requireUserId()));
    }

    /** 修改当前登录用户的昵称。 */
    @PutMapping("/profile")
    public ApiResponse<UserProfileUpdateResponse> updateProfile(
            @Valid @RequestBody UserProfileUpdateRequest request) {
        return ApiResponse.success(
                userService.updateProfile(UserContext.requireUserId(), request));
    }

    /** 修改当前用户视角下的伴侣备注。 */
    @PutMapping("/partner/remark")
    public ApiResponse<PartnerRemarkResponse> updatePartnerRemark(
            @Valid @RequestBody PartnerRemarkUpdateRequest request) {
        return ApiResponse.success(
                userService.updatePartnerRemark(UserContext.requireUserId(), request));
    }

    /** 返回当前会员、功能开关、配额和使用量。 */
    @GetMapping("/entitlements")
    public ApiResponse<EntitlementsResponse> entitlements() {
        return ApiResponse.success(userService.getEntitlements(UserContext.requireUserId()));
    }

    /** 保留解绑接口；Phase 1 统一返回 HTTP 501。 */
    @PostMapping("/unbind")
    public ApiResponse<Void> unbind(@Valid @RequestBody UserUnbindRequest request) {
        userService.unbind(UserContext.requireUserId(), request);
        return ApiResponse.success();
    }
}
