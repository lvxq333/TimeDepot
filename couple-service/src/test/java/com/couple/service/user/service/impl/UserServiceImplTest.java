package com.couple.service.user.service.impl;

import com.couple.common.exception.BadRequestException;
import com.couple.common.exception.ConflictException;
import com.couple.common.exception.UnauthorizedException;
import com.couple.common.security.CurrentUser;
import com.couple.service.auth.service.AuthSessionService;
import com.couple.service.user.dto.UserBindRequest;
import com.couple.service.user.dto.UserLoginRequest;
import com.couple.service.user.dto.UserProfileUpdateRequest;
import com.couple.service.user.dto.UserRegisterRequest;
import com.couple.service.user.dto.RecoveryQuestionRequest;
import com.couple.service.user.dto.UserUnbindRequest;
import com.couple.service.user.entity.CoupleBindRequestEntity;
import com.couple.service.user.entity.UserEntity;
import com.couple.service.user.mapper.CoupleBindRequestMapper;
import com.couple.service.user.mapper.UserMapper;
import com.couple.service.user.mapper.RecoveryQuestionMapper;
import com.couple.service.user.vo.CoupleBindRequestView;
import com.couple.service.user.vo.UserBindResponse;
import com.couple.service.user.vo.UserRegisterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 用户核心业务的单元测试，不依赖 Spring 容器、数据库或动态代理库。 */
class UserServiceImplTest {

    private static final String JWT_SECRET = "12345678901234567890123456789012";

    private StubUserMapper mapperStub;
    private StubBindRequestMapper bindRequestMapperStub;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        mapperStub = new StubUserMapper();
        UserMapper mapper = (UserMapper) Proxy.newProxyInstance(
                UserMapper.class.getClassLoader(), new Class<?>[]{UserMapper.class}, mapperStub);
        bindRequestMapperStub = new StubBindRequestMapper();
        CoupleBindRequestMapper bindRequestMapper = (CoupleBindRequestMapper) Proxy.newProxyInstance(
                CoupleBindRequestMapper.class.getClassLoader(),
                new Class<?>[]{CoupleBindRequestMapper.class}, bindRequestMapperStub);
        RecoveryQuestionMapper recoveryQuestionMapper = (RecoveryQuestionMapper) Proxy.newProxyInstance(
                RecoveryQuestionMapper.class.getClassLoader(), new Class<?>[]{RecoveryQuestionMapper.class},
                (proxy, method, args) -> "insert".equals(method.getName()) ? 1 : null);
        PasswordEncoder encoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "encoded:" + rawPassword;
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return ("encoded:" + rawPassword).equals(encodedPassword);
            }
        };
        AuthSessionService authSessionService = new AuthSessionService() {
            @Override
            public String createSession(Long userId, String username, Duration ttl) {
                return "test-session-id";
            }

            @Override
            public void requireActiveSession(CurrentUser currentUser) {
            }

            @Override
            public void revokeCurrentSession(CurrentUser currentUser) {
            }

            @Override
            public void revokeAllSessions(Long userId) {
            }
        };
        userService = new UserServiceImpl(
                mapper, bindRequestMapper, recoveryQuestionMapper, encoder, authSessionService, JWT_SECRET, 86400);
    }

    @Test
    void registerHashesPasswordAndUsesUsernameAsDefaultNickname() {
        UserRegisterResponse response = userService.register(
                new UserRegisterRequest("alice", "abc123456", null,
                        List.of(new RecoveryQuestionRequest("只有我知道的提示", "private-answer-2026"))));

        assertEquals(1L, response.userId());
        assertEquals("alice", response.nickname());
        assertEquals(6, response.coupleCode().length());
        assertEquals("encoded:abc123456", mapperStub.inserted.getPassword());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        mapperStub.usersByUsername.put("alice", user(1L, "alice"));

        assertThrows(ConflictException.class, () -> userService.register(
                new UserRegisterRequest("alice", "abc123456", "Alice",
                        List.of(new RecoveryQuestionRequest("只有我知道的提示", "private-answer-2026")))));
    }

    @Test
    void loginUsesSameMessageForInvalidCredentials() {
        UserEntity user = user(1L, "alice");
        user.setPassword("encoded:abc123456");
        mapperStub.usersByUsername.put("alice", user);

        UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                () -> userService.login(new UserLoginRequest("alice", "wrong-password")));

        assertEquals("用户名或密码错误", exception.getMessage());
    }

    @Test
    void nicknameCanChangeOnlyOncePerBeijingCalendarMonth() {
        UserEntity alice = user(1L, "alice");
        mapperStub.usersById.put(1L, alice);

        assertEquals(1, userService.getProfile(1L).nicknameChangesRemaining());
        var first = userService.updateProfile(1L, new UserProfileUpdateRequest("Alice"));
        assertEquals(0, first.nicknameChangesRemaining());
        assertEquals("Alice", alice.getNickname());
        assertEquals(1, mapperStub.nicknameUpdateCount);

        // 与当前昵称相同的请求是空操作，不再次占用额度。
        userService.updateProfile(1L, new UserProfileUpdateRequest(" Alice "));
        assertEquals(1, mapperStub.nicknameUpdateCount);
        assertThrows(ConflictException.class,
                () -> userService.updateProfile(1L, new UserProfileUpdateRequest("Alice Two")));
        assertEquals(1, mapperStub.nicknameUpdateCount);

        // 模拟进入下一自然月；历史修改时间早于本月月初后可再次修改。
        alice.setNicknameChangedAt(YearMonth.now(java.time.ZoneId.of("Asia/Shanghai"))
                .minusMonths(1).atDay(1).atStartOfDay());
        assertEquals(1, userService.getProfile(1L).nicknameChangesRemaining());
        userService.updateProfile(1L, new UserProfileUpdateRequest("Alice Two"));
        assertEquals(2, mapperStub.nicknameUpdateCount);
    }

    @Test
    void createBindRequestRejectsOwnCoupleCode() {
        UserEntity current = user(1L, "alice");
        current.setCoupleCode("ABC234");
        mapperStub.usersByCoupleCode.put("ABC234", current);
        mapperStub.usersById.put(1L, current);

        assertThrows(BadRequestException.class,
                () -> userService.createBindRequest(1L, new UserBindRequest("abc234")));
    }

    @Test
    void createBindRequestDoesNotBindUsersBeforeReceiverAccepts() {
        UserEntity current = user(1L, "alice");
        current.setCoupleCode("AAA234");
        UserEntity target = user(2L, "bob");
        target.setNickname("鲍勃");
        target.setCoupleCode("BBB234");
        mapperStub.usersById.put(1L, current);
        mapperStub.usersById.put(2L, target);
        mapperStub.usersByCoupleCode.put("BBB234", target);

        CoupleBindRequestView response = userService.createBindRequest(
                1L, new UserBindRequest("bbb234"));

        assertEquals(2L, response.receiverId());
        assertEquals("鲍勃", response.receiverNickname());
        assertEquals("PENDING", response.status());
        assertEquals(0, mapperStub.bindingUpdateCount);
    }

    @Test
    void acceptBindRequestUpdatesBothUsers() {
        UserEntity requester = user(1L, "alice");
        UserEntity receiver = user(2L, "bob");
        receiver.setNickname("鲍勃");
        mapperStub.usersById.put(1L, requester);
        mapperStub.usersById.put(2L, receiver);
        mapperStub.lockedPair = List.of(requester, receiver);
        CoupleBindRequestEntity request = new CoupleBindRequestEntity();
        request.setId(10L);
        request.setRequesterId(1L);
        request.setReceiverId(2L);
        request.setStatus("PENDING");
        request.setCreatedAt(LocalDateTime.now());
        bindRequestMapperStub.requestsById.put(10L, request);

        UserBindResponse response = userService.acceptBindRequest(2L, 10L);

        assertEquals(1L, response.partnerId());
        assertEquals("alice", response.partnerNickname());
        assertEquals("绑定申请已同意", response.message());
        assertEquals(2, mapperStub.bindingUpdateCount);
        assertEquals("ACCEPTED", bindRequestMapperStub.lastDecision);
    }

    @Test
    void unbindClearsBothSidesAndIssuesDifferentCodes() {
        UserEntity alice = user(1L, "alice");
        UserEntity bob = user(2L, "bob");
        alice.setPartnerId(2L);
        bob.setPartnerId(1L);
        alice.setRelationshipStatus("BOUND");
        bob.setRelationshipStatus("BOUND");
        mapperStub.usersById.put(1L, alice);
        mapperStub.usersById.put(2L, bob);
        mapperStub.lockedPair = List.of(alice, bob);

        var response = userService.unbind(1L, new UserUnbindRequest(true, "双方协商"));

        assertEquals("解绑成功", response.message());
        assertNotNull(response.unboundAt());
        assertEquals(alice.getCoupleCode(), response.newCoupleCode());
        assertEquals(6, alice.getCoupleCode().length());
        assertEquals(6, bob.getCoupleCode().length());
        assertEquals(false, alice.getCoupleCode().equals(bob.getCoupleCode()));
        assertNull(alice.getPartnerId());
        assertNull(bob.getPartnerId());
        assertEquals("UNBOUND_HISTORY", alice.getRelationshipStatus());
        assertEquals("UNBOUND_HISTORY", bob.getRelationshipStatus());
        assertEquals(1, bindRequestMapperStub.cancelPendingCount);
    }

    @Test
    void unbindRejectsNonReciprocalRelationship() {
        UserEntity alice = user(1L, "alice");
        UserEntity bob = user(2L, "bob");
        alice.setPartnerId(2L);
        alice.setRelationshipStatus("BOUND");
        mapperStub.usersById.put(1L, alice);
        mapperStub.usersById.put(2L, bob);
        mapperStub.lockedPair = List.of(alice, bob);

        assertThrows(ConflictException.class,
                () -> userService.unbind(1L, new UserUnbindRequest(true, "")));
        assertEquals(0, mapperStub.unbindingUpdateCount);
    }

    private UserEntity user(Long id, String username) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(username);
        user.setRelationshipStatus("UNBOUND");
        user.setMembershipPlan("FREE");
        user.setIsDeleted(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    /** 仅实现被测 Service 会调用的 Mapper 方法，其余方法返回类型默认值。 */
    private static final class StubUserMapper implements InvocationHandler {
        private final Map<String, UserEntity> usersByUsername = new HashMap<>();
        private final Map<String, UserEntity> usersByCoupleCode = new HashMap<>();
        private final Map<Long, UserEntity> usersById = new HashMap<>();
        private List<UserEntity> lockedPair = List.of();
        private UserEntity inserted;
        private int bindingUpdateCount;
        private int unbindingUpdateCount;
        private int nicknameUpdateCount;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "selectByUsername" -> usersByUsername.get((String) args[0]);
                case "selectByCoupleCode" -> usersByCoupleCode.get((String) args[0]);
                case "selectById" -> usersById.get((Long) args[0]);
                case "selectByIdForUpdate" -> usersById.get((Long) args[0]);
                case "selectPairForUpdate" -> lockedPair;
                case "insert" -> insert((UserEntity) args[0]);
                case "updateBinding" -> {
                    bindingUpdateCount++;
                    yield 1;
                }
                case "updateUnbinding" -> {
                    UserEntity user = usersById.get((Long) args[0]);
                    user.setPartnerId(null);
                    user.setPartnerRemark(null);
                    user.setRelationshipStatus("UNBOUND_HISTORY");
                    user.setUnboundAt((LocalDateTime) args[3]);
                    user.setCoupleCode((String) args[2]);
                    unbindingUpdateCount++;
                    yield 1;
                }
                case "updateNickname" -> {
                    UserEntity user = usersById.get((Long) args[0]);
                    user.setNickname((String) args[1]);
                    user.setNicknameChangedAt((LocalDateTime) args[2]);
                    nicknameUpdateCount++;
                    yield 1;
                }
                case "updatePartnerRemark" -> 1;
                case "toString" -> "StubUserMapper";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            };
        }

        private int insert(UserEntity user) {
            inserted = user;
            user.setId(1L);
            usersByUsername.put(user.getUsername(), user);
            usersByCoupleCode.put(user.getCoupleCode(), user);
            usersById.put(user.getId(), user);
            return 1;
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            return null;
        }
    }

    /** 绑定申请 Mapper 的内存替身。 */
    private static final class StubBindRequestMapper implements InvocationHandler {
        private final Map<Long, CoupleBindRequestEntity> requestsById = new HashMap<>();
        private long nextId = 1L;
        private String lastDecision;
        private int cancelPendingCount;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "insert" -> insert((CoupleBindRequestEntity) args[0]);
                case "selectByIdForUpdate" -> requestsById.get((Long) args[0]);
                case "selectReceived", "selectSent" -> List.of();
                case "updateDecision" -> updateDecision((Long) args[0], (Long) args[1], (String) args[2]);
                case "cancelOtherPending" -> 0;
                case "cancelPendingForPair" -> { cancelPendingCount++; yield 0; }
                case "toString" -> "StubBindRequestMapper";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            };
        }

        private int insert(CoupleBindRequestEntity request) {
            request.setId(nextId++);
            requestsById.put(request.getId(), request);
            return 1;
        }

        private int updateDecision(Long requestId, Long receiverId, String status) {
            CoupleBindRequestEntity request = requestsById.get(requestId);
            if (request == null || !receiverId.equals(request.getReceiverId())
                    || !"PENDING".equals(request.getStatus())) {
                return 0;
            }
            request.setStatus(status);
            lastDecision = status;
            return 1;
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class) return false;
            if (returnType == int.class) return 0;
            if (returnType == long.class) return 0L;
            return null;
        }
    }
}
