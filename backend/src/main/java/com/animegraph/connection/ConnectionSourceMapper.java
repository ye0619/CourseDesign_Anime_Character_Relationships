package com.animegraph.connection;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ConnectionSourceMapper {
    @Select("SELECT subject_id FROM public_credit_snapshot WHERE kind='CAST' UNION SELECT source_id FROM saved_anime WHERE source='bangumi' AND payload IS NOT NULL ORDER BY subject_id LIMIT 501")
    List<Long> subjects();
}
