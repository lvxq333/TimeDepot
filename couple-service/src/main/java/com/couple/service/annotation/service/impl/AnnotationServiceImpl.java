package com.couple.service.annotation.service.impl;

import com.couple.common.exception.BadRequestException;
import com.couple.common.exception.BusinessException;
import com.couple.common.exception.ConflictException;
import com.couple.common.exception.ForbiddenException;
import com.couple.common.exception.NotFoundException;
import com.couple.service.annotation.dto.AnnotationCreateRequest;
import com.couple.service.annotation.dto.AnnotationReplyRequest;
import com.couple.service.annotation.entity.AnnotationEntity;
import com.couple.service.annotation.mapper.AnnotationMapper;
import com.couple.service.annotation.service.AnnotationService;
import com.couple.service.annotation.vo.AnnotationDeleteResponse;
import com.couple.service.annotation.vo.AnnotationListResponse;
import com.couple.service.annotation.vo.AnnotationView;
import com.couple.service.letter.entity.EmotionLetterEntity;
import com.couple.service.letter.entity.LetterLogEntity;
import com.couple.service.letter.mapper.EmotionLetterMapper;
import com.couple.service.letter.mapper.LetterLogMapper;
import com.couple.service.user.entity.UserEntity;
import com.couple.service.user.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 批注实现：以已送达且参与双方可见的不可变正文作为引用依据。 */
@Service
public class AnnotationServiceImpl implements AnnotationService {
    private static final Set<String> DELIVERED_STATUSES = Set.of("SENT", "READ", "RESOLVED");

    private final AnnotationMapper annotationMapper;
    private final EmotionLetterMapper letterMapper;
    private final LetterLogMapper logMapper;
    private final UserMapper userMapper;

    public AnnotationServiceImpl(AnnotationMapper annotationMapper,
                                 EmotionLetterMapper letterMapper,
                                 LetterLogMapper logMapper,
                                 UserMapper userMapper) {
        this.annotationMapper = annotationMapper;
        this.letterMapper = letterMapper;
        this.logMapper = logMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public AnnotationView create(Long userId, AnnotationCreateRequest request) {
        EmotionLetterEntity letter = requireVisibleDeliveredLetter(userId, request.letterId(), true);
        validateQuote(letter.getContent(), request.quoteText(), request.quoteStart(), request.quoteEnd());

        LocalDateTime now = LocalDateTime.now();
        AnnotationEntity annotation = new AnnotationEntity();
        annotation.setLetterId(letter.getId());
        annotation.setAuthorId(userId);
        annotation.setQuoteText(request.quoteText());
        annotation.setQuoteStart(request.quoteStart());
        annotation.setQuoteEnd(request.quoteEnd());
        annotation.setContent(request.content().trim());
        annotation.setTemplateType(normalizeNullable(request.templateType()));
        annotation.setIsDeleted(0);
        annotation.setCreatedAt(now);
        annotation.setUpdatedAt(now);
        if (annotationMapper.insert(annotation) != 1) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "批注保存失败，请稍后重试");
        }
        writeLog(letter.getId(), userId, "ANNOTATE", "添加原文批注", now);
        return toView(annotation, requireAuthor(userId).getNickname());
    }

    @Override
    @Transactional
    public AnnotationView reply(Long userId, Long annotationId, AnnotationReplyRequest request) {
        AnnotationEntity target = annotationMapper.selectByIdForUpdate(annotationId);
        if (target == null) throw new NotFoundException("要回复的批注不存在");
        AnnotationEntity root = target.getParentAnnotationId() == null
                ? target : annotationMapper.selectByIdForUpdate(target.getParentAnnotationId());
        if (root == null) throw new NotFoundException("原始批注不存在");
        EmotionLetterEntity letter = requireVisibleDeliveredLetter(userId, root.getLetterId(), true);

        LocalDateTime now = LocalDateTime.now();
        AnnotationEntity reply = new AnnotationEntity();
        reply.setLetterId(letter.getId());
        reply.setAuthorId(userId);
        reply.setParentAnnotationId(root.getId());
        reply.setQuoteText(root.getQuoteText());
        reply.setQuoteStart(root.getQuoteStart());
        reply.setQuoteEnd(root.getQuoteEnd());
        reply.setContent(request.content().trim());
        reply.setIsDeleted(0);
        reply.setCreatedAt(now);
        reply.setUpdatedAt(now);
        if (annotationMapper.insert(reply) != 1) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "回复保存失败，请稍后重试");
        }
        writeLog(letter.getId(), userId, "REPLY_ANNOTATION", "回复原文批注", now);
        return toView(reply, requireAuthor(userId).getNickname());
    }

    @Override
    @Transactional(readOnly = true)
    public AnnotationListResponse list(Long userId, Long letterId) {
        requireVisibleDeliveredLetter(userId, letterId, false);
        List<AnnotationEntity> annotations = annotationMapper.selectByLetterId(letterId);
        Map<Long, String> nicknames = new HashMap<>();
        List<AnnotationView> views = annotations.stream().map(annotation -> {
            String nickname = nicknames.computeIfAbsent(annotation.getAuthorId(),
                    authorId -> requireAuthor(authorId).getNickname());
            return toView(annotation, nickname);
        }).toList();
        return new AnnotationListResponse(views);
    }

    @Override
    @Transactional
    public AnnotationDeleteResponse delete(Long userId, Long annotationId) {
        AnnotationEntity annotation = annotationMapper.selectByIdForUpdate(annotationId);
        if (annotation == null) throw new NotFoundException("批注不存在");
        if (!Objects.equals(userId, annotation.getAuthorId())) {
            throw new ForbiddenException("只能删除自己创建的批注");
        }
        requireVisibleDeliveredLetter(userId, annotation.getLetterId(), true);
        if (annotation.getParentAnnotationId() == null
                && annotationMapper.countActiveReplies(annotationId) > 0) {
            throw new ConflictException("这条批注已有回复，暂不能删除");
        }
        LocalDateTime now = LocalDateTime.now();
        if (annotationMapper.deleteById(annotationId) != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "批注状态已发生变化，请刷新后重试");
        }
        writeLog(annotation.getLetterId(), userId, "DELETE_ANNOTATION", "删除原文批注", now);
        return new AnnotationDeleteResponse(annotationId, "删除成功");
    }

    private EmotionLetterEntity requireVisibleDeliveredLetter(Long userId, Long letterId, boolean lock) {
        EmotionLetterEntity letter = lock
                ? letterMapper.selectByIdForUpdate(letterId)
                : letterMapper.selectById(letterId);
        if (letter == null) throw new NotFoundException("情绪信不存在");
        if (!Objects.equals(userId, letter.getSenderId())
                && !Objects.equals(userId, letter.getReceiverId())) {
            throw new ForbiddenException("无权查看或批注这封情绪信");
        }
        if (!DELIVERED_STATUSES.contains(letter.getStatus())) {
            throw new ForbiddenException("情绪信送达后才能添加或查看批注");
        }
        return letter;
    }

    private void validateQuote(String letterContent, String quoteText, int quoteStart, int quoteEnd) {
        if (quoteStart >= quoteEnd || quoteEnd > letterContent.length()) {
            throw new BadRequestException("引用位置超出信件正文范围");
        }
        if (!letterContent.substring(quoteStart, quoteEnd).equals(quoteText)) {
            throw new BadRequestException("引用原文与信件正文不匹配，请重新选择");
        }
    }

    private UserEntity requireAuthor(Long authorId) {
        UserEntity user = userMapper.selectById(authorId);
        if (user == null) throw new NotFoundException("批注作者不存在");
        return user;
    }

    private AnnotationView toView(AnnotationEntity annotation, String authorNickname) {
        return new AnnotationView(annotation.getId(), annotation.getLetterId(),
                annotation.getAuthorId(), annotation.getParentAnnotationId(), authorNickname,
                annotation.getQuoteText(),
                annotation.getQuoteStart(), annotation.getQuoteEnd(), annotation.getContent(),
                annotation.getTemplateType(), annotation.getCreatedAt());
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void writeLog(Long letterId, Long userId, String action, String detail, LocalDateTime now) {
        LetterLogEntity log = new LetterLogEntity();
        log.setLetterId(letterId);
        log.setOperatorId(userId);
        log.setAction(action);
        log.setDetail(detail);
        log.setCreatedAt(now);
        if (logMapper.insert(log) != 1) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "批注操作记录失败");
        }
    }
}
