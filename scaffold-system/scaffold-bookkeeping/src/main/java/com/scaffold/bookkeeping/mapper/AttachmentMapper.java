package com.scaffold.bookkeeping.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface AttachmentMapper {
    @Select("""
            SELECT id, record_id AS recordId, original_name AS originalName,
                   mime_type AS mimeType, file_size AS fileSize, sha256,
                   is_primary AS isPrimary, source_type AS sourceType, uploaded_by AS uploadedBy,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime
            FROM acct_record_attachment WHERE record_id = #{recordId} AND deleted = 0
            ORDER BY is_primary DESC, id
            """)
    List<Map<String, Object>> selectAttachments(@Param("recordId") Long recordId);

    @Select("""
            SELECT id, record_id AS recordId, original_name AS originalName, storage_key AS storageKey,
                   mime_type AS mimeType, file_size AS fileSize, is_primary AS isPrimary
            FROM acct_record_attachment WHERE id = #{id} AND deleted = 0
            """)
    Map<String, Object> selectAttachment(@Param("id") Long id);

    @Insert("""
            INSERT INTO acct_record_attachment
                (record_id, original_name, storage_key, mime_type, file_size, sha256,
                 is_primary, source_type, uploaded_by, create_by, update_by)
            VALUES (#{row.recordId}, #{row.originalName}, #{row.storageKey}, #{row.mimeType},
                    #{row.fileSize}, #{row.sha256}, #{row.isPrimary}, 'MANUAL',
                    #{row.uploadedBy}, #{username}, #{username})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id", keyColumn = "id")
    int insertAttachment(@Param("row") Map<String, Object> row, @Param("username") String username);

    @Update("""
            UPDATE acct_record_attachment SET is_primary = 0, update_by = #{username}, update_time = NOW()
            WHERE record_id = #{recordId} AND deleted = 0 AND is_primary = 1
            """)
    void clearPrimary(@Param("recordId") Long recordId, @Param("username") String username);

    @Update("""
            UPDATE acct_record_attachment SET is_primary = 1, update_by = #{username}, update_time = NOW()
            WHERE id = #{id} AND record_id = #{recordId} AND deleted = 0
            """)
    int setPrimary(@Param("id") Long id, @Param("recordId") Long recordId, @Param("username") String username);

    @Update("""
            UPDATE acct_record_attachment SET deleted = 1, is_primary = 0,
                update_by = #{username}, update_time = NOW()
            WHERE id = #{id} AND deleted = 0
            """)
    int softDelete(@Param("id") Long id, @Param("username") String username);
}
