package com.scaffold.bookkeeping.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

/** 仅供服务端 OCR 专用配置流程使用，通用参数接口不读取本命名空间。 */
@Mapper
public interface OcrConfigMapper {
    @Select("SELECT config_value FROM sys_config WHERE config_key = #{key}")
    String selectValue(@Param("key") String key);

    @Select("""
            SELECT config_key AS name, config_value AS value FROM sys_config
            WHERE config_key IN ('bookkeeping.ocr.provider','bookkeeping.ocr.endpoint',
                                 'bookkeeping.ocr.api_key','bookkeeping.ocr.access_key_id')
            """)
    List<Map<String,String>> selectSettings();

    @Insert("""
            INSERT INTO sys_config (config_name,config_key,config_value,config_type,builtin,create_by,update_by)
            VALUES ('OCR 专用配置',#{key},#{value},'Y',1,#{username},#{username})
            ON DUPLICATE KEY UPDATE config_value=#{value},builtin=1,update_by=#{username},update_time=NOW()
            """)
    void upsert(@Param("key") String key, @Param("value") String value, @Param("username") String username);
}
