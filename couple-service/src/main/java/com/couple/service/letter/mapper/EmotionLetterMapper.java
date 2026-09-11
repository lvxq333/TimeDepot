package com.couple.service.letter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couple.service.letter.entity.EmotionLetterEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 情绪信数据访问入口，列表查询会隔离接收方尚未收到的草稿和冷静期信件。 */
@Mapper
public interface EmotionLetterMapper extends BaseMapper<EmotionLetterEntity> {

    /** 锁定未删除信件，保证并发状态操作串行执行。 */
    @Select("SELECT * FROM emotion_letter WHERE id = #{letterId} AND is_deleted = 0 FOR UPDATE")
    EmotionLetterEntity selectByIdForUpdate(@Param("letterId") Long letterId);

    @Select("<script>SELECT * FROM emotion_letter WHERE is_deleted = 0 "
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
            + "AND (sender_id = #{userId} OR (receiver_id = #{userId} "
            + "AND status IN ('SENT','READ','RESOLVED'))) ORDER BY created_at ASC, id ASC")
    List<EmotionLetterEntity> selectVisibleThread(@Param("userId") Long userId,
                                                  @Param("threadId") String threadId);

    @Select("<script>SELECT COUNT(*) FROM emotion_letter WHERE is_deleted = 0 "
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
