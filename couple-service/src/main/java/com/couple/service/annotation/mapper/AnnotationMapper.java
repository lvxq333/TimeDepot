package com.couple.service.annotation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couple.service.annotation.entity.AnnotationEntity;
import com.couple.service.annotation.vo.LetterAnnotationCountView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 批注数据访问入口，逻辑删除的数据不会出现在查询结果中。 */
@Mapper
public interface AnnotationMapper extends BaseMapper<AnnotationEntity> {

    @Select("SELECT * FROM annotation WHERE id = #{annotationId} AND is_deleted = 0 FOR UPDATE")
    AnnotationEntity selectByIdForUpdate(@Param("annotationId") Long annotationId);

    @Select("SELECT * FROM annotation WHERE letter_id = #{letterId} AND is_deleted = 0 "
            + "ORDER BY quote_start ASC, created_at ASC, id ASC")
    List<AnnotationEntity> selectByLetterId(@Param("letterId") Long letterId);

    @Select("SELECT COUNT(*) FROM annotation WHERE parent_annotation_id = #{annotationId} "
            + "AND is_deleted = 0")
    long countActiveReplies(@Param("annotationId") Long annotationId);

    @Select("<script>SELECT letter_id, COUNT(*) AS annotation_count FROM annotation "
            + "WHERE is_deleted = 0 AND parent_annotation_id IS NULL AND letter_id IN "
            + "<foreach collection='letterIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY letter_id</script>")
    List<LetterAnnotationCountView> countTopLevelByLetterIds(@Param("letterIds") List<Long> letterIds);
}
