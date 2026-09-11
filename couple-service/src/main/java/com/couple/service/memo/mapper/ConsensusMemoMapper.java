package com.couple.service.memo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couple.service.memo.entity.ConsensusMemoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 共识备忘录数据访问入口。 */
@Mapper
public interface ConsensusMemoMapper extends BaseMapper<ConsensusMemoEntity> {
    @Select("SELECT * FROM consensus_memo WHERE thread_id = #{threadId}")
    ConsensusMemoEntity selectByThreadId(@Param("threadId") String threadId);

    @Select("SELECT * FROM consensus_memo WHERE id = #{memoId} FOR UPDATE")
    ConsensusMemoEntity selectByIdForUpdate(@Param("memoId") Long memoId);
}
