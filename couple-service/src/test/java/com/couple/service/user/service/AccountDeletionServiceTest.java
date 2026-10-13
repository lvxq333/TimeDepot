package com.couple.service.user.service;

import com.couple.common.exception.UnauthorizedException;
import com.couple.common.security.CurrentUser;
import com.couple.service.auth.service.AuthSessionService;
import com.couple.service.user.dto.AccountDeletionRequest;
import com.couple.service.user.entity.UserEntity;
import com.couple.service.user.mapper.AccountDeletionMapper;
import com.couple.service.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccountDeletionServiceTest {
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final List<String> actions = new ArrayList<>();

    @Test
    void wrongPasswordKeepsBothAccountsAndSharedContent() {
        AccountDeletionService service = service();
        assertThrows(UnauthorizedException.class,
                () -> service.delete(1L, new AccountDeletionRequest("wrong", true)));
        assertTrue(actions.isEmpty());
    }

    @Test
    void confirmedCancellationDetachesPartnerAndPrunesOnlyAfterAccountMarkedDeleted() {
        AccountDeletionService service = service();
        service.delete(1L, new AccountDeletionRequest("correct-password", true));
        assertTrue(actions.get(0).startsWith("detachDeletedPartner"));
        assertTrue(actions.contains("deleteLetters"));
        assertTrue(actions.contains("deleteNotifications"));
        assertTrue(actions.contains("revokeAllSessions"));
        assertTrue(actions.indexOf("markAccountDeleted") < actions.indexOf("deleteMemos"));
        assertTrue(actions.indexOf("deleteMemos") < actions.indexOf("deleteLetters"));
    }

    private AccountDeletionService service() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setPartnerId(2L);
        user.setPassword(encoder.encode("correct-password"));
        UserEntity partner = new UserEntity();
        partner.setId(2L);
        partner.setPartnerId(1L);
        UserMapper users = proxy(UserMapper.class, (method, args) -> switch (method.getName()) {
            case "selectById" -> user;
            case "selectPairForUpdate" -> List.of(user, partner);
            case "selectByCoupleCode" -> null;
            case "detachDeletedPartner", "markAccountDeleted" -> {
                actions.add(method.getName());
                yield 1;
            }
            default -> null;
        });
        AccountDeletionMapper deletion = proxy(AccountDeletionMapper.class, (method, args) -> {
            actions.add(method.getName());
            return 1;
        });
        AuthSessionService sessions = new AuthSessionService() {
            public String createSession(Long userId, String username, Duration ttl) { return null; }
            public void requireActiveSession(CurrentUser currentUser) { }
            public void revokeCurrentSession(CurrentUser currentUser) { }
            public void revokeAllSessions(Long userId) { actions.add("revokeAllSessions"); }
        };
        return new AccountDeletionService(users, deletion, sessions, encoder);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type,
            java.util.function.BiFunction<java.lang.reflect.Method, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> handler.apply(method, args));
    }
}
