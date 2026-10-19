package com.paytech.commercial.test.dal;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.FundMode;
import com.paytech.commercial.api.enums.ProductCode;

/**
 * 商务测试数据的定向修改、清理与只读计数，制造负例或观察写入；只操作 mor_commercial。
 * <li>全量清理由 CommercialDataCleaner 在每例结束后执行；这里的清理只用于用例内部重建前置数据。</li>
 */
@TestComponent
public class CommercialTestMapper {
    private final JdbcTemplate jdbc;

    /** 使用被测应用的同一数据源。 */
    public CommercialTestMapper(DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    /** 推进某合同的 AGREEMENT 根修订。 */
    public void advanceAgreementRoot(long agreementId) {
        jdbc.update("UPDATE t_business_change_record SET current_version=current_version+1 "
            + "WHERE record_type='AGREEMENT' AND record_id=? AND parent_history_id IS NULL", Long.toString(agreementId));
    }

    /** 终止某合同。 */
    public void terminateAgreement(long agreementId) {
        jdbc.update("UPDATE t_agreement SET status='TERMINATED' WHERE agreement_id=?", agreementId);
    }

    /** 清空产品目录三表（计费事件、可开通项、产品）。 */
    public void clearProductCatalog() {
        jdbc.update("DELETE FROM t_product_fee_event");
        jdbc.update("DELETE FROM t_product_capability");
        jdbc.update("DELETE FROM t_product");
    }

    /** 删除一份合同的订阅、开通项及其约束。 */
    public void clearAgreementSubscriptions(long agreementId) {
        jdbc.update("DELETE FROM t_agreement_capability_limit WHERE agreement_capability_id IN "
            + "(SELECT agreement_capability_id FROM t_agreement_capability WHERE agreement_id=?)", agreementId);
        jdbc.update("DELETE FROM t_agreement_capability WHERE agreement_id=?", agreementId);
        jdbc.update("DELETE FROM t_agreement_product WHERE agreement_id=?", agreementId);
    }

    /** 某合同的订阅行数。 */
    public int countAgreementProducts(long agreementId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_agreement_product WHERE agreement_id=?", Integer.class,
            agreementId);
    }

    /** 某开通项的约束行数。 */
    public int countCapabilityLimits(long agreementCapabilityId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_agreement_capability_limit WHERE agreement_capability_id=?",
            Integer.class, agreementCapabilityId);
    }

    /** 直接改写开通项 update_time，制造以 update_time 为条件的并发冲突。 */
    public void touchAgreementCapability(long agreementCapabilityId, java.time.Instant updateTime) {
        jdbc.update("UPDATE t_agreement_capability SET update_time=? WHERE agreement_capability_id=?",
            java.sql.Timestamp.from(updateTime), agreementCapabilityId);
    }

    /** 改写合同业务模式与资金模式，制造 PSP 或本期不受理的资金模式。 */
    public void overwriteAgreementModes(long agreementId, BusinessMode businessMode, FundMode fundMode) {
        jdbc.update("UPDATE t_agreement SET business_mode=?, fund_mode=? WHERE agreement_id=?", businessMode.getCode(),
            fundMode.getCode(), agreementId);
    }

    /** 删除一份合同的某个产品订阅。 */
    public void deleteAgreementProduct(long agreementId, ProductCode productCode) {
        jdbc.update("DELETE FROM t_agreement_product WHERE agreement_id=? AND product_code=?", agreementId,
            productCode.getCode());
    }

    /** 改写开通项状态。 */
    public void overwriteCapabilityStatus(long agreementCapabilityId, AgreementCapabilityStatus status) {
        jdbc.update("UPDATE t_agreement_capability SET status=? WHERE agreement_capability_id=?", status.getCode(),
            agreementCapabilityId);
    }

    /** 改写开通项生效终点。 */
    public void overwriteCapabilityEffectiveTo(long agreementCapabilityId, java.time.Instant effectiveTo) {
        jdbc.update("UPDATE t_agreement_capability SET effective_to=? WHERE agreement_capability_id=?",
            java.sql.Timestamp.from(effectiveTo), agreementCapabilityId);
    }

    /** 删除一条开通项约束。 */
    public void deleteCapabilityLimit(long agreementCapabilityLimitId) {
        jdbc.update("DELETE FROM t_agreement_capability_limit WHERE agreement_capability_limit_id=?",
            agreementCapabilityLimitId);
    }

    /** 改写条款版本生效终点，模拟排期时关闭前一版本区间或版本已过期。 */
    public void overwriteVersionEffectiveTo(long termVersionId, java.time.Instant effectiveTo) {
        jdbc.update("UPDATE t_commercial_term_version SET effective_to=? WHERE term_version_id=?",
            java.sql.Timestamp.from(effectiveTo), termVersionId);
    }

    /** 直接写入组件计算基数原值，制造闭合枚举之外的数据库 Code。 */
    public void overwriteComponentCalculationBase(long termComponentId, String calculationBase) {
        jdbc.update("UPDATE t_commercial_term_component SET calculation_base=? WHERE term_component_id=?", calculationBase,
            termComponentId);
    }

    /** 插入一个聚合根的 BCR Root 行（无父记录），只用于发布 Handler 回读根修订。 */
    public void insertBusinessChangeRoot(long historyId, String rootType, long rootId, long currentVersion) {
        jdbc.update("INSERT INTO t_business_change_record (history_id, record_type, record_id, aggregate_root_type, "
            + "aggregate_root_id, parent_history_id, current_version, create_time, update_time) "
            + "VALUES (?, ?, ?, ?, ?, NULL, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))", historyId, rootType,
            Long.toString(rootId), rootType, Long.toString(rootId), currentVersion);
    }

    /** 某补偿任务（事件键 + 类型）当前的执行状态行；任务成功后被删除，返回空列表。 */
    public List<Map<String, Object>> compensationEvents(String eventType, String eventKey) {
        return jdbc.queryForList("SELECT id, execute_status, execute_count, last_error_code FROM t_compensation_event "
            + "WHERE event_type=? AND event_key=?", eventType, eventKey);
    }
    /** 一行合同（列名为键），不存在为 null。 */
    public Map<String, Object> agreement(long agreementId) {
        return one(jdbc.queryForList("SELECT * FROM t_agreement WHERE agreement_id=?", agreementId));
    }

    /** 按编号统计合同行数。 */
    public int countAgreementsByNo(String agreementNo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_agreement WHERE agreement_no=?", Integer.class, agreementNo);
    }

    /** 合同的参与方角色行，按主键升序。 */
    public List<Map<String, Object>> agreementParties(long agreementId) {
        return jdbc.queryForList("SELECT * FROM t_agreement_party WHERE agreement_id=? ORDER BY agreement_party_id",
            agreementId);
    }

    /** 合同的文件引用行，按主键升序。 */
    public List<Map<String, Object>> agreementDocuments(long agreementId) {
        return jdbc.queryForList(
            "SELECT * FROM t_agreement_document_ref WHERE agreement_id=? ORDER BY agreement_document_id", agreementId);
    }

    /** 合同覆盖 OE，升序。 */
    public List<String> agreementEntityNos(long agreementId) {
        return jdbc.queryForList("SELECT entity_no FROM t_agreement_operating_entity WHERE agreement_id=? ORDER BY entity_no",
            String.class, agreementId);
    }

    /** 合同的订阅行，按主键升序。 */
    public List<Map<String, Object>> agreementProducts(long agreementId) {
        return jdbc.queryForList("SELECT * FROM t_agreement_product WHERE agreement_id=? ORDER BY agreement_product_id",
            agreementId);
    }

    /** 合同订阅的产品代码，升序。 */
    public List<String> agreementProductCodes(long agreementId) {
        return jdbc.queryForList("SELECT product_code FROM t_agreement_product WHERE agreement_id=? ORDER BY product_code",
            String.class, agreementId);
    }

    /** 一行开通项，不存在为 null。 */
    public Map<String, Object> agreementCapability(long agreementCapabilityId) {
        return one(jdbc.queryForList("SELECT * FROM t_agreement_capability WHERE agreement_capability_id=?",
            agreementCapabilityId));
    }

    /** 某合同的开通项行数。 */
    public int countAgreementCapabilities(long agreementId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_agreement_capability WHERE agreement_id=?", Integer.class,
            agreementId);
    }

    /** 某开通项的约束值，形如 CODE:VALUE，升序。 */
    public List<String> capabilityLimitValues(long agreementCapabilityId) {
        return jdbc.queryForList("SELECT CONCAT(limit_code, ':', limit_value) FROM t_agreement_capability_limit "
            + "WHERE agreement_capability_id=? ORDER BY limit_code, limit_value", String.class, agreementCapabilityId);
    }

    /** 一行定价画像，不存在为 null。 */
    public Map<String, Object> pricingProfile(long pricingProfileId) {
        return one(jdbc.queryForList("SELECT * FROM t_pricing_profile WHERE pricing_profile_id=?", pricingProfileId));
    }

    /** 画像条件，形如 DIMENSION:VALUE，升序。 */
    public List<String> pricingProfileConditions(long pricingProfileId) {
        return jdbc.queryForList("SELECT CONCAT(dimension_code, ':', condition_value) FROM t_pricing_profile_cond "
            + "WHERE pricing_profile_id=? ORDER BY dimension_code, condition_value", String.class, pricingProfileId);
    }

    /** 按代码统计画像行数。 */
    public int countPricingProfilesByCode(String pricingProfileCode) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_pricing_profile WHERE pricing_profile_code=?", Integer.class,
            pricingProfileCode);
    }

    /** 一条 BCR 历史身份（列名为键），不存在为 null。 */
    public Map<String, Object> businessChangeRecord(String recordType, long recordId) {
        return one(jdbc.queryForList("SELECT * FROM t_business_change_record WHERE record_type=? AND record_id=?",
            recordType, Long.toString(recordId)));
    }

    /** 一条历史身份的全部快照，按版本升序。 */
    public List<Map<String, Object>> businessChangeVersions(long historyId) {
        return jdbc.queryForList("SELECT change_version_id, history_id, version_no, operation_type, "
            + "CAST(snapshot_json AS CHAR) AS snapshot_json, snapshot_format, create_by, create_time "
            + "FROM t_business_change_version WHERE history_id=? ORDER BY version_no", historyId);
    }

    /** 某补偿 Handler 类型的任务行数。 */
    public int countCompensationEvents(String eventType) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_compensation_event WHERE event_type=?", Integer.class, eventType);
    }

    /** 预先占用一条快照坐标，使写入命令在业务更新之后的快照插入处失败，用于验证整体回滚。 */
    public void occupyBusinessChangeVersion(long changeVersionId, long historyId, long versionNo) {
        jdbc.update("INSERT INTO t_business_change_version (change_version_id, history_id, version_no, operation_type, "
            + "snapshot_json, snapshot_format, create_by, create_time) VALUES (?, ?, ?, 'UPDATE', JSON_OBJECT(), "
            + "'ENTITY_NO', 1, UTC_TIMESTAMP(6))", changeVersionId, historyId, versionNo);
    }

    /** 改写产品的业务模式适用范围与状态，制造不可订阅的产品。 */
    public void updateProduct(String productCode, String businessModeScope, String status) {
        jdbc.update("UPDATE t_product SET business_mode_scope=?, status=? WHERE product_code=?", businessModeScope, status,
            productCode);
    }

    /** 改写可开通项目录状态。 */
    public void updateProductCapabilityStatus(String capabilityCode, String status) {
        jdbc.update("UPDATE t_product_capability SET status=? WHERE capability_code=?", status, capabilityCode);
    }

    /** 列表最多一行。 */
    private static Map<String, Object> one(List<Map<String, Object>> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("期望至多一行，实际 " + rows.size());
        }
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
