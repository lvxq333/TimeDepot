package com.couple.service.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couple.service.notification.entity.UserNotificationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserNotificationMapper extends BaseMapper<UserNotificationEntity> {
    @Select("SELECT * FROM user_notification WHERE recipient_id = #{userId} "
            + "ORDER BY created_at DESC, id DESC LIMIT #{offset}, #{pageSize}")
    List<UserNotificationEntity> selectPage(@Param("userId") Long userId,
                                            @Param("offset") long offset,
                                            @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM user_notification WHERE recipient_id = #{userId}")
    long countAll(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM user_notification WHERE recipient_id = #{userId} AND read_at IS NULL")
    long countUnread(@Param("userId") Long userId);

    @Select("SELECT * FROM user_notification WHERE id = #{notificationId} AND recipient_id = #{userId}")
    UserNotificationEntity selectOwned(@Param("userId") Long userId,
                                       @Param("notificationId") Long notificationId);

    @Update("UPDATE user_notification SET read_at = #{readAt} WHERE id = #{notificationId} "
            + "AND recipient_id = #{userId} AND read_at IS NULL")
    int markRead(@Param("userId") Long userId, @Param("notificationId") Long notificationId,
                 @Param("readAt") LocalDateTime readAt);

    @Update("UPDATE user_notification SET read_at = #{readAt} WHERE recipient_id = #{userId} AND read_at IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
