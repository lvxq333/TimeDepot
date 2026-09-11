package com.couple.service.memo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couple.service.memo.entity.MemoCommitmentRevisionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/** 承诺版本及审核记录数据访问入口。 */
@Mapper
public interface MemoCommitmentRevisionMapper extends BaseMapper<MemoCommitmentRevisionEntity> {
    @Select("SELECT * FROM memo_commitment_revision WHERE commitment_id = #{commitmentId} ORDER BY version_no")
    List<MemoCommitmentRevisionEntity> selectByCommitmentId(@Param("commitmentId") Long commitmentId);

    @Select("SELECT * FROM memo_commitment_revision WHERE id = #{revisionId} FOR UPDATE")
    MemoCommitmentRevisionEntity selectByIdForUpdate(@Param("revisionId") Long revisionId);

    @Delete("DELETE FROM memo_commitment_revision WHERE commitment_id = #{commitmentId}")
    int deletePrivateByCommitmentId(@Param("commitmentId") Long commitmentId);
}
