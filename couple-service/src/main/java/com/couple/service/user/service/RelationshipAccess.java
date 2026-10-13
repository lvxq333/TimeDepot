package com.couple.service.user.service;

import com.couple.common.exception.ConflictException;
import com.couple.service.letter.entity.EmotionLetterEntity;
import com.couple.service.user.entity.UserEntity;
import com.couple.service.user.mapper.UserMapper;

import java.util.List;
import java.util.Objects;

/** 历史内容始终可读；原参与双方重新互相绑定后可按内容状态继续操作。 */
public final class RelationshipAccess {
    private RelationshipAccess() { }

    public static boolean canWrite(UserEntity sender, UserEntity receiver, EmotionLetterEntity letter) {
        if (sender == null || receiver == null || letter == null) return false;
        return "BOUND".equals(sender.getRelationshipStatus())
                && "BOUND".equals(receiver.getRelationshipStatus())
                && Objects.equals(sender.getPartnerId(), receiver.getId())
                && Objects.equals(receiver.getPartnerId(), sender.getId());
    }

    public static void requireWritable(UserMapper mapper, EmotionLetterEntity letter) {
        if (!isWritableLocked(mapper, letter)) {
            throw new ConflictException("原信件双方当前未互相绑定，仅可查看历史内容");
        }
    }

    public static boolean isWritableLocked(UserMapper mapper, EmotionLetterEntity letter) {
        Long senderId = letter.getSenderId();
        Long receiverId = letter.getReceiverId();
        List<UserEntity> users = mapper.selectPairForUpdate(
                Math.min(senderId, receiverId), Math.max(senderId, receiverId));
        UserEntity sender = users.stream().filter(user -> Objects.equals(user.getId(), senderId))
                .findFirst().orElse(null);
        UserEntity receiver = users.stream().filter(user -> Objects.equals(user.getId(), receiverId))
                .findFirst().orElse(null);
        return canWrite(sender, receiver, letter);
    }

    public static void requireCurrentPair(UserMapper mapper, Long firstId, Long secondId) {
        List<UserEntity> users = mapper.selectPairForUpdate(
                Math.min(firstId, secondId), Math.max(firstId, secondId));
        UserEntity first = users.stream().filter(user -> Objects.equals(user.getId(), firstId))
                .findFirst().orElse(null);
        UserEntity second = users.stream().filter(user -> Objects.equals(user.getId(), secondId))
                .findFirst().orElse(null);
        if (first == null || second == null
                || !"BOUND".equals(first.getRelationshipStatus())
                || !"BOUND".equals(second.getRelationshipStatus())
                || !Objects.equals(first.getPartnerId(), secondId)
                || !Objects.equals(second.getPartnerId(), firstId)) {
            throw new ConflictException("伴侣关系已变化，请刷新后重试");
        }
    }

}
