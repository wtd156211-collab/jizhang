package com.scaffold.bookkeeping.mapper;

import com.scaffold.bookkeeping.domain.vo.ReviewHistoryVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;
import java.util.Map;

/** 历史只有追加和查询接口；读写前由服务按账目数据范围鉴权。 */
@Mapper
public interface ReviewMapper {
    @Update("""
            UPDATE acct_record SET status = #{toStatus}, version = version + 1,
                update_by = #{username}, update_time = NOW()
            WHERE id = #{id} AND deleted = 0 AND version = #{version} AND status = #{fromStatus}
            """)
    int updateStatus(@Param("id") Long id, @Param("version") int version,
                     @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                     @Param("username") String username);

    @Insert("""
            INSERT INTO acct_record_review (record_id, action, from_status, to_status, reason, operator_id)
            VALUES (#{recordId}, #{action}, #{fromStatus}, #{toStatus}, #{reason}, #{operatorId})
            """)
    int insertReview(@Param("recordId") Long recordId, @Param("action") String action,
                     @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                     @Param("reason") String reason, @Param("operatorId") Long operatorId);

    @Insert("""
            INSERT INTO acct_record_version (record_id, version_no, action, snapshot_json, reason, operator_id)
            VALUES (#{recordId}, #{versionNo}, #{action}, #{snapshotJson}, #{reason}, #{operatorId})
            """)
    int insertVersion(@Param("recordId") Long recordId, @Param("versionNo") int versionNo,
                      @Param("action") String action, @Param("snapshotJson") String snapshotJson,
                      @Param("reason") String reason, @Param("operatorId") Long operatorId);

    @Select("SELECT COUNT(*) FROM acct_record_review WHERE record_id = #{recordId}")
    long countReviews(@Param("recordId") Long recordId);

    @Select("""
            SELECT h.id, h.record_id AS recordId, h.action, h.from_status AS fromStatus, h.to_status AS toStatus,
                   h.reason, h.operator_id AS operatorId, u.username AS operatorName,
                   DATE_FORMAT(h.review_time, '%Y-%m-%d %H:%i:%s') AS reviewTime
            FROM acct_record_review h LEFT JOIN sys_user u ON u.id = h.operator_id
            WHERE h.record_id = #{recordId} ORDER BY h.review_time DESC, h.id DESC
            LIMIT #{offset}, #{pageSize}
            """)
    List<ReviewHistoryVO> selectReviews(@Param("recordId") Long recordId, @Param("offset") int offset,
                                       @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM acct_record_version WHERE record_id = #{recordId}")
    long countVersions(@Param("recordId") Long recordId);

    @Select("""
            SELECT v.id, v.record_id AS recordId, v.version_no AS versionNo, v.action, v.snapshot_json AS snapshotJson,
                   v.reason, v.operator_id AS operatorId, u.username AS operatorName,
                   DATE_FORMAT(v.create_time, '%Y-%m-%d %H:%i:%s') AS createTime
            FROM acct_record_version v LEFT JOIN sys_user u ON u.id = v.operator_id
            WHERE v.record_id = #{recordId} ORDER BY v.version_no DESC, v.id DESC
            LIMIT #{offset}, #{pageSize}
            """)
    List<Map<String, Object>> selectVersions(@Param("recordId") Long recordId, @Param("offset") int offset,
                                            @Param("pageSize") int pageSize);
}
