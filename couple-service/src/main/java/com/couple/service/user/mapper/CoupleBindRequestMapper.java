package com.couple.service.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couple.service.user.entity.CoupleBindRequestEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 情侣绑定申请的数据访问入口。 */
@Mapper
public interface CoupleBindRequestMapper extends BaseMapper<CoupleBindRequestEntity> {

    /** 锁定单条申请，供同意操作进行状态校验。 */
    @Select("SELECT * FROM couple_bind_request WHERE id = #{requestId} FOR UPDATE")
    CoupleBindRequestEntity selectByIdForUpdate(@Param("requestId") Long requestId);

    /** 查询当前用户收到的申请，待处理申请优先。 */
    @Select("SELECT * FROM couple_bind_request WHERE receiver_id = #{userId} "
            + "ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END, created_at DESC")
    List<CoupleBindRequestEntity> selectReceived(@Param("userId") Long userId);

    /** 查询当前用户发出的申请。 */
    @Select("SELECT * FROM couple_bind_request WHERE requester_id = #{userId} ORDER BY created_at DESC")
    List<CoupleBindRequestEntity> selectSent(@Param("userId") Long userId);

    /** 接收方处理待处理申请，同时释放唯一活动键。 */
    @Update("UPDATE couple_bind_request SET status = #{status}, active_pair_key = NULL, "
            + "active_requester_id = NULL, processed_at = #{processedAt}, updated_at = #{processedAt} "
            + "WHERE id = #{requestId} AND receiver_id = #{receiverId} AND status = 'PENDING'")
    int updateDecision(@Param("requestId") Long requestId,
                       @Param("receiverId") Long receiverId,
                       @Param("status") String status,
                       @Param("processedAt") LocalDateTime processedAt);

    /** 绑定成功后取消涉及双方的其他待处理申请，防止残留申请再次被接受。 */
    @Update("UPDATE couple_bind_request SET status = 'CANCELLED', active_pair_key = NULL, "
            + "active_requester_id = NULL, processed_at = #{processedAt}, updated_at = #{processedAt} "
            + "WHERE status = 'PENDING' AND id <> #{acceptedId} AND "
            + "(requester_id IN (#{firstUserId}, #{secondUserId}) "
            + "OR receiver_id IN (#{firstUserId}, #{secondUserId}))")
    int cancelOtherPending(@Param("acceptedId") Long acceptedId,
                           @Param("firstUserId") Long firstUserId,
                           @Param("secondUserId") Long secondUserId,
                           @Param("processedAt") LocalDateTime processedAt);
}
