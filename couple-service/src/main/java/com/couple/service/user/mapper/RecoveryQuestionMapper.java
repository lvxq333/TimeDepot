package com.couple.service.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couple.service.user.entity.RecoveryQuestionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RecoveryQuestionMapper extends BaseMapper<RecoveryQuestionEntity> {
    @Select("SELECT * FROM user_recovery_question WHERE user_id = #{userId} ORDER BY position")
    List<RecoveryQuestionEntity> selectByUserId(@Param("userId") Long userId);
}
