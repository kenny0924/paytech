package com.paytech.risk.test.mapper.decision;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.decision.domain.enums.DictionaryType;

/**
 * 拒绝原因与主体标签两张字典表的测试 SQL
 * <li>写入与删除只按调用方给出的编码执行，不清理其他测试或运营维护的字典。</li>
 * <li>共享锁持有方法模拟规则保存对字典的选择锁，用于观察真实数据库锁顺序。</li>
 *
 * @author Kenny
 */
@TestComponent
public class DictionaryTestMapper {

    private final JdbcTemplate jdbc;

    private final PlatformTransactionManager transactionManager;

    public DictionaryTestMapper(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
    }

    /** 字典当前行；标签的 defaultMessage 恒为 null。 */
    public record DictionaryRow(long id, String code, String name, String description, String defaultMessage,
        String status, long version, long createdBy, long updatedBy, Instant createTime, Instant updateTime) {
    }

    /** 插入一条拒绝原因当前行。 */
    public void insertReason(long id, String code, String name, String description, String defaultMessage,
        DictionaryStatus status, long version, long createdBy, long updatedBy, Instant createTime, Instant updateTime) {
        jdbc.update("INSERT INTO t_rule_reject_reason(rejection_reason_id,reason_code,reason_name,description,"
            + "default_message,status,version,created_by,updated_by,create_time,update_time) "
            + "VALUES(?,?,?,?,?,?,?,?,?,?,?)", id, code, name, description, defaultMessage, status.getCode(), version,
            createdBy, updatedBy, ts(createTime), ts(updateTime));
    }

    /** 插入一条主体标签当前行。 */
    public void insertTag(long id, String code, String name, String description, DictionaryStatus status,
        long version, long createdBy, long updatedBy, Instant createTime, Instant updateTime) {
        jdbc.update("INSERT INTO t_rule_subject_tag(tag_id,tag_code,tag_name,description,status,version,created_by,"
            + "updated_by,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?)", id, code, name, description,
            status.getCode(), version, createdBy, updatedBy, ts(createTime), ts(updateTime));
    }

    /** 按类型和编码读取当前行，不存在时为空。 */
    public Optional<DictionaryRow> find(DictionaryType type, String code) {
        List<DictionaryRow> rows = type == DictionaryType.REJECTION_REASON
            ? jdbc.query("SELECT rejection_reason_id,reason_code,reason_name,description,default_message,status,"
                + "version,created_by,updated_by,create_time,update_time FROM t_rule_reject_reason WHERE reason_code=?",
                DictionaryTestMapper::row, code)
            : jdbc.query("SELECT tag_id,tag_code,tag_name,description,NULL AS default_message,status,version,"
                + "created_by,updated_by,create_time,update_time FROM t_rule_subject_tag WHERE tag_code=?",
                DictionaryTestMapper::row, code);
        return rows.stream().findFirst();
    }

    /** 统计该类型字典的全部行，用于不带筛选的列表断言基线。 */
    public long count(DictionaryType type) {
        String table = type == DictionaryType.REJECTION_REASON ? "t_rule_reject_reason" : "t_rule_subject_tag";
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0 : count;
    }

    /** 按精确编码删除；编码由测试 Mocker 生成并登记。 */
    public void delete(DictionaryType type, String code) {
        if (type == DictionaryType.REJECTION_REASON) {
            jdbc.update("DELETE FROM t_rule_reject_reason WHERE reason_code=?", code);
        } else {
            jdbc.update("DELETE FROM t_rule_subject_tag WHERE tag_code=?", code);
        }
    }

    /** 在独立短事务中以 FOR SHARE 读取该字典行，持锁期间执行回调，回调结束后提交释放。 */
    public void holdShareLock(DictionaryType type, String code, Runnable whileLocked) {
        String sql = type == DictionaryType.REJECTION_REASON
            ? "SELECT COUNT(*) FROM t_rule_reject_reason WHERE reason_code=? FOR SHARE"
            : "SELECT COUNT(*) FROM t_rule_subject_tag WHERE tag_code=? FOR SHARE";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Long locked = jdbc.queryForObject(sql, Long.class, code);
            if (locked == null || locked != 1) {
                throw new IllegalStateException("共享锁未锁定到字典行：" + code);
            }
            whileLocked.run();
        });
    }

    private static DictionaryRow row(ResultSet rs, int rowNum) throws SQLException {
        return new DictionaryRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
            rs.getString(6), rs.getLong(7), rs.getLong(8), rs.getLong(9), instant(rs.getTimestamp(10)),
            instant(rs.getTimestamp(11)));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
