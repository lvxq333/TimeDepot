package com.couple.service.memo.service.impl;

import com.couple.common.exception.NotFoundException;
import com.couple.service.letter.entity.EmotionLetterEntity;
import com.couple.service.letter.mapper.EmotionLetterMapper;
import com.couple.service.letter.service.LetterService;
import com.couple.service.letter.vo.LetterDetailView;
import com.couple.service.letter.vo.LetterThreadView;
import com.couple.service.memo.service.MemoService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoEventServiceImplTest {

    @Test
    void listGroupsLettersByThreadAndOrdersByLatestReply() {
        LocalDateTime base = LocalDateTime.of(2026, 9, 11, 10, 0);
        List<EmotionLetterEntity> letters = List.of(
                letter(1L, "thread-a", 1L, 2L, "事件 A", "READ", base),
                letter(2L, "thread-a", 2L, 1L, "回复 A", "SENT", base.plusHours(1)),
                letter(3L, "thread-b", 2L, 1L, "事件 B", "READ", base.plusHours(2)));
        MemoEventServiceImpl service = service(letters, List.of());

        var page = service.list(1L, 1, 20);

        assertEquals(2, page.total());
        assertEquals("thread-b", page.records().get(0).threadId());
        assertEquals(2, page.records().get(1).letterCount());
        assertEquals(1, page.records().get(1).unreadCount());
        assertEquals("NONE", page.records().get(1).memoStatus());
    }

    @Test
    void detailKeepsOnlySentTimelineEntriesInChronologicalOrder() {
        LocalDateTime base = LocalDateTime.of(2026, 9, 11, 10, 0);
        EmotionLetterEntity shared = letter(1L, "thread-a", 1L, 2L, "事件 A", "READ", base);
        List<LetterDetailView> timeline = List.of(
                view(1L, "READ", base),
                view(2L, "DRAFT", base.plusHours(1)));
        MemoEventServiceImpl service = service(List.of(shared), timeline);

        var detail = service.detail(1L, "thread-a");

        assertEquals(1, detail.letters().size());
        assertEquals(1L, detail.letters().get(0).letterId());
    }

    private static MemoEventServiceImpl service(List<EmotionLetterEntity> shared,
                                                List<LetterDetailView> timeline) {
        EmotionLetterMapper mapper = proxy(EmotionLetterMapper.class, (proxy, method, args) ->
                switch (method.getName()) {
                    case "selectSharedEventPage", "selectSharedEventThread" -> shared;
                    case "countSharedEvents" -> (long) shared.stream()
                            .map(EmotionLetterEntity::getThreadId).distinct().count();
                    default -> defaultValue(method.getReturnType());
                });
        LetterService letterService = proxy(LetterService.class, (proxy, method, args) ->
                "thread".equals(method.getName())
                        ? new LetterThreadView("thread-a", "事件 A", timeline)
                        : defaultValue(method.getReturnType()));
        MemoService memoService = proxy(MemoService.class, (proxy, method, args) -> {
            if ("getByLetter".equals(method.getName())) throw new NotFoundException("暂无备忘录");
            return defaultValue(method.getReturnType());
        });
        return new MemoEventServiceImpl(mapper, letterService, memoService);
    }

    private static EmotionLetterEntity letter(Long id, String threadId, Long senderId, Long receiverId,
                                               String title, String status, LocalDateTime createdAt) {
        EmotionLetterEntity entity = new EmotionLetterEntity();
        entity.setId(id);
        entity.setThreadId(threadId);
        entity.setSenderId(senderId);
        entity.setReceiverId(receiverId);
        entity.setTitle(title);
        entity.setStatus(status);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private static LetterDetailView view(Long id, String status, LocalDateTime createdAt) {
        return new LetterDetailView(id, "标题", "正文", "sad", 5, status,
                1L, "甲", 2L, "乙", "thread-a", null, "sent",
                "READ".equals(status), null, createdAt, createdAt, null, null, List.of(), null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
