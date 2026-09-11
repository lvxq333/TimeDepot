package com.couple.service.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couple.service.auth.entity.AuthSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 登录会话的数据访问入口。 */
@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSessionEntity> {

    @Select("SELECT * FROM auth_session WHERE session_id = #{sessionId} LIMIT 1")
    AuthSessionEntity selectBySessionId(@Param("sessionId") String sessionId);

    @Update("UPDATE auth_session SET status = 'REVOKED', revoked_at = #{revokedAt}, "
            + "revoke_reason = #{reason}, updated_at = #{revokedAt} "
            + "WHERE session_id = #{sessionId} AND user_id = #{userId} AND status = 'ACTIVE'")
    int revoke(@Param("sessionId") String sessionId,
               @Param("userId") Long userId,
               @Param("reason") String reason,
               @Param("revokedAt") LocalDateTime revokedAt);
}
