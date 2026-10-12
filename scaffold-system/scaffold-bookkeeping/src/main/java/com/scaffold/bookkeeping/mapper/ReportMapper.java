package com.scaffold.bookkeeping.mapper;

import com.scaffold.framework.datascope.DataScopeCriteria;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 金额统计只计 POSTED；主表与分类明细分别聚合，防止多明细重复累计总金额。 */
@Mapper
public interface ReportMapper {
    String PERIOD = RecordMapper.VISIBLE_SCOPE + """
            AND r.occurred_date &gt;= #{from} AND r.occurred_date &lt;= #{to}
            AND (#{recordType} IS NULL OR r.record_type = #{recordType})
            """;

    @Select("<script> SELECT DATE_FORMAT(r.occurred_date,'%Y-%m-%d') AS date, " +
            "SUM(CASE WHEN r.record_type='INCOME' THEN r.total_amount ELSE 0 END) AS income, " +
            "SUM(CASE WHEN r.record_type='EXPENSE' THEN r.total_amount ELSE 0 END) AS expense, COUNT(*) AS count " +
            "FROM acct_record r " + PERIOD + " AND r.status='POSTED' GROUP BY r.occurred_date ORDER BY r.occurred_date </script>")
    List<Map<String,Object>> daily(@Param("scope") DataScopeCriteria scope, @Param("from") LocalDate from,
                                  @Param("to") LocalDate to, @Param("recordType") String type);

    @Select("<script> SELECT r.status, COUNT(*) AS count FROM acct_record r " + PERIOD + " GROUP BY r.status </script>")
    List<Map<String,Object>> states(@Param("scope") DataScopeCriteria scope, @Param("from") LocalDate from,
                                   @Param("to") LocalDate to, @Param("recordType") String type);

    @Select("<script> SELECT d.category_id AS categoryId, COALESCE(c.category_name,'未命名分类') AS categoryName, " +
            "r.record_type AS recordType, SUM(d.amount) AS amount, COUNT(DISTINCT r.id) AS recordCount " +
            "FROM acct_record r JOIN acct_record_detail d ON d.record_id=r.id LEFT JOIN acct_category c ON c.id=d.category_id " +
            PERIOD + " AND r.status='POSTED' GROUP BY d.category_id,c.category_name,r.record_type ORDER BY amount DESC,d.category_id </script>")
    List<Map<String,Object>> categories(@Param("scope") DataScopeCriteria scope, @Param("from") LocalDate from,
                                       @Param("to") LocalDate to, @Param("recordType") String type);

    @Select("""
            <script>
            SELECT r.record_no AS recordNo, d.category_id AS categoryId, c.category_name AS categoryName,
                d.amount, u.nickname AS handlerName, dp.dept_name AS deptName, p.project_name AS projectName, d.remark
            FROM acct_record_detail d JOIN acct_record r ON r.id=d.record_id
            LEFT JOIN acct_category c ON c.id=d.category_id
            LEFT JOIN sys_user u ON u.id=d.handler_id LEFT JOIN sys_dept dp ON dp.id=d.dept_id
            LEFT JOIN acct_project p ON p.id=d.project_id
            WHERE r.id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY r.occurred_date DESC,(r.occurred_time IS NULL) ASC,r.occurred_time DESC,r.record_no DESC,d.sort,d.id
            LIMIT 50001
            </script>
            """)
    List<Map<String,Object>> exportDetails(@Param("ids") List<Long> ids);

    @Select("""
            <script>
            SELECT r.id, pm.method_name AS paymentMethodName, a.account_name AS fundAccountName,
                u.nickname AS creatorName, dp.dept_name AS deptName
            FROM acct_record r LEFT JOIN acct_payment_method pm ON pm.id=r.payment_method_id
            LEFT JOIN acct_fund_account a ON a.id=r.fund_account_id
            LEFT JOIN sys_user u ON u.id=r.created_by LEFT JOIN sys_dept dp ON dp.id=r.dept_id
            WHERE r.id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Map<String,Object>> exportLabels(@Param("ids") List<Long> ids);
}
