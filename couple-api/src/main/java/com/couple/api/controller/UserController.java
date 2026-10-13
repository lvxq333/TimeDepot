package com.couple.api.controller;

import com.couple.api.annotation.NoAuth;
import com.couple.common.response.ApiResponse;
import com.couple.common.security.UserContext;
import com.couple.api.websocket.WebSocketTicketService;
import com.couple.service.auth.service.SecurityAttemptLimiter;
import com.couple.service.user.dto.PartnerRemarkUpdateRequest;
import com.couple.service.user.dto.UserBindRequest;
import com.couple.service.user.dto.UserLoginRequest;
import com.couple.service.user.dto.UserProfileUpdateRequest;
import com.couple.service.user.dto.UserRegisterRequest;
import com.couple.service.user.dto.UserUnbindRequest;
import com.couple.service.user.dto.PasswordRecoveryRequest;
import com.couple.service.user.dto.RecoverySetupRequest;
import com.couple.service.user.dto.AccountDeletionRequest;
import com.couple.service.user.service.PasswordRecoveryService;
import com.couple.service.user.service.AccountDeletionService;
import com.couple.service.user.vo.UserUnbindResponse;
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
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.time.Duration;

/** 用户账户、资料、伴侣关系和权益接口。 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final AccountDeletionService accountDeletionService;
    private final SecurityAttemptLimiter attemptLimiter;
    private final WebSocketTicketService ticketService;

    public UserController(UserService userService, PasswordRecoveryService passwordRecoveryService,
                          AccountDeletionService accountDeletionService,
                          SecurityAttemptLimiter attemptLimiter,
                          WebSocketTicketService ticketService) {
        this.userService = userService;
        this.passwordRecoveryService = passwordRecoveryService;
        this.accountDeletionService = accountDeletionService;
        this.attemptLimiter = attemptLimiter;
        this.ticketService = ticketService;
    }

    /** 注册新用户并返回配对码。 */
    @NoAuth
    @PostMapping("/register")
    public ApiResponse<UserRegisterResponse> register(
            @Valid @RequestBody UserRegisterRequest request, HttpServletRequest servletRequest) {
        attemptLimiter.check("register-ip", clientAddress(servletRequest), 10, Duration.ofHours(1));
        return ApiResponse.success(userService.register(request));
    }

    /** 校验用户名和密码并签发 JWT。 */
    @NoAuth
    @PostMapping("/login")
    public ApiResponse<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest request,
                                                HttpServletRequest servletRequest) {
        String ip = clientAddress(servletRequest);
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        // 同时限制单一来源总量和针对同一账号的尝试，避免批量撞库和定向猜密码。
        attemptLimiter.check("login-ip", ip, 100, Duration.ofMinutes(15));
        attemptLimiter.check("login-account-ip", username + ":" + ip, 20, Duration.ofMinutes(15));
        return ApiResponse.success(userService.login(request));
    }

    /** 按来源及用户名限流后，返回可用于找回的自定义问题。 */
    @NoAuth
    @GetMapping("/recovery/questions")
    public ApiResponse<List<String>> recoveryQuestions(@RequestParam String username,
                                                        HttpServletRequest servletRequest) {
        attemptLimiter.check("recovery-questions-ip", clientAddress(servletRequest),
                60, Duration.ofHours(1));
        return ApiResponse.success(passwordRecoveryService.questions(username));
    }

    /** 按来源及用户名限流后核对全部答案并重置密码。 */
    @NoAuth
    @PostMapping("/recovery/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordRecoveryRequest request,
                                              HttpServletRequest servletRequest) {
        attemptLimiter.check("recovery-reset-ip", clientAddress(servletRequest),
                20, Duration.ofHours(1));
        passwordRecoveryService.reset(request);
        return ResponseEntity.noContent().build();
    }

    /** 已登录用户查询是否需要补设找回问题。 */
    @GetMapping("/recovery/setup-status")
    public ApiResponse<Map<String, Boolean>> recoverySetupStatus() {
        return ApiResponse.success(Map.of("hasQuestions",
                passwordRecoveryService.hasQuestions(UserContext.requireUserId())));
    }

    /** 老账号输入当前密码后首次补设找回问题。 */
    @PostMapping("/recovery/setup")
    public ResponseEntity<Void> setupRecovery(@Valid @RequestBody RecoverySetupRequest request) {
        passwordRecoveryService.setupForExistingAccount(UserContext.requireUserId(), request);
        return ResponseEntity.noContent().build();
    }

    /** 用受保护的 HTTP 请求为每次 WebSocket 连接签发一次性票据。 */
    @PostMapping("/ws-ticket")
    public ApiResponse<Map<String, String>> webSocketTicket() {
        return ApiResponse.success(Map.of("ticket", ticketService.issue(UserContext.requireUser())));
    }

    /** 二次验证密码后注销账号；双方均注销时清理共有内容。 */
    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(@Valid @RequestBody AccountDeletionRequest request) {
        accountDeletionService.delete(UserContext.requireUserId(), request);
        return ResponseEntity.noContent().build();
    }

    /** 撤销当前 Token 对应的服务端会话。 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        userService.logout(UserContext.requireUser());
        return ResponseEntity.noContent().build();
    }

    /** 使用对方配对码发送绑定申请，发送后不会立即建立关系。 */
    @PostMapping("/bind-requests")
    public ResponseEntity<ApiResponse<CoupleBindRequestView>> createBindRequest(
            @Valid @RequestBody UserBindRequest request) {
        Long userId = UserContext.requireUserId();
        attemptLimiter.check("bind-user", userId.toString(), 10, Duration.ofHours(1));
        CoupleBindRequestView result = userService.createBindRequest(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, result));
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

    /** 解除双方伴侣关系，保留历史数据。 */
    @PostMapping("/unbind")
    public ApiResponse<UserUnbindResponse> unbind(@Valid @RequestBody UserUnbindRequest request) {
        return ApiResponse.success(userService.unbind(UserContext.requireUserId(), request));
    }

    /** 读取 Nginx 覆盖的真实来源地址；直连开发环境使用连接地址。 */
    private String clientAddress(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp;
    }
}
