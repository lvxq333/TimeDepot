package com.couple.service.memo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couple.service.memo.entity.MemoCommitmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 结构化承诺数据访问入口，不提供物理删除方法。 */
@Mapper
public interface MemoCommitmentMapper extends BaseMapper<MemoCommitmentEntity> {
    @Select("SELECT * FROM memo_commitment WHERE memo_id = #{memoId} ORDER BY created_at, id")
    List<MemoCommitmentEntity> selectByMemoId(@Param("memoId") Long memoId);

    @Select("SELECT * FROM memo_commitment WHERE id = #{commitmentId} FOR UPDATE")
    MemoCommitmentEntity selectByIdForUpdate(@Param("commitmentId") Long commitmentId);
}
