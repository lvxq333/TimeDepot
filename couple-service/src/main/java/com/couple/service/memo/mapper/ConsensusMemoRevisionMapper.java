package com.couple.service.memo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couple.service.memo.entity.ConsensusMemoRevisionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 备忘录正文版本数据访问入口。 */
@Mapper
public interface ConsensusMemoRevisionMapper extends BaseMapper<ConsensusMemoRevisionEntity> {
    @Select("SELECT * FROM consensus_memo_revision WHERE memo_id = #{memoId} ORDER BY version_no")
    List<ConsensusMemoRevisionEntity> selectByMemoId(@Param("memoId") Long memoId);
}
