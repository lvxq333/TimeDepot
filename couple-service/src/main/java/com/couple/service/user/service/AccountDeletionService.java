package com.couple.service.user.service;

import com.couple.common.exception.ConflictException;
import com.couple.common.exception.BadRequestException;
import com.couple.common.exception.NotFoundException;
import com.couple.common.exception.UnauthorizedException;
import com.couple.service.auth.service.AuthSessionService;
import com.couple.service.user.dto.AccountDeletionRequest;
import com.couple.service.user.entity.UserEntity;
import com.couple.service.user.mapper.AccountDeletionMapper;
import com.couple.service.user.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 注销账号；共有信件只在双方账号均已注销后清理。 */
@Service
public class AccountDeletionService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final UserMapper userMapper;
    private final AccountDeletionMapper deletionMapper;
    private final AuthSessionService authSessionService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public AccountDeletionService(UserMapper userMapper, AccountDeletionMapper deletionMapper,
                                  AuthSessionService authSessionService, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.deletionMapper = deletionMapper;
        this.authSessionService = authSessionService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void delete(Long userId, AccountDeletionRequest request) {
        if (request == null || !request.confirm() || request.password() == null || request.password().isBlank()) {
            throw new BadRequestException("请确认注销并输入当前密码");
        }
        UserEntity snapshot = userMapper.selectById(userId);
        if (snapshot == null) throw new NotFoundException("账号不存在");
        Long partnerId = snapshot.getPartnerId();
        List<UserEntity> locked = partnerId == null ? List.of(userMapper.selectByIdForUpdate(userId))
                : userMapper.selectPairForUpdate(Math.min(userId, partnerId), Math.max(userId, partnerId));
        UserEntity user = locked.stream().filter(item -> Objects.equals(item.getId(), userId))
                .findFirst().orElseThrow(() -> new NotFoundException("账号不存在"));
        if (!Objects.equals(partnerId, user.getPartnerId())) {
            throw new ConflictException("伴侣关系已变化，请刷新后重试");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("当前密码错误");
        }
        if (partnerId != null) {
            if (locked.size() != 2 || !Objects.equals(locked.stream()
                    .filter(item -> Objects.equals(item.getId(), partnerId)).findFirst()
                    .orElseThrow().getPartnerId(), userId)) {
                throw new ConflictException("伴侣关系已变化，请刷新后重试");
            }
            String code = freshCode();
            if (userMapper.detachDeletedPartner(partnerId, userId, code) != 1) {
                throw new ConflictException("伴侣关系已变化，请刷新后重试");
            }
        }
        deletionMapper.deleteNotifications(userId);
        deletionMapper.deleteBindRequests(userId);
        deletionMapper.deleteRecoveryQuestions(userId);
        authSessionService.revokeAllSessions(userId);
        deletionMapper.deleteSessions(userId);
        if (userMapper.markAccountDeleted(userId, passwordEncoder.encode(UUID.randomUUID().toString())) != 1) {
            throw new ConflictException("账号状态已变化，请重试");
        }
        deletionMapper.deleteCommitmentRevisions(userId);
        deletionMapper.deleteCommitments(userId);
        deletionMapper.deleteMemoRevisions(userId);
        deletionMapper.deleteMemos(userId);
        deletionMapper.deleteAnnotations(userId);
        deletionMapper.deleteVisibility(userId);
        deletionMapper.deleteLogs(userId);
        deletionMapper.deleteThreads(userId);
        deletionMapper.deleteLetters(userId);
    }

    private String freshCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder(6);
            for (int i = 0; i < 6; i++) code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            if (userMapper.selectByCoupleCode(code.toString()) == null) return code.toString();
        }
        throw new ConflictException("配对码生成失败，请重试");
    }
}
