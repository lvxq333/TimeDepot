package com.couple.service.user.service.impl;

import com.couple.common.exception.BadRequestException;
import com.couple.common.exception.BusinessException;
import com.couple.common.exception.ConflictException;
import com.couple.common.exception.NotFoundException;
import com.couple.common.exception.UnauthorizedException;
import com.couple.common.security.JwtTokenUtil;
import com.couple.common.security.CurrentUser;
import com.couple.service.auth.service.AuthSessionService;
import com.couple.service.notification.RealtimeNotificationEvent;
import com.couple.service.user.dto.PartnerRemarkUpdateRequest;
import com.couple.service.user.dto.UserBindRequest;
import com.couple.service.user.dto.UserLoginRequest;
import com.couple.service.user.dto.UserProfileUpdateRequest;
import com.couple.service.user.dto.UserRegisterRequest;
import com.couple.service.user.dto.UserUnbindRequest;
import com.couple.service.user.entity.CoupleBindRequestEntity;
import com.couple.service.user.entity.UserEntity;
import com.couple.service.user.mapper.CoupleBindRequestMapper;
import com.couple.service.user.mapper.UserMapper;
import com.couple.service.user.mapper.RecoveryQuestionMapper;
import com.couple.service.user.entity.RecoveryQuestionEntity;
import com.couple.service.user.service.UserService;
import com.couple.service.user.vo.EntitlementsResponse;
import com.couple.service.user.vo.CoupleBindRequestView;
import com.couple.service.user.vo.FeatureFlagsView;
import com.couple.service.user.vo.LimitsView;
import com.couple.service.user.vo.MembershipView;
import com.couple.service.user.vo.PartnerRemarkResponse;
import com.couple.service.user.vo.UsageView;
import com.couple.service.user.vo.UserBindResponse;
import com.couple.service.user.vo.UserLoginResponse;
import com.couple.service.user.vo.UserProfileResponse;
import com.couple.service.user.vo.UserProfileUpdateResponse;
import com.couple.service.user.vo.UserRegisterResponse;
import com.couple.service.user.vo.UserUnbindResponse;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 用户业务实现，负责账户认证、资料维护和伴侣关系绑定。 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserServiceImpl.class);

    private static final String STATUS_UNBOUND = "UNBOUND";
    private static final String STATUS_BOUND = "BOUND";
    private static final String REQUEST_PENDING = "PENDING";
    private static final String REQUEST_ACCEPTED = "ACCEPTED";
    private static final String REQUEST_REJECTED = "REJECTED";
    private static final String DEFAULT_PLAN = "FREE";
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int CODE_GENERATION_ATTEMPTS = 20;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final FeatureFlagsView DEFAULT_FLAGS = new FeatureFlagsView(false, true, false);
    private static final LimitsView DEFAULT_LIMITS = new LimitsView(30, 0);

    private final UserMapper userMapper;
    private final RecoveryQuestionMapper recoveryQuestionMapper;
    private final CoupleBindRequestMapper bindRequestMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;
    private final String jwtSecret;
    private final long jwtExpireSeconds;
    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    public UserServiceImpl(UserMapper userMapper,
                           CoupleBindRequestMapper bindRequestMapper,
                           RecoveryQuestionMapper recoveryQuestionMapper,
                           PasswordEncoder passwordEncoder,
                           AuthSessionService authSessionService,
                           @Value("${jwt.secret}") String jwtSecret,
                           @Value("${jwt.expire:86400}") long jwtExpireSeconds) {
        this.userMapper = userMapper;
        this.recoveryQuestionMapper = recoveryQuestionMapper;
        this.bindRequestMapper = bindRequestMapper;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
        this.jwtSecret = jwtSecret;
        this.jwtExpireSeconds = jwtExpireSeconds;
    }

    /** 创建账户，密码只保存 BCrypt 哈希，并为未绑定用户分配唯一配对码。 */
    @Override
    @Transactional
    public UserRegisterResponse register(UserRegisterRequest request) {
        if (request.recoveryQuestions() == null || request.recoveryQuestions().isEmpty()
                || request.recoveryQuestions().size() > 3) {
            throw new BadRequestException("请设置 1~3 个找回问题");
        }
        for (var item : request.recoveryQuestions()) {
            if (item == null || item.question() == null || item.question().isBlank()
                    || item.answer() == null || item.answer().trim().length() < 8) {
                throw new BadRequestException("找回答案至少需要 8 个字符");
            }
        }
        String username = request.username().trim();
        if (userMapper.selectByUsername(username) != null) {
            throw new ConflictException("用户名已存在");
        }

        String nickname = normalizeNickname(request.nickname(), username);
        LocalDateTime now = LocalDateTime.now();
        for (int attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
            UserEntity user = new UserEntity();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(request.password()));
            user.setNickname(nickname);
            user.setCoupleCode(generateCoupleCode());
            user.setRelationshipStatus(STATUS_UNBOUND);
            user.setMembershipPlan(DEFAULT_PLAN);
            user.setIsDeleted(0);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            try {
                if (userMapper.insert(user) != 1) {
                    throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "注册失败，请稍后重试");
                }
                for (int i = 0; i < request.recoveryQuestions().size(); i++) {
                    var item = request.recoveryQuestions().get(i);
                    RecoveryQuestionEntity recovery = new RecoveryQuestionEntity();
                    recovery.setUserId(user.getId());
                    recovery.setPosition(i + 1);
                    recovery.setQuestion(item.question().trim());
                    recovery.setAnswerHash(passwordEncoder.encode(item.answer().trim()));
                    if (recoveryQuestionMapper.insert(recovery) != 1) {
                        throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "保存找回问题失败");
                    }
                }
                return new UserRegisterResponse(
                        user.getId(), username, nickname, user.getCoupleCode(), now);
            } catch (DuplicateKeyException ex) {
                // 并发注册可能同时命中用户名或极低概率的配对码冲突，分别处理。
                if (userMapper.selectByUsername(username) != null) {
                    throw new ConflictException("用户名已存在");
                }
            }
        }
        throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "生成配对码失败，请稍后重试");
    }

    /** 校验账户密码并签发 JWT。 */
    @Override
    @Transactional
    public UserLoginResponse login(UserLoginRequest request) {
        UserEntity user = userMapper.selectByUsername(request.username().trim());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("用户名或密码错误");
        }

        UserEntity partner = findPartner(user);
        Duration tokenTtl = Duration.ofSeconds(jwtExpireSeconds);
        String sessionId = authSessionService.createSession(user.getId(), user.getUsername(), tokenTtl);
        String token = JwtTokenUtil.createToken(
                jwtSecret, user.getId(), user.getUsername(), sessionId, tokenTtl);
        return new UserLoginResponse(
                user.getId(), user.getUsername(), user.getNickname(), user.getCoupleCode(),
                token, jwtExpireSeconds,
                isBound(user), user.getPartnerId(), partner == null ? null : partner.getNickname(),
                user.getPartnerRemark(), membershipOf(user), DEFAULT_FLAGS, DEFAULT_LIMITS);
    }

    /** 撤销当前 JWT 对应的服务端会话。 */
    @Override
    public void logout(CurrentUser currentUser) {
        authSessionService.revokeCurrentSession(currentUser);
    }

    /** 根据对方配对码创建待处理申请，不在此阶段修改双方伴侣关系。 */
    @Override
    @Transactional
    public CoupleBindRequestView createBindRequest(Long userId, UserBindRequest request) {
        String coupleCode = request.coupleCode().trim().toUpperCase(Locale.ROOT);
        UserEntity current = requireUser(userId);
        UserEntity target = userMapper.selectByCoupleCode(coupleCode);
        if (target == null) {
            throw new NotFoundException("配对码无效");
        }
        if (Objects.equals(userId, target.getId())) {
            throw new BadRequestException("不能向自己发起绑定申请");
        }
        if (isBound(current)) {
            throw new ConflictException("当前用户已绑定伴侣");
        }
        if (isBound(target)) {
            throw new ConflictException("对方已绑定伴侣");
        }

        LocalDateTime now = LocalDateTime.now();
        CoupleBindRequestEntity bindRequest = new CoupleBindRequestEntity();
        bindRequest.setRequesterId(userId);
        bindRequest.setReceiverId(target.getId());
        bindRequest.setStatus(REQUEST_PENDING);
        bindRequest.setActivePairKey(activePairKey(userId, target.getId()));
        bindRequest.setActiveRequesterId(userId);
        bindRequest.setCreatedAt(now);
        bindRequest.setUpdatedAt(now);
        try {
            if (bindRequestMapper.insert(bindRequest) != 1) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "申请发送失败，请稍后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("你已有待处理申请，或双方之间已有待处理申请");
        }
        CoupleBindRequestView view = toBindRequestView(bindRequest, current, target);
        publish("BIND_REQUEST_CREATED", target.getId(), userId, "BIND_REQUEST", bindRequest.getId(),
                Map.of("requesterNickname", current.getNickname()));
        return view;
    }

    /** 查询当前用户收到的绑定申请，待处理申请优先。 */
    @Override
    @Transactional(readOnly = true)
    public List<CoupleBindRequestView> getReceivedBindRequests(Long userId) {
        requireUser(userId);
        return bindRequestMapper.selectReceived(userId).stream().map(this::toBindRequestView).toList();
    }

    /** 查询当前用户发出的绑定申请。 */
    @Override
    @Transactional(readOnly = true)
    public List<CoupleBindRequestView> getSentBindRequests(Long userId) {
        requireUser(userId);
        return bindRequestMapper.selectSent(userId).stream().map(this::toBindRequestView).toList();
    }

    /** 接收方同意申请后，在同一事务内完成双向绑定。 */
    @Override
    @Transactional
    public UserBindResponse acceptBindRequest(Long userId, Long requestId) {
        CoupleBindRequestEntity bindRequest = requirePendingRequestForReceiver(userId, requestId);
        Long requesterId = bindRequest.getRequesterId();
        Long firstId = Math.min(userId, requesterId);
        Long secondId = Math.max(userId, requesterId);
        List<UserEntity> lockedUsers = userMapper.selectPairForUpdate(firstId, secondId);
        if (lockedUsers.size() != 2) {
            throw new NotFoundException("申请用户不存在");
        }
        UserEntity requester = findLockedUser(lockedUsers, requesterId);
        UserEntity receiver = findLockedUser(lockedUsers, userId);
        if (isBound(requester)) {
            throw new ConflictException("申请方已绑定伴侣");
        }
        if (isBound(receiver)) {
            throw new ConflictException("当前用户已绑定伴侣");
        }

        LocalDateTime boundAt = LocalDateTime.now();
        if (bindRequestMapper.updateDecision(requestId, userId, REQUEST_ACCEPTED, boundAt) != 1) {
            throw new ConflictException("申请已被处理");
        }
        int requesterUpdated = bindOneUser(requesterId, userId, receiver.getNickname(), boundAt);
        int receiverUpdated = bindOneUser(userId, requesterId, requester.getNickname(), boundAt);
        if (requesterUpdated != 1 || receiverUpdated != 1) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "绑定失败，请稍后重试");
        }
        bindRequestMapper.cancelOtherPending(requestId, firstId, secondId, boundAt);
        publish("BIND_REQUEST_ACCEPTED", requesterId, userId, "BIND_REQUEST", requestId,
                Map.of("partnerNickname", receiver.getNickname()));
        return new UserBindResponse(
                "绑定申请已同意", requesterId, requester.getNickname(), requester.getNickname(), boundAt);
    }

    /** 接收方拒绝申请，仅改变申请状态，不修改用户关系。 */
    @Override
    @Transactional
    public CoupleBindRequestView rejectBindRequest(Long userId, Long requestId) {
        CoupleBindRequestEntity bindRequest = requirePendingRequestForReceiver(userId, requestId);
        LocalDateTime processedAt = LocalDateTime.now();
        if (bindRequestMapper.updateDecision(requestId, userId, REQUEST_REJECTED, processedAt) != 1) {
            throw new ConflictException("申请已被处理");
        }
        bindRequest.setStatus(REQUEST_REJECTED);
        bindRequest.setActivePairKey(null);
        bindRequest.setActiveRequesterId(null);
        bindRequest.setProcessedAt(processedAt);
        CoupleBindRequestView view = toBindRequestView(bindRequest);
        publish("BIND_REQUEST_REJECTED", bindRequest.getRequesterId(), userId,
                "BIND_REQUEST", requestId, Map.of());
        return view;
    }

    /** 查询当前用户、伴侣展示信息和默认权益。 */
    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        UserEntity user = requireUser(userId);
        UserEntity partner = findPartner(user);
        return new UserProfileResponse(
                user.getId(), user.getUsername(), user.getNickname(), user.getCoupleCode(), isBound(user),
                user.getPartnerId(), partner == null ? null : partner.getNickname(),
                user.getPartnerRemark(), user.getBoundAt(), membershipOf(user),
                DEFAULT_FLAGS, DEFAULT_LIMITS);
    }

    /** 修改当前用户昵称；空请求保持原值不变。 */
    @Override
    @Transactional
    public UserProfileUpdateResponse updateProfile(Long userId, UserProfileUpdateRequest request) {
        UserEntity user = requireUser(userId);
        if (request.nickname() == null) {
            return new UserProfileUpdateResponse(
                    user.getId(), user.getUsername(), user.getNickname(), user.getUpdatedAt());
        }
        String nickname = request.nickname().trim();
        if (nickname.isEmpty()) {
            throw new BadRequestException("昵称不能为空");
        }
        LocalDateTime updatedAt = LocalDateTime.now();
        int updated = userMapper.updateNickname(userId, nickname, updatedAt);
        if (updated != 1) {
            throw new NotFoundException("用户不存在");
        }
        return new UserProfileUpdateResponse(user.getId(), user.getUsername(), nickname, updatedAt);
    }

    /** 修改当前用户视角下的伴侣备注，不改变对方昵称。 */
    @Override
    @Transactional
    public PartnerRemarkResponse updatePartnerRemark(Long userId, PartnerRemarkUpdateRequest request) {
        UserEntity user = requireUser(userId);
        if (!isBound(user)) {
            throw new ConflictException("请先绑定伴侣");
        }
        UserEntity partner = requireUser(user.getPartnerId());
        String remark = request.partnerRemark().trim();
        LocalDateTime updatedAt = LocalDateTime.now();
        int updated = userMapper.updatePartnerRemark(userId, remark, updatedAt);
        if (updated != 1) {
            throw new NotFoundException("用户不存在");
        }
        return new PartnerRemarkResponse(
                partner.getId(), partner.getNickname(), remark, updatedAt);
    }

    /** 返回 Phase 1 免费版默认权益和初始使用量。 */
    @Override
    @Transactional(readOnly = true)
    public EntitlementsResponse getEntitlements(Long userId) {
        UserEntity user = requireUser(userId);
        return new EntitlementsResponse(
                membershipOf(user), DEFAULT_FLAGS, DEFAULT_LIMITS, new UsageView(0, 0));
    }

    /** 同一事务内解除双方关系，保留历史数据并生成新的配对码。 */
    @Override
    @Transactional
    public UserUnbindResponse unbind(Long userId, UserUnbindRequest request) {
        if (!request.confirm()) {
            throw new BadRequestException("必须确认解绑操作");
        }
        UserEntity current = requireUser(userId);
        if (!isBound(current)) {
            throw new ConflictException("当前用户未绑定伴侣");
        }
        Long partnerId = current.getPartnerId();
        List<UserEntity> lockedUsers = userMapper.selectPairForUpdate(
                Math.min(userId, partnerId), Math.max(userId, partnerId));
        if (lockedUsers.size() != 2) {
            throw new ConflictException("伴侣关系状态异常，请刷新后重试");
        }
        UserEntity lockedCurrent = findLockedUser(lockedUsers, userId);
        UserEntity partner = findLockedUser(lockedUsers, partnerId);
        if (!isBound(lockedCurrent) || !isBound(partner)
                || !Objects.equals(lockedCurrent.getPartnerId(), partnerId)
                || !Objects.equals(partner.getPartnerId(), userId)) {
            throw new ConflictException("伴侣关系已变化，请刷新后重试");
        }

        String currentCode = freshCoupleCode();
        String partnerCode = freshCoupleCode();
        while (currentCode.equals(partnerCode)) partnerCode = freshCoupleCode();
        LocalDateTime unboundAt = LocalDateTime.now();
        try {
            if (userMapper.updateUnbinding(userId, partnerId, currentCode, unboundAt) != 1
                    || userMapper.updateUnbinding(partnerId, userId, partnerCode, unboundAt) != 1) {
                throw new ConflictException("伴侣关系已变化，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("配对码生成冲突，请重试解绑");
        }
        bindRequestMapper.cancelPendingForPair(userId, partnerId, unboundAt);
        LOG.info("用户关系解绑: userId={}, partnerId={}, reason={}", userId, partnerId,
                request.reason() == null ? "" : request.reason().replaceAll("[\\r\\n]", " "));
        publish("PARTNER_UNBOUND", partnerId, userId, "USER", userId, Map.of());
        return new UserUnbindResponse("解绑成功", unboundAt, currentCode);
    }

    private String freshCoupleCode() {
        for (int attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = generateCoupleCode();
            if (userMapper.selectByCoupleCode(code) == null) return code;
        }
        throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "生成配对码失败，请稍后重试");
    }

    private int bindOneUser(Long userId, Long partnerId, String partnerRemark,
                            LocalDateTime boundAt) {
        return userMapper.updateBinding(userId, partnerId, partnerRemark, boundAt);
    }

    private CoupleBindRequestEntity requirePendingRequestForReceiver(Long userId, Long requestId) {
        CoupleBindRequestEntity bindRequest = bindRequestMapper.selectByIdForUpdate(requestId);
        if (bindRequest == null || !Objects.equals(bindRequest.getReceiverId(), userId)) {
            throw new NotFoundException("绑定申请不存在");
        }
        if (!REQUEST_PENDING.equals(bindRequest.getStatus())) {
            throw new ConflictException("申请已被处理");
        }
        return bindRequest;
    }

    private UserEntity findLockedUser(List<UserEntity> users, Long userId) {
        return users.stream()
                .filter(user -> Objects.equals(user.getId(), userId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("用户不存在"));
    }

    private CoupleBindRequestView toBindRequestView(CoupleBindRequestEntity bindRequest) {
        UserEntity requester = userMapper.selectById(bindRequest.getRequesterId());
        UserEntity receiver = userMapper.selectById(bindRequest.getReceiverId());
        if (requester == null || receiver == null) {
            throw new NotFoundException("申请关联用户不存在");
        }
        return toBindRequestView(bindRequest, requester, receiver);
    }

    private CoupleBindRequestView toBindRequestView(CoupleBindRequestEntity bindRequest,
                                                     UserEntity requester,
                                                     UserEntity receiver) {
        return new CoupleBindRequestView(
                bindRequest.getId(), requester.getId(), requester.getUsername(), requester.getNickname(),
                receiver.getId(), receiver.getUsername(), receiver.getNickname(), bindRequest.getStatus(),
                bindRequest.getCreatedAt(), bindRequest.getProcessedAt());
    }

    private String activePairKey(Long firstUserId, Long secondUserId) {
        return Math.min(firstUserId, secondUserId) + ":" + Math.max(firstUserId, secondUserId);
    }

    private UserEntity requireUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new UnauthorizedException("用户不存在或已注销");
        }
        return user;
    }

    private UserEntity findPartner(UserEntity user) {
        return user.getPartnerId() == null ? null : userMapper.selectById(user.getPartnerId());
    }

    private boolean isBound(UserEntity user) {
        return user.getPartnerId() != null && STATUS_BOUND.equals(user.getRelationshipStatus());
    }

    private MembershipView membershipOf(UserEntity user) {
        String plan = user.getMembershipPlan() == null ? DEFAULT_PLAN : user.getMembershipPlan();
        return new MembershipView(plan, "ACTIVE", user.getMembershipExpireAt());
    }

    private String normalizeNickname(String nickname, String username) {
        if (nickname == null || nickname.isBlank()) {
            return username;
        }
        return nickname.trim();
    }

    private String generateCoupleCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    private void publish(String type, Long recipientId, Long actorId, String aggregateType,
                         Object aggregateId, Map<String, Object> payload) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(RealtimeNotificationEvent.create(
                    type, recipientId, actorId, aggregateType, aggregateId, payload));
        }
    }
}
