package com.couple.service.letter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couple.service.letter.entity.LetterThreadEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LetterThreadMapper extends BaseMapper<LetterThreadEntity> {
    @Select("SELECT * FROM letter_thread WHERE thread_id = #{threadId} FOR UPDATE")
    LetterThreadEntity selectByIdForUpdate(@Param("threadId") String threadId);

    @Select("<script>SELECT * FROM letter_thread WHERE thread_id IN "
            + "<foreach collection='threadIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<LetterThreadEntity> selectByThreadIds(@Param("threadIds") List<String> threadIds);
}
