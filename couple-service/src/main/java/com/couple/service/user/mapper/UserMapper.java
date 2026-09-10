package com.couple.service.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couple.service.user.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 用户表数据访问入口。 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    /** 按用户名查询未删除用户。 */
    @Select("SELECT * FROM `user` WHERE username = #{username} AND is_deleted = 0 LIMIT 1")
    UserEntity selectByUsername(@Param("username") String username);

    /** 按配对码查询未删除用户，用于绑定前定位目标账号。 */
    @Select("SELECT * FROM `user` WHERE couple_code = #{coupleCode} AND is_deleted = 0 LIMIT 1")
    UserEntity selectByCoupleCode(@Param("coupleCode") String coupleCode);

    /**
     * 按固定 ID 顺序锁定参与绑定的两行，避免双方同时绑定时产生锁顺序反转。
     */
    @Select("SELECT * FROM `user` WHERE id IN (#{firstId}, #{secondId}) "
            + "AND is_deleted = 0 ORDER BY id FOR UPDATE")
    List<UserEntity> selectPairForUpdate(@Param("firstId") Long firstId,
                                         @Param("secondId") Long secondId);

    /** 将单个用户更新为已绑定状态；只有尚未绑定时才会成功。 */
    @Update("UPDATE `user` SET partner_id = #{partnerId}, partner_remark = #{partnerRemark}, "
            + "relationship_status = 'BOUND', bound_at = #{boundAt}, couple_code = NULL, "
            + "updated_at = #{boundAt} WHERE id = #{userId} AND partner_id IS NULL "
            + "AND is_deleted = 0")
    int updateBinding(@Param("userId") Long userId,
                      @Param("partnerId") Long partnerId,
                      @Param("partnerRemark") String partnerRemark,
                      @Param("boundAt") LocalDateTime boundAt);

    /** 修改指定用户昵称。 */
    @Update("UPDATE `user` SET nickname = #{nickname}, updated_at = #{updatedAt} "
            + "WHERE id = #{userId} AND is_deleted = 0")
    int updateNickname(@Param("userId") Long userId,
                       @Param("nickname") String nickname,
                       @Param("updatedAt") LocalDateTime updatedAt);

    /** 修改当前用户保存的伴侣备注。 */
    @Update("UPDATE `user` SET partner_remark = #{partnerRemark}, updated_at = #{updatedAt} "
            + "WHERE id = #{userId} AND is_deleted = 0")
    int updatePartnerRemark(@Param("userId") Long userId,
                            @Param("partnerRemark") String partnerRemark,
                            @Param("updatedAt") LocalDateTime updatedAt);
}
