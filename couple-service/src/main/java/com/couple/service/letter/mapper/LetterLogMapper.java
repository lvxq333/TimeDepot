package com.couple.service.letter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.couple.service.letter.entity.LetterLogEntity;
import org.apache.ibatis.annotations.Mapper;

/** 情绪信操作日志的数据访问入口。 */
@Mapper
public interface LetterLogMapper extends BaseMapper<LetterLogEntity> {
}
