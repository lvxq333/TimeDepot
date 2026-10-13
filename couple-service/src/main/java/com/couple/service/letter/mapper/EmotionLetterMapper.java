package com.couple.service.letter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couple.service.letter.entity.EmotionLetterEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.time.LocalDateTime;

/** 情绪信数据访问入口，列表查询会隔离接收方尚未收到的草稿和冷静期信件。 */
@Mapper
public interface EmotionLetterMapper extends BaseMapper<EmotionLetterEntity> {

    @Select("SELECT COUNT(*) FROM emotion_letter l WHERE l.thread_id = #{threadId} AND l.is_deleted = 0 "
            + "AND NOT EXISTS (SELECT 1 FROM letter_visibility h WHERE h.letter_id = l.id AND h.user_id = #{userId}) "
            + "AND (l.sender_id = #{userId} OR (l.receiver_id = #{userId} AND l.status IN ('SENT','READ','RESOLVED')))")
    int countVisibleInThread(@Param("userId") Long userId, @Param("threadId") String threadId);

    /** 锁定未删除信件，保证并发状态操作串行执行。 */
    @Select("SELECT * FROM emotion_letter WHERE id = #{letterId} AND is_deleted = 0 FOR UPDATE")
    EmotionLetterEntity selectByIdForUpdate(@Param("letterId") Long letterId);

    @Select("SELECT * FROM emotion_letter WHERE thread_id = #{threadId} AND is_deleted = 0 "
            + "ORDER BY created_at ASC, id ASC LIMIT 1")
    EmotionLetterEntity selectRootByThreadId(@Param("threadId") String threadId);

    @Select("<script>SELECT * FROM emotion_letter WHERE is_deleted = 0 "
            + "AND NOT EXISTS (SELECT 1 FROM letter_visibility h WHERE h.letter_id = emotion_letter.id AND h.user_id = #{userId}) "
            + "<choose>"
            + "<when test='direction == \"sent\"'>AND sender_id = #{userId} </when>"
            + "<when test='direction == \"inbox\"'>AND receiver_id = #{userId} "
            + "AND status IN ('SENT','READ','RESOLVED') </when>"
            + "<otherwise>AND (sender_id = #{userId} OR (receiver_id = #{userId} "
            + "AND status IN ('SENT','READ','RESOLVED'))) </otherwise>"
            + "</choose>"
            + "<if test='statuses != null and !statuses.isEmpty()'>AND status IN "
            + "<foreach collection='statuses' item='status' open='(' separator=',' close=')'>#{status}</foreach> "
            + "</if>"
            + "<if test='emotionTag != null'>AND emotion_tag = #{emotionTag} </if>"
            + "ORDER BY created_at DESC LIMIT #{pageSize} OFFSET #{offset}</script>")
    List<EmotionLetterEntity> selectVisiblePage(@Param("userId") Long userId,
                                                 @Param("direction") String direction,
                                                 @Param("statuses") List<String> statuses,
                                                 @Param("emotionTag") String emotionTag,
                                                 @Param("offset") long offset,
                                                 @Param("pageSize") int pageSize);

    @Select("SELECT * FROM emotion_letter WHERE is_deleted = 0 AND thread_id = #{threadId} "
            + "AND NOT EXISTS (SELECT 1 FROM letter_visibility h WHERE h.letter_id = emotion_letter.id AND h.user_id = #{userId}) "
            + "AND (sender_id = #{userId} OR (receiver_id = #{userId} "
            + "AND status IN ('SENT','READ','RESOLVED'))) ORDER BY created_at ASC, id ASC")
    List<EmotionLetterEntity> selectVisibleThread(@Param("userId") Long userId,
                                                  @Param("threadId") String threadId);

    /** 服务端先分页事件，再返回本页事件内当前用户可见的信件。 */
    @Select("<script>WITH visible AS ("
            + "SELECT l.*, CASE WHEN l.sender_id = #{userId} THEN 'sent' ELSE 'inbox' END AS view_direction, "
            + "ROW_NUMBER() OVER (PARTITION BY l.thread_id ORDER BY COALESCE(l.sent_at,l.created_at) DESC,l.id DESC) AS rn "
            + "FROM emotion_letter l WHERE l.is_deleted = 0 "
            + "AND NOT EXISTS (SELECT 1 FROM letter_visibility h WHERE h.letter_id = l.id AND h.user_id = #{userId}) "
            + "AND (l.sender_id = #{userId} OR "
            + "(l.receiver_id = #{userId} AND l.status IN ('SENT','READ','RESOLVED')))), "
            + "thread_page AS (SELECT v.thread_id, MAX(COALESCE(v.sent_at,v.created_at)) AS last_activity "
            + "FROM visible v JOIN letter_thread t ON t.thread_id = v.thread_id "
            + "JOIN visible latest ON latest.thread_id = v.thread_id AND latest.rn = 1 WHERE 1=1 "
            + "<if test='direction != null'>AND latest.view_direction = #{direction} </if>"
            + "<if test='status != null'>AND (CASE WHEN t.status IN ('RESOLVED','CLOSED') THEN t.status "
            + "WHEN latest.status IN ('DRAFT','COOLDOWN') THEN latest.status ELSE t.status END) = #{status} </if>"
            + "<if test='keyword != null or emotionTag != null or startAt != null or endAt != null'>"
            + "AND EXISTS (SELECT 1 FROM visible matched WHERE matched.thread_id = v.thread_id "
            + "<if test='keyword != null'>AND (LOCATE(#{keyword}, COALESCE(matched.title,'')) &gt; 0 OR LOCATE(#{keyword}, matched.content) &gt; 0) </if>"
            + "<if test='emotionTag != null'>AND matched.emotion_tag = #{emotionTag} </if>"
            + "<if test='startAt != null'>AND matched.created_at &gt;= #{startAt} </if>"
            + "<if test='endAt != null'>AND matched.created_at &lt; #{endAt} </if>"
            + ") </if>"
            + "GROUP BY v.thread_id ORDER BY last_activity DESC LIMIT #{pageSize} OFFSET #{offset}) "
            + "SELECT l.* FROM emotion_letter l JOIN thread_page p ON p.thread_id = l.thread_id "
            + "WHERE l.is_deleted = 0 "
            + "AND NOT EXISTS (SELECT 1 FROM letter_visibility h WHERE h.letter_id = l.id AND h.user_id = #{userId}) "
            + "AND (l.sender_id = #{userId} OR "
            + "(l.receiver_id = #{userId} AND l.status IN ('SENT','READ','RESOLVED'))) "
            + "ORDER BY p.last_activity DESC,l.created_at ASC,l.id ASC</script>")
    List<EmotionLetterEntity> selectVisibleThreadPage(@Param("userId") Long userId,
                                                       @Param("direction") String direction,
                                                       @Param("status") String status,
                                                       @Param("emotionTag") String emotionTag,
                                                       @Param("keyword") String keyword,
                                                       @Param("startAt") LocalDateTime startAt,
                                                       @Param("endAt") LocalDateTime endAt,
                                                       @Param("offset") long offset,
                                                       @Param("pageSize") int pageSize);

    @Select("<script>WITH visible AS ("
            + "SELECT l.*, CASE WHEN l.sender_id = #{userId} THEN 'sent' ELSE 'inbox' END AS view_direction, "
            + "ROW_NUMBER() OVER (PARTITION BY l.thread_id ORDER BY COALESCE(l.sent_at,l.created_at) DESC,l.id DESC) AS rn "
            + "FROM emotion_letter l WHERE l.is_deleted = 0 "
            + "AND NOT EXISTS (SELECT 1 FROM letter_visibility h WHERE h.letter_id = l.id AND h.user_id = #{userId}) "
            + "AND (l.sender_id = #{userId} OR "
            + "(l.receiver_id = #{userId} AND l.status IN ('SENT','READ','RESOLVED')))) "
            + "SELECT COUNT(DISTINCT v.thread_id) FROM visible v "
            + "JOIN letter_thread t ON t.thread_id = v.thread_id "
            + "JOIN visible latest ON latest.thread_id = v.thread_id AND latest.rn = 1 WHERE 1=1 "
            + "<if test='direction != null'>AND latest.view_direction = #{direction} </if>"
            + "<if test='status != null'>AND (CASE WHEN t.status IN ('RESOLVED','CLOSED') THEN t.status "
            + "WHEN latest.status IN ('DRAFT','COOLDOWN') THEN latest.status ELSE t.status END) = #{status} </if>"
            + "<if test='keyword != null or emotionTag != null or startAt != null or endAt != null'>"
            + "AND EXISTS (SELECT 1 FROM visible matched WHERE matched.thread_id = v.thread_id "
            + "<if test='keyword != null'>AND (LOCATE(#{keyword}, COALESCE(matched.title,'')) &gt; 0 OR LOCATE(#{keyword}, matched.content) &gt; 0) </if>"
            + "<if test='emotionTag != null'>AND matched.emotion_tag = #{emotionTag} </if>"
            + "<if test='startAt != null'>AND matched.created_at &gt;= #{startAt} </if>"
            + "<if test='endAt != null'>AND matched.created_at &lt; #{endAt} </if>"
            + ") </if>"
            + "</script>")
    long countVisibleThreads(@Param("userId") Long userId,
                             @Param("direction") String direction,
                             @Param("status") String status,
                             @Param("emotionTag") String emotionTag,
                             @Param("keyword") String keyword,
                             @Param("startAt") LocalDateTime startAt,
                             @Param("endAt") LocalDateTime endAt);

    /** 先按最近活动的线程分页，再取当前页中的已发送信件，避免全量加载历史记录。 */
    @Select("SELECT letter.* FROM emotion_letter letter JOIN ("
            + "SELECT thread_id, MAX(created_at) AS last_reply_at FROM emotion_letter "
            + "WHERE is_deleted = 0 AND status IN ('SENT','READ','RESOLVED') "
            + "AND NOT EXISTS (SELECT 1 FROM letter_visibility h WHERE h.letter_id = emotion_letter.id AND h.user_id = #{userId}) "
            + "AND (sender_id = #{userId} OR receiver_id = #{userId}) "
            + "GROUP BY thread_id ORDER BY last_reply_at DESC LIMIT #{pageSize} OFFSET #{offset}"
            + ") event_page ON event_page.thread_id = letter.thread_id "
            + "WHERE letter.is_deleted = 0 AND letter.status IN ('SENT','READ','RESOLVED') "
            + "AND NOT EXISTS (SELECT 1 FROM letter_visibility h WHERE h.letter_id = letter.id AND h.user_id = #{userId}) "
            + "AND (letter.sender_id = #{userId} OR letter.receiver_id = #{userId}) "
            + "ORDER BY event_page.last_reply_at DESC, letter.created_at ASC, letter.id ASC")
    List<EmotionLetterEntity> selectSharedEventPage(@Param("userId") Long userId,
                                                     @Param("offset") long offset,
                                                     @Param("pageSize") int pageSize);

    /** 查询一个事件中双方都已实际拥有的信件。 */
    @Select("SELECT * FROM emotion_letter WHERE is_deleted = 0 AND thread_id = #{threadId} "
            + "AND NOT EXISTS (SELECT 1 FROM letter_visibility h WHERE h.letter_id = emotion_letter.id AND h.user_id = #{userId}) "
            + "AND status IN ('SENT','READ','RESOLVED') "
            + "AND (sender_id = #{userId} OR receiver_id = #{userId}) "
            + "ORDER BY created_at ASC, id ASC")
    List<EmotionLetterEntity> selectSharedEventThread(@Param("userId") Long userId,
                                                       @Param("threadId") String threadId);

    @Select("SELECT COUNT(DISTINCT thread_id) FROM emotion_letter WHERE is_deleted = 0 "
            + "AND NOT EXISTS (SELECT 1 FROM letter_visibility h WHERE h.letter_id = emotion_letter.id AND h.user_id = #{userId}) "
            + "AND status IN ('SENT','READ','RESOLVED') "
            + "AND (sender_id = #{userId} OR receiver_id = #{userId})")
    long countSharedEvents(@Param("userId") Long userId);

    @Select("<script>SELECT COUNT(*) FROM emotion_letter WHERE is_deleted = 0 "
            + "AND NOT EXISTS (SELECT 1 FROM letter_visibility h WHERE h.letter_id = emotion_letter.id AND h.user_id = #{userId}) "
            + "<choose>"
            + "<when test='direction == \"sent\"'>AND sender_id = #{userId} </when>"
            + "<when test='direction == \"inbox\"'>AND receiver_id = #{userId} "
            + "AND status IN ('SENT','READ','RESOLVED') </when>"
            + "<otherwise>AND (sender_id = #{userId} OR (receiver_id = #{userId} "
            + "AND status IN ('SENT','READ','RESOLVED'))) </otherwise>"
            + "</choose>"
            + "<if test='statuses != null and !statuses.isEmpty()'>AND status IN "
            + "<foreach collection='statuses' item='status' open='(' separator=',' close=')'>#{status}</foreach> "
            + "</if>"
            + "<if test='emotionTag != null'>AND emotion_tag = #{emotionTag} </if>"
            + "</script>")
    long countVisible(@Param("userId") Long userId,
                      @Param("direction") String direction,
                      @Param("statuses") List<String> statuses,
                      @Param("emotionTag") String emotionTag);
}
