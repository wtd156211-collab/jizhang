package com.scaffold.bookkeeping.mapper;

import org.apache.ibatis.annotations.*;
import java.util.Map;
import java.util.List;

@Mapper
public interface OcrTaskMapper {
    @Select("""
            SELECT id,record_id AS recordId,attachment_id AS attachmentId,provider,status,
                   normalized_result AS normalizedResult,error_message AS errorMessage,requested_by AS requestedBy,
                   DATE_FORMAT(started_at,'%Y-%m-%d %H:%i:%s') AS startedAt,
                   DATE_FORMAT(finished_at,'%Y-%m-%d %H:%i:%s') AS finishedAt,
                   DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS createTime
            FROM acct_ocr_task WHERE id=#{id}
            """)
    Map<String,Object> selectTask(@Param("id") Long id);

    @Select("""
            SELECT COUNT(*) FROM acct_ocr_task t JOIN acct_record_attachment a ON a.id=t.attachment_id AND a.record_id=t.record_id AND a.deleted=0
            WHERE t.record_id=#{recordId}
            """)
    long countTasks(@Param("recordId") Long recordId);

    @Select("""
            SELECT t.id,t.record_id AS recordId,t.attachment_id AS attachmentId,t.provider,t.status,
                   t.normalized_result AS normalizedResult,t.error_message AS errorMessage,t.requested_by AS requestedBy,
                   DATE_FORMAT(t.started_at,'%Y-%m-%d %H:%i:%s') AS startedAt,
                   DATE_FORMAT(t.finished_at,'%Y-%m-%d %H:%i:%s') AS finishedAt,
                   DATE_FORMAT(t.create_time,'%Y-%m-%d %H:%i:%s') AS createTime
            FROM acct_ocr_task t JOIN acct_record_attachment a ON a.id=t.attachment_id AND a.record_id=t.record_id AND a.deleted=0
            WHERE t.record_id=#{recordId} ORDER BY t.id DESC LIMIT #{offset},#{pageSize}
            """)
    List<Map<String,Object>> selectTasks(@Param("recordId") Long recordId, @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("SELECT status FROM acct_ocr_task WHERE id=#{id} FOR UPDATE")
    String lockStatus(@Param("id") Long id);

    @Select("""
            SELECT id FROM acct_ocr_task
            WHERE record_id=#{recordId} AND attachment_id=#{attachmentId} AND requested_by=#{userId}
              AND provider=#{provider} AND status='SUCCEEDED' AND finished_at >= DATE_SUB(NOW(),INTERVAL 10 SECOND)
              AND JSON_UNQUOTE(JSON_EXTRACT(normalized_result,'$.documentType'))=#{documentType}
              AND JSON_EXTRACT(normalized_result,'$.sourceVersion')=#{version}
            ORDER BY id DESC LIMIT 1
            """)
    Long recentSuccess(@Param("recordId") Long recordId, @Param("attachmentId") Long attachmentId,
                       @Param("userId") Long userId, @Param("provider") String provider,
                       @Param("documentType") String documentType, @Param("version") int version);

    @Insert("""
            INSERT INTO acct_ocr_task(record_id,attachment_id,provider,status,requested_by,started_at)
            VALUES (#{row.recordId},#{row.attachmentId},#{row.provider},'RUNNING',#{row.requestedBy},NOW())
            """)
    @Options(useGeneratedKeys=true,keyProperty="row.id",keyColumn="id")
    int insertTask(@Param("row") Map<String,Object> row);

    @Update("""
            UPDATE acct_ocr_task SET provider=#{provider},status=#{status},raw_result=#{raw},normalized_result=#{normalized},
                   error_message=#{error},finished_at=NOW(),update_time=NOW()
            WHERE id=#{id} AND status='RUNNING'
            """)
    int finish(@Param("id") Long id, @Param("provider") String provider, @Param("status") String status,
               @Param("raw") String raw, @Param("normalized") String normalized, @Param("error") String error);

    @Update("UPDATE acct_ocr_task SET status='CONFIRMED',update_time=NOW() WHERE id=#{id} AND status='SUCCEEDED'")
    int confirm(@Param("id") Long id);
}
