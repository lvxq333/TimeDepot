package com.couple.service.user.service.impl;

import com.couple.common.exception.BadRequestException;
import com.couple.common.exception.ConflictException;
import com.couple.common.exception.UnauthorizedException;
import com.couple.service.user.dto.UserBindRequest;
import com.couple.service.user.dto.UserLoginRequest;
import com.couple.service.user.dto.UserRegisterRequest;
import com.couple.service.user.entity.CoupleBindRequestEntity;
import com.couple.service.user.entity.UserEntity;
import com.couple.service.user.mapper.CoupleBindRequestMapper;
import com.couple.service.user.mapper.UserMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        userService = new UserServiceImpl(mapper, bindRequestMapper, encoder, JWT_SECRET, 86400);
    }

    @Test
    void registerHashesPasswordAndUsesUsernameAsDefaultNickname() {
        UserRegisterResponse response = userService.register(
                new UserRegisterRequest("alice", "abc123456", null));

        assertEquals(1L, response.userId());
        assertEquals("alice", response.nickname());
        assertEquals(6, response.coupleCode().length());
        assertEquals("encoded:abc123456", mapperStub.inserted.getPassword());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        mapperStub.usersByUsername.put("alice", user(1L, "alice"));

        assertThrows(ConflictException.class, () -> userService.register(
                new UserRegisterRequest("alice", "abc123456", "Alice")));
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

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "selectByUsername" -> usersByUsername.get((String) args[0]);
                case "selectByCoupleCode" -> usersByCoupleCode.get((String) args[0]);
                case "selectById" -> usersById.get((Long) args[0]);
                case "selectPairForUpdate" -> lockedPair;
                case "insert" -> insert((UserEntity) args[0]);
                case "updateBinding" -> {
                    bindingUpdateCount++;
                    yield 1;
                }
                case "updateNickname", "updatePartnerRemark" -> 1;
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

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "insert" -> insert((CoupleBindRequestEntity) args[0]);
                case "selectByIdForUpdate" -> requestsById.get((Long) args[0]);
                case "selectReceived", "selectSent" -> List.of();
                case "updateDecision" -> updateDecision((Long) args[0], (Long) args[1], (String) args[2]);
                case "cancelOtherPending" -> 0;
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
