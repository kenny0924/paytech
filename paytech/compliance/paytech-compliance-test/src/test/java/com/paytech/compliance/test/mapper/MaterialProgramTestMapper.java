package com.paytech.risk.test.mapper.compliance;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 通用材料程序三张表（t_dd_program、t_dd_program_version、t_dd_program_requirement）的测试 SQL
 * <li>只按调用方给出的程序 ID 删除，不清理其他测试或会话插入的程序。</li>
 *
 * @author Kenny
 */
@TestComponent
public class MaterialProgramTestMapper {

    private final JdbcTemplate jdbc;

    public MaterialProgramTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 插入程序主记录。 */
    public void insertProgram(long programId, String programCode, String qualificationType, String status) {
        jdbc.update("INSERT INTO t_dd_program(program_id,program_code,qualification_type,status,create_time,update_time) "
            + "VALUES(?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", programId, programCode, qualificationType, status);
    }

    /** 插入程序修订；approvedTime 为空表示未批准。 */
    public void insertVersion(long versionId, long programId, int versionNo, String status, Integer activeSlot,
        String contentHash, Long approvedBy, Instant approvedTime) {
        jdbc.update("INSERT INTO t_dd_program_version(program_version_id,program_id,version_no,status,active_slot,"
            + "content_hash,approved_by,approved_time,create_time,update_time) "
            + "VALUES(?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", versionId, programId, versionNo, status,
            activeSlot, contentHash, approvedBy, approvedTime == null ? null : Timestamp.from(approvedTime));
    }

    /** 批量插入一个修订下的材料要求，每行为 {ID, 要求代码, 材料类型, 必需标记}。 */
    public void insertRequirements(long versionId, List<Object[]> rows) {
        jdbc.batchUpdate("INSERT INTO t_dd_program_requirement(program_requirement_id,program_version_id,"
            + "requirement_code,document_type,required_flag,create_time) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",
            rows.stream().map(row -> new Object[] {row[0], versionId, row[1], row[2], row[3]}).toList());
    }

    /** 删除指定程序及其全部修订和材料要求。 */
    public void deletePrograms(Collection<Long> programIds) {
        for (Long programId : programIds) {
            jdbc.update("DELETE r FROM t_dd_program_requirement r JOIN t_dd_program_version v "
                + "ON v.program_version_id=r.program_version_id WHERE v.program_id=?", programId);
            jdbc.update("DELETE FROM t_dd_program_version WHERE program_id=?", programId);
            jdbc.update("DELETE FROM t_dd_program WHERE program_id=?", programId);
        }
    }
}
