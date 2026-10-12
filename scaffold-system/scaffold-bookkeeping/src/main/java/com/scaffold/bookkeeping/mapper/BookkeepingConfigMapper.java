package com.scaffold.bookkeeping.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;
import com.scaffold.framework.datascope.DataScopeCriteria;

/** 记账基础配置查询与写入。SQL 使用参数绑定，避免把筛选条件拼接到语句中。 */
@Mapper
public interface BookkeepingConfigMapper {

    @Select("""
            <script>
            SELECT id, username, nickname FROM sys_user WHERE deleted=0 AND status=1
            AND (#{scope.allScope}=TRUE OR id=#{scope.userId}
            <if test="scope.deptIds != null and scope.deptIds.size > 0">OR dept_id IN
            <foreach collection="scope.deptIds" item="id" open="(" separator="," close=")">#{id}</foreach></if>)
            ORDER BY id
            </script>
            """)
    List<Map<String,Object>> selectEntryUsers(@Param("scope") DataScopeCriteria scope);

    @Select("""
            <script>
            SELECT id,dept_name AS name,parent_id AS parentId,status FROM sys_dept WHERE deleted=0 AND status=1
            AND (#{scope.allScope}=TRUE OR id=(SELECT dept_id FROM sys_user WHERE id=#{scope.userId} AND deleted=0 AND status=1)
            <if test="scope.deptIds != null and scope.deptIds.size > 0">OR id IN
            <foreach collection="scope.deptIds" item="id" open="(" separator="," close=")">#{id}</foreach></if>)
            ORDER BY sort,id
            </script>
            """)
    List<Map<String,Object>> selectEntryDepts(@Param("scope") DataScopeCriteria scope);

    @Select("""
            <script>
            SELECT COUNT(*) FROM acct_category
            WHERE deleted = 0
              AND (#{categoryType} IS NULL OR category_type = #{categoryType})
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR category_code LIKE CONCAT('%', #{keyword}, '%')
                   OR category_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR status = #{status})
            </script>
            """)
    long countCategories(@Param("categoryType") String categoryType,
                         @Param("keyword") String keyword,
                         @Param("status") Integer status);

    @Select("""
            <script>
            SELECT id, category_type AS categoryType, category_code AS categoryCode,
                   category_name AS categoryName, parent_id AS parentId, sort, status,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS createTime,
                   DATE_FORMAT(update_time, '%Y-%m-%d %H:%i') AS updateTime
            FROM acct_category
            WHERE deleted = 0
              AND (#{categoryType} IS NULL OR category_type = #{categoryType})
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR category_code LIKE CONCAT('%', #{keyword}, '%')
                   OR category_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR status = #{status})
            ORDER BY sort, id
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectCategories(@Param("categoryType") String categoryType,
                                               @Param("keyword") String keyword,
                                               @Param("status") Integer status,
                                               @Param("offset") int offset,
                                               @Param("pageSize") int pageSize);

    // 软删除仍保留编码，查重范围与数据库唯一索引一致。
    @Select("SELECT COUNT(*) FROM acct_category WHERE category_code = #{code} AND (#{excludeId} IS NULL OR id <> #{excludeId})")
    long countCategoryCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM acct_category WHERE category_type = #{categoryType} AND category_name = #{name} AND deleted = 0 AND (#{excludeId} IS NULL OR id <> #{excludeId})")
    long countCategoryName(@Param("categoryType") String categoryType, @Param("name") String name, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM acct_category WHERE id = #{id} AND deleted = 0")
    long countCategory(@Param("id") Long id);

    @Select("SELECT id, category_type AS categoryType, parent_id AS parentId FROM acct_category WHERE id = #{id} AND deleted = 0")
    Map<String, Object> selectCategory(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM acct_category WHERE parent_id = #{id} AND deleted = 0")
    long countCategoryChildren(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM acct_record_detail d JOIN acct_record r ON r.id = d.record_id WHERE d.category_id = #{id}")
    long countCategoryReferences(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM acct_record_detail d JOIN acct_record r ON r.id = d.record_id WHERE d.category_id = #{id} AND r.deleted = 0 AND r.status IN ('DRAFT', 'PENDING_REVIEW', 'RETURNED', 'WITHDRAWN')")
    long countActiveCategoryReferences(@Param("id") Long id);

    @Insert("""
            INSERT INTO acct_category
                (category_type, category_code, category_name, parent_id, sort, status, create_by, update_by)
            VALUES
                (#{row.categoryType}, #{row.categoryCode}, #{row.categoryName}, #{row.parentId},
                 #{row.sort}, #{row.status}, #{username}, #{username})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id", keyColumn = "id")
    int insertCategory(@Param("row") Map<String, Object> row, @Param("username") String username);

    @Update("""
            UPDATE acct_category
            SET category_type = #{row.categoryType}, category_code = #{row.categoryCode},
                category_name = #{row.categoryName}, parent_id = #{row.parentId}, sort = #{row.sort},
                status = #{row.status}, update_by = #{username}, update_time = NOW()
            WHERE id = #{id} AND deleted = 0
            """)
    int updateCategory(@Param("id") Long id, @Param("row") Map<String, Object> row, @Param("username") String username);

    @Update("UPDATE acct_category SET deleted = 1, update_by = #{username}, update_time = NOW() WHERE id = #{id} AND deleted = 0")
    int softDeleteCategory(@Param("id") Long id, @Param("username") String username);

    @Select("""
            <script>
            SELECT COUNT(*) FROM acct_payment_method
            WHERE deleted = 0
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR method_code LIKE CONCAT('%', #{keyword}, '%')
                   OR method_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR status = #{status})
            </script>
            """)
    long countPaymentMethods(@Param("keyword") String keyword, @Param("status") Integer status);

    @Select("""
            <script>
            SELECT id, method_code AS methodCode, method_name AS methodName, sort, status,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS createTime,
                   DATE_FORMAT(update_time, '%Y-%m-%d %H:%i') AS updateTime
            FROM acct_payment_method
            WHERE deleted = 0
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR method_code LIKE CONCAT('%', #{keyword}, '%')
                   OR method_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR status = #{status})
            ORDER BY sort, id
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectPaymentMethods(@Param("keyword") String keyword,
                                                   @Param("status") Integer status,
                                                   @Param("offset") int offset,
                                                   @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM acct_payment_method WHERE method_code = #{code} AND (#{excludeId} IS NULL OR id <> #{excludeId})")
    long countPaymentMethodCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM acct_payment_method WHERE method_name = #{name} AND deleted = 0 AND (#{excludeId} IS NULL OR id <> #{excludeId})")
    long countPaymentMethodName(@Param("name") String name, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM acct_payment_method WHERE id = #{id} AND deleted = 0")
    long countPaymentMethod(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM acct_record WHERE payment_method_id = #{id}")
    long countPaymentMethodReferences(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM acct_record WHERE payment_method_id = #{id} AND deleted = 0 AND status IN ('DRAFT', 'PENDING_REVIEW', 'RETURNED', 'WITHDRAWN')")
    long countActivePaymentMethodReferences(@Param("id") Long id);

    @Insert("""
            INSERT INTO acct_payment_method
                (method_code, method_name, sort, status, create_by, update_by)
            VALUES (#{row.methodCode}, #{row.methodName}, #{row.sort}, #{row.status}, #{username}, #{username})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id", keyColumn = "id")
    int insertPaymentMethod(@Param("row") Map<String, Object> row, @Param("username") String username);

    @Update("""
            UPDATE acct_payment_method
            SET method_code = #{row.methodCode}, method_name = #{row.methodName}, sort = #{row.sort},
                status = #{row.status}, update_by = #{username}, update_time = NOW()
            WHERE id = #{id} AND deleted = 0
            """)
    int updatePaymentMethod(@Param("id") Long id, @Param("row") Map<String, Object> row, @Param("username") String username);

    @Update("UPDATE acct_payment_method SET deleted = 1, update_by = #{username}, update_time = NOW() WHERE id = #{id} AND deleted = 0")
    int softDeletePaymentMethod(@Param("id") Long id, @Param("username") String username);

    @Select("""
            <script>
            SELECT COUNT(*) FROM acct_fund_account
            WHERE deleted = 0
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR account_code LIKE CONCAT('%', #{keyword}, '%')
                   OR account_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR status = #{status})
            </script>
            """)
    long countFundAccounts(@Param("keyword") String keyword, @Param("status") Integer status);

    @Select("""
            <script>
            SELECT id, account_code AS accountCode, account_name AS accountName, account_type AS accountType,
                   sort, status, remark,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS createTime,
                   DATE_FORMAT(update_time, '%Y-%m-%d %H:%i') AS updateTime
            FROM acct_fund_account
            WHERE deleted = 0
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR account_code LIKE CONCAT('%', #{keyword}, '%')
                   OR account_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR status = #{status})
            ORDER BY sort, id
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectFundAccounts(@Param("keyword") String keyword,
                                                 @Param("status") Integer status,
                                                 @Param("offset") int offset,
                                                 @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM acct_fund_account WHERE account_code = #{code} AND (#{excludeId} IS NULL OR id <> #{excludeId})")
    long countFundAccountCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM acct_fund_account WHERE account_name = #{name} AND deleted = 0 AND (#{excludeId} IS NULL OR id <> #{excludeId})")
    long countFundAccountName(@Param("name") String name, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM acct_fund_account WHERE id = #{id} AND deleted = 0")
    long countFundAccount(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM acct_record WHERE fund_account_id = #{id}")
    long countFundAccountReferences(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM acct_record WHERE fund_account_id = #{id} AND deleted = 0 AND status IN ('DRAFT', 'PENDING_REVIEW', 'RETURNED', 'WITHDRAWN')")
    long countActiveFundAccountReferences(@Param("id") Long id);

    @Insert("""
            INSERT INTO acct_fund_account
                (account_code, account_name, account_type, sort, status, remark, create_by, update_by)
            VALUES (#{row.accountCode}, #{row.accountName}, #{row.accountType}, #{row.sort},
                    #{row.status}, #{row.remark}, #{username}, #{username})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id", keyColumn = "id")
    int insertFundAccount(@Param("row") Map<String, Object> row, @Param("username") String username);

    @Update("""
            UPDATE acct_fund_account
            SET account_code = #{row.accountCode}, account_name = #{row.accountName}, account_type = #{row.accountType},
                sort = #{row.sort}, status = #{row.status}, remark = #{row.remark},
                update_by = #{username}, update_time = NOW()
            WHERE id = #{id} AND deleted = 0
            """)
    int updateFundAccount(@Param("id") Long id, @Param("row") Map<String, Object> row, @Param("username") String username);

    @Update("UPDATE acct_fund_account SET deleted = 1, update_by = #{username}, update_time = NOW() WHERE id = #{id} AND deleted = 0")
    int softDeleteFundAccount(@Param("id") Long id, @Param("username") String username);

    @Select("""
            <script>
            SELECT COUNT(*) FROM acct_project
            WHERE deleted = 0
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR project_code LIKE CONCAT('%', #{keyword}, '%')
                   OR project_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR status = #{status})
            </script>
            """)
    long countProjects(@Param("keyword") String keyword, @Param("status") Integer status);

    @Select("""
            <script>
            SELECT id, project_code AS projectCode, project_name AS projectName, sort, status, remark,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS createTime,
                   DATE_FORMAT(update_time, '%Y-%m-%d %H:%i') AS updateTime
            FROM acct_project
            WHERE deleted = 0
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR project_code LIKE CONCAT('%', #{keyword}, '%')
                   OR project_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR status = #{status})
            ORDER BY sort, id
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectProjects(@Param("keyword") String keyword,
                                             @Param("status") Integer status,
                                             @Param("offset") int offset,
                                             @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM acct_project WHERE project_code = #{code} AND (#{excludeId} IS NULL OR id <> #{excludeId})")
    long countProjectCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM acct_project WHERE project_name = #{name} AND deleted = 0 AND (#{excludeId} IS NULL OR id <> #{excludeId})")
    long countProjectName(@Param("name") String name, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM acct_project WHERE id = #{id} AND deleted = 0")
    long countProject(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM acct_record_detail WHERE project_id = #{id}")
    long countProjectReferences(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM acct_record_detail d JOIN acct_record r ON r.id = d.record_id WHERE d.project_id = #{id} AND r.deleted = 0 AND r.status IN ('DRAFT', 'PENDING_REVIEW', 'RETURNED', 'WITHDRAWN')")
    long countActiveProjectReferences(@Param("id") Long id);

    @Insert("""
            INSERT INTO acct_project
                (project_code, project_name, sort, status, remark, create_by, update_by)
            VALUES (#{row.projectCode}, #{row.projectName}, #{row.sort}, #{row.status},
                    #{row.remark}, #{username}, #{username})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id", keyColumn = "id")
    int insertProject(@Param("row") Map<String, Object> row, @Param("username") String username);

    @Update("""
            UPDATE acct_project
            SET project_code = #{row.projectCode}, project_name = #{row.projectName}, sort = #{row.sort},
                status = #{row.status}, remark = #{row.remark}, update_by = #{username}, update_time = NOW()
            WHERE id = #{id} AND deleted = 0
            """)
    int updateProject(@Param("id") Long id, @Param("row") Map<String, Object> row, @Param("username") String username);

    @Update("UPDATE acct_project SET deleted = 1, update_by = #{username}, update_time = NOW() WHERE id = #{id} AND deleted = 0")
    int softDeleteProject(@Param("id") Long id, @Param("username") String username);
}
