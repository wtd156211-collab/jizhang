package com.scaffold.bookkeeping.mapper;

import com.scaffold.framework.datascope.DataScopeCriteria;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/** 账目主表与明细表 Mapper。所有查询均使用参数绑定并带数据范围条件。 */
@Mapper
public interface RecordMapper {

    // 列表、统计和导出共用可见范围条件；仅使用绑定参数。
    String VISIBLE_SCOPE = """
            WHERE r.deleted = 0 AND (#{scope.allScope} = TRUE OR r.created_by = #{scope.userId}
                   <if test="scope.deptIds != null and scope.deptIds.size > 0">
                   OR r.dept_id IN
                   <foreach collection="scope.deptIds" item="deptId" open="(" separator="," close=")">#{deptId}</foreach>
                   </if>)
            """;

    String LIST_WHERE = VISIBLE_SCOPE + """
              AND (#{recordType} IS NULL OR r.record_type = #{recordType})
              AND (#{status} IS NULL OR r.status = #{status})
              AND (#{occurredFrom} IS NULL OR r.occurred_date &gt;= #{occurredFrom})
              AND (#{occurredTo} IS NULL OR r.occurred_date &lt;= #{occurredTo})
              AND (#{categoryId} IS NULL OR EXISTS (SELECT 1 FROM acct_record_detail d WHERE d.record_id = r.id AND d.category_id = #{categoryId}))
              AND (#{keyword} IS NULL OR #{keyword} = '' OR r.record_no LIKE CONCAT('%', #{keyword}, '%') OR r.summary LIKE CONCAT('%', #{keyword}, '%'))
            """;

    String RECORD_BY_SCOPE = """
            <script>
            SELECT id, record_no AS recordNo, record_type AS recordType, status, summary,
                   DATE_FORMAT(occurred_date, '%Y-%m-%d') AS occurredDate,
                   TIME_FORMAT(occurred_time, '%H:%i:%s') AS occurredTime,
                   total_amount AS totalAmount, payment_method_id AS paymentMethodId,
                   fund_account_id AS fundAccountId, created_by AS createdBy, dept_id AS deptId,
                   version, deleted, remark, create_by AS createBy, update_by AS updateBy,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime,
                   DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS updateTime
            FROM acct_record WHERE id = #{id}
              AND (#{scope.allScope} = TRUE OR created_by = #{scope.userId}
                   <if test="scope.deptIds != null and scope.deptIds.size > 0">
                   OR dept_id IN
                   <foreach collection="scope.deptIds" item="deptId" open="(" separator="," close=")">#{deptId}</foreach>
                   </if>)
            """;

    @Select(RECORD_BY_SCOPE + " AND deleted = 0 LIMIT 1 FOR UPDATE </script>")
    Map<String, Object> lockRecordForWrite(@Param("id") Long id, @Param("scope") DataScopeCriteria scope);

    @Select("SELECT id FROM sys_user WHERE username = #{username} AND deleted = 0 AND status = 1 LIMIT 1")
    Long selectUserIdByUsername(@Param("username") String username);

    @Select("SELECT dept_id FROM sys_user WHERE username = #{username} AND deleted = 0 AND status = 1 LIMIT 1")
    Long selectUserDeptIdByUsername(@Param("username") String username);

    @Select("SELECT COUNT(*) FROM sys_user WHERE id = #{id} AND deleted = 0 AND status = 1")
    long countActiveHandler(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM sys_dept WHERE id = #{id} AND deleted = 0 AND status = 1")
    long countActiveDept(@Param("id") Long id);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM acct_record r
            """ + LIST_WHERE + """
            </script>
            """)
    long countRecords(@Param("scope") DataScopeCriteria scope,
                      @Param("recordType") String recordType,
                      @Param("status") String status,
                      @Param("occurredFrom") Object occurredFrom,
                      @Param("occurredTo") Object occurredTo,
                      @Param("categoryId") Long categoryId,
                      @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT r.id, r.record_no AS recordNo, r.record_type AS recordType, r.status,
                   r.summary, DATE_FORMAT(r.occurred_date, '%Y-%m-%d') AS occurredDate,
                   TIME_FORMAT(r.occurred_time, '%H:%i:%s') AS occurredTime,
                   r.total_amount AS totalAmount, r.payment_method_id AS paymentMethodId,
                   r.fund_account_id AS fundAccountId, r.created_by AS createdBy, r.dept_id AS deptId,
                   r.version, r.remark,
                   (SELECT COUNT(*) FROM acct_record_detail d WHERE d.record_id = r.id) AS detailCount,
                   DATE_FORMAT(r.create_time, '%Y-%m-%d %H:%i') AS createTime,
                   DATE_FORMAT(r.update_time, '%Y-%m-%d %H:%i') AS updateTime
            FROM acct_record r
            """ + LIST_WHERE + """
            ORDER BY r.occurred_date DESC, (r.occurred_time IS NULL) ASC, r.occurred_time DESC, r.record_no DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectRecords(@Param("scope") DataScopeCriteria scope,
                                             @Param("recordType") String recordType,
                                             @Param("status") String status,
                                             @Param("occurredFrom") Object occurredFrom,
                                             @Param("occurredTo") Object occurredTo,
                                             @Param("categoryId") Long categoryId,
                                             @Param("keyword") String keyword,
                                             @Param("offset") int offset,
                                             @Param("pageSize") int pageSize);

    @Select(RECORD_BY_SCOPE + " AND deleted = 0 LIMIT 1 </script>")
    Map<String, Object> selectRecordForWrite(@Param("id") Long id, @Param("scope") DataScopeCriteria scope);

    // 与普通查询相同的数据范围，但允许软删账目的历史查询；不会暴露软删账目给普通列表。
    @Select(RECORD_BY_SCOPE + " LIMIT 1 </script>")
    Map<String, Object> selectRecordForHistory(@Param("id") Long id, @Param("scope") DataScopeCriteria scope);

    @Select("""
            SELECT d.id, d.record_id AS recordId, d.category_id AS categoryId,
                   c.category_name AS categoryName, d.amount, d.handler_id AS handlerId,
                   d.dept_id AS deptId, d.project_id AS projectId, d.remark, d.sort
            FROM acct_record_detail d
            LEFT JOIN acct_category c ON c.id = d.category_id
            WHERE d.record_id = #{recordId}
            ORDER BY d.sort, d.id
            """)
    List<Map<String, Object>> selectDetails(@Param("recordId") Long recordId);

    @Select("""
            <script>
            SELECT COUNT(*) FROM acct_category
            WHERE deleted = 0 AND status = 1 AND category_type = #{recordType}
              AND id IN <foreach collection="categoryIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countActiveCategories(@Param("categoryIds") List<Long> categoryIds, @Param("recordType") String recordType);

    @Select("SELECT COUNT(*) FROM acct_payment_method WHERE id = #{id} AND deleted = 0 AND status = 1")
    long countActivePaymentMethod(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM acct_fund_account WHERE id = #{id} AND deleted = 0 AND status = 1")
    long countActiveFundAccount(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM acct_project WHERE id = #{id} AND deleted = 0 AND status = 1")
    long countActiveProject(@Param("id") Long id);

    @Insert("""
            INSERT INTO acct_record
                (record_no, record_type, status, summary, occurred_date, occurred_time, total_amount,
                 payment_method_id, fund_account_id, created_by, dept_id, version, remark, create_by, update_by)
            VALUES (#{row.recordNo}, #{row.recordType}, 'DRAFT', #{row.summary}, #{row.occurredDate},
                    #{row.occurredTime}, #{row.totalAmount}, #{row.paymentMethodId}, #{row.fundAccountId},
                    #{row.createdBy}, #{row.deptId}, 0, #{row.remark}, #{username}, #{username})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id", keyColumn = "id")
    int insertRecord(@Param("row") Map<String, Object> row, @Param("username") String username);

    @Insert("""
            INSERT INTO acct_record_detail
                (record_id, category_id, amount, handler_id, dept_id, project_id, remark, sort, create_by, update_by)
            VALUES (#{row.recordId}, #{row.categoryId}, #{row.amount}, #{row.handlerId}, #{row.deptId},
                    #{row.projectId}, #{row.remark}, #{row.sort}, #{username}, #{username})
            """)
    int insertDetail(@Param("row") Map<String, Object> row, @Param("username") String username);

    @Update("""
            UPDATE acct_record
            SET record_type = #{row.recordType}, summary = #{row.summary}, occurred_date = #{row.occurredDate},
                occurred_time = #{row.occurredTime}, total_amount = #{row.totalAmount},
                payment_method_id = #{row.paymentMethodId}, fund_account_id = #{row.fundAccountId},
                dept_id = #{row.deptId}, remark = #{row.remark}, version = version + 1,
                update_by = #{username}, update_time = NOW()
            WHERE id = #{id} AND deleted = 0 AND version = #{version} AND status IN ('DRAFT', 'RETURNED', 'WITHDRAWN')
            """)
    int updateRecord(@Param("row") Map<String, Object> row,
                     @Param("id") Long id,
                     @Param("version") Integer version,
                     @Param("username") String username);

    @Delete("DELETE FROM acct_record_detail WHERE record_id = #{recordId}")
    int deleteDetails(@Param("recordId") Long recordId);

    @Update("""
            UPDATE acct_record SET deleted = 1, version = version + 1,
                update_by = #{username}, update_time = NOW()
            WHERE id = #{id} AND deleted = 0 AND status IN ('DRAFT', 'RETURNED', 'WITHDRAWN')
            """)
    int softDeleteRecord(@Param("id") Long id, @Param("username") String username);
}
