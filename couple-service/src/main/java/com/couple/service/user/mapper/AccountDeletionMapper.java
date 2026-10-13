package com.couple.service.user.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 仅在信件双方都已注销时按依赖顺序清除共有内容。调用方必须开启事务。 */
@Mapper
public interface AccountDeletionMapper {
    String ELIGIBLE_LETTERS = " FROM emotion_letter l JOIN `user` s ON s.id = l.sender_id "
            + "JOIN `user` r ON r.id = l.receiver_id WHERE (l.sender_id = #{userId} OR l.receiver_id = #{userId}) "
            + "AND s.is_deleted = 1 AND r.is_deleted = 1";
    String LETTERS = "SELECT l.id" + ELIGIBLE_LETTERS;
    String THREADS = "SELECT DISTINCT l.thread_id" + ELIGIBLE_LETTERS;
    String MEMOS = "SELECT id FROM consensus_memo WHERE thread_id IN (" + THREADS + ")";
    String COMMITMENTS = "SELECT id FROM memo_commitment WHERE memo_id IN (" + MEMOS + ")";

    @Delete("DELETE FROM memo_commitment_revision WHERE commitment_id IN (" + COMMITMENTS + ")")
    int deleteCommitmentRevisions(@Param("userId") Long userId);

    @Delete("DELETE FROM memo_commitment WHERE memo_id IN (" + MEMOS + ")")
    int deleteCommitments(@Param("userId") Long userId);

    @Delete("DELETE FROM consensus_memo_revision WHERE memo_id IN (" + MEMOS + ")")
    int deleteMemoRevisions(@Param("userId") Long userId);

    @Delete("DELETE FROM consensus_memo WHERE thread_id IN (" + THREADS + ")")
    int deleteMemos(@Param("userId") Long userId);

    @Delete("DELETE FROM annotation WHERE letter_id IN (" + LETTERS + ")")
    int deleteAnnotations(@Param("userId") Long userId);

    @Delete("DELETE FROM letter_visibility WHERE letter_id IN (" + LETTERS + ")")
    int deleteVisibility(@Param("userId") Long userId);

    @Delete("DELETE FROM letter_log WHERE letter_id IN (" + LETTERS + ")")
    int deleteLogs(@Param("userId") Long userId);

    @Delete("DELETE FROM letter_thread WHERE thread_id IN (" + THREADS + ")")
    int deleteThreads(@Param("userId") Long userId);

    @Delete("DELETE l FROM emotion_letter l JOIN `user` s ON s.id = l.sender_id "
            + "JOIN `user` r ON r.id = l.receiver_id WHERE (l.sender_id = #{userId} OR l.receiver_id = #{userId}) "
            + "AND s.is_deleted = 1 AND r.is_deleted = 1")
    int deleteLetters(@Param("userId") Long userId);

    @Delete("DELETE FROM user_notification WHERE recipient_id = #{userId}")
    int deleteNotifications(@Param("userId") Long userId);

    @Delete("DELETE FROM couple_bind_request WHERE requester_id = #{userId} OR receiver_id = #{userId}")
    int deleteBindRequests(@Param("userId") Long userId);

    @Delete("DELETE FROM user_recovery_question WHERE user_id = #{userId}")
    int deleteRecoveryQuestions(@Param("userId") Long userId);

    @Delete("DELETE FROM auth_session WHERE user_id = #{userId}")
    int deleteSessions(@Param("userId") Long userId);
}
