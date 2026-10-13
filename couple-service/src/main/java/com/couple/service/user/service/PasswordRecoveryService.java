package com.couple.service.user.service;

import com.couple.common.exception.BusinessException;
import com.couple.common.exception.UnauthorizedException;
import com.couple.service.auth.service.AuthSessionService;
import com.couple.service.auth.service.SecurityAttemptLimiter;
import com.couple.service.user.dto.PasswordRecoveryRequest;
import com.couple.service.user.dto.RecoverySetupRequest;
import com.couple.service.user.entity.RecoveryQuestionEntity;
import com.couple.service.user.entity.UserEntity;
import com.couple.service.user.mapper.RecoveryQuestionMapper;
import com.couple.service.user.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/** 通过注册时设置的全部问答找回密码；Redis 不可用时拒绝找回，避免绕过次数限制。 */
@Service
public class PasswordRecoveryService {
    private final UserMapper userMapper;
    private final RecoveryQuestionMapper questionMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;
    private final SecurityAttemptLimiter attemptLimiter;

    /** 注入用户数据和统一限流器；找回问答与其他敏感操作共享 Redis 限流实现。 */
    public PasswordRecoveryService(UserMapper userMapper, RecoveryQuestionMapper questionMapper,
                                   PasswordEncoder passwordEncoder, AuthSessionService authSessionService,
                                   SecurityAttemptLimiter attemptLimiter) {
        this.userMapper = userMapper;
        this.questionMapper = questionMapper;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
        this.attemptLimiter = attemptLimiter;
    }

    /** 查询找回问题；未知账号使用固定提示，避免直接暴露账号存在性。 */
    @Transactional(readOnly = true)
    public List<String> questions(String username) {
        limit("questions", username, 20);
        UserEntity user = userMapper.selectByUsername(username.trim());
        if (user == null) return List.of("请输入注册时设置的找回问题答案");
        List<String> questions = questionMapper.selectByUserId(user.getId()).stream()
                .map(RecoveryQuestionEntity::getQuestion).toList();
        return questions.isEmpty() ? List.of("该账号尚未设置找回问题") : questions;
    }

    /** 核对全部答案后重置密码，并撤销该账号的全部登录会话。 */
    @Transactional
    public void reset(PasswordRecoveryRequest request) {
        limit("reset", request.username(), 5);
        UserEntity user = userMapper.selectByUsernameForUpdate(request.username().trim());
        if (user == null) throw new UnauthorizedException("用户名或找回答案不正确");
        List<RecoveryQuestionEntity> questions = questionMapper.selectByUserId(user.getId());
        if (questions.isEmpty() || request.answers() == null
                || request.answers().size() != questions.size()) {
            throw new UnauthorizedException("用户名或找回答案不正确");
        }
        boolean matched = true;
        for (int i = 0; i < questions.size(); i++) {
            String answer = request.answers().get(i);
            matched &= answer != null && passwordEncoder.matches(answer.trim(), questions.get(i).getAnswerHash());
        }
        if (!matched) throw new UnauthorizedException("用户名或找回答案不正确");
        if (userMapper.updatePassword(user.getId(), passwordEncoder.encode(request.newPassword())) != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "账号状态已变化，请重试");
        }
        authSessionService.revokeAllSessions(user.getId());
    }

    /** 返回当前账号是否已有找回问答，供已登录的老用户显示补设入口。 */
    @Transactional(readOnly = true)
    public boolean hasQuestions(Long userId) {
        return !questionMapper.selectByUserId(userId).isEmpty();
    }

    /** 只允许尚无问答的老账号在验证当前密码后首次设置，不能覆盖原问答。 */
    @Transactional
    public void setupForExistingAccount(Long userId, RecoverySetupRequest request) {
        UserEntity user = userMapper.selectByIdForUpdate(userId);
        if (user == null) throw new UnauthorizedException("账号不存在或已注销");
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new UnauthorizedException("当前密码错误");
        }
        if (!questionMapper.selectByUserId(userId).isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "找回问题已设置");
        }
        for (int i = 0; i < request.questions().size(); i++) {
            var item = request.questions().get(i);
            RecoveryQuestionEntity entity = new RecoveryQuestionEntity();
            entity.setUserId(userId);
            entity.setPosition(i + 1);
            entity.setQuestion(item.question().trim());
            // 与注册流程一致，只保存 BCrypt 哈希，不保存答案明文。
            entity.setAnswerHash(passwordEncoder.encode(item.answer().trim()));
            if (questionMapper.insert(entity) != 1) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "保存找回问题失败");
            }
        }
    }

    /** 找回流程保留按用户名的限制，并改为原子计数以覆盖并发尝试。 */
    private void limit(String action, String username, int maximum) {
        attemptLimiter.check("recovery-" + action, username.trim().toLowerCase(),
                maximum, Duration.ofHours(1));
    }
}
