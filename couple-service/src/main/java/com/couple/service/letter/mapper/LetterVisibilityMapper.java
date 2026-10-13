package com.couple.service.letter.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LetterVisibilityMapper {
    @Select("SELECT COUNT(*) FROM letter_visibility WHERE letter_id = #{letterId} AND user_id = #{userId}")
    int isHidden(@Param("letterId") Long letterId, @Param("userId") Long userId);

    @Insert("INSERT IGNORE INTO letter_visibility (letter_id, user_id) VALUES (#{letterId}, #{userId})")
    int hide(@Param("letterId") Long letterId, @Param("userId") Long userId);
}
