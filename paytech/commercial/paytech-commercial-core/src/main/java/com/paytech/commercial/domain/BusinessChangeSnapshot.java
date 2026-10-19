package com.paytech.commercial.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.AgreementProduct;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeVersion;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

import tools.jackson.databind.json.JsonMapper;

/**
 * commercial BCR 快照信封（DATABASE_DESIGN_GUIDE §9.1、COMMERCIAL_PHYSICAL_DECISIONS §4）
 * <li>信封固定为 {@code {"_meta":{schemaVersion,triggerType,triggerId,reasonCode,integrityAlgorithm,integrityHash},"data":{..}}}；
 * data 按 record_type 白名单逐字段构造，不序列化 DataObject，不含创建 / 更新时间等技术字段。</li>
 * <li>Integrity Hash = SHA-256（UTF-8 RFC 8785）于：Domain Tag {@value #DOMAIN}、记录 / 聚合 / 父级 Tuple、history_id、version_no、
 * operation_type、create_by、create_time 与去掉 integrityHash 的信封；与 client-master 的 CLIENT 快照同一输入结构。</li>
 * <li>BIGINT 编码为十进制字符串，DECIMAL 去尾零且无指数，时间为六位微秒 UTC 字符串；纯函数，不访问数据库。</li>
 */
public final class BusinessChangeSnapshot {
    /** 服务与 Schema 版本化的 Integrity Domain Tag，与原 CCT 同值。 */
    public static final String DOMAIN = "CODE-MOR:CCT:BUSINESS_CHANGE_SNAPSHOT:1";

    /** 快照 Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 外部写命令触发。 */
    public static final String TRIGGER_COMMAND = "COMMAND";

    /** 完整性算法。 */
    public static final String INTEGRITY_ALGORITHM = "SHA-256";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final DateTimeFormatter MICROS = new DateTimeFormatterBuilder().appendInstant(6).toFormatter();

    /** 纯静态工具。 */
    private BusinessChangeSnapshot() {
    }

    /**
     * 生成带完整性哈希的快照 JSON
     *
     * @param record 已准备 Tuple 的历史身份
     * @param version 已准备 ID、版本号、操作、操作者与时间的快照行（snapshotJson 由本方法结果填充）
     * @param triggerId 同一命令所有快照共享的触发标识（正十进制）
     * @param reason 业务原因
     * @param data 白名单审计视图
     * @return 快照 JSON
     */
    public static String build(BusinessChangeRecord record, BusinessChangeVersion version, long triggerId,
        BusinessChangeReason reason, Map<String, Object> data) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("schemaVersion", SCHEMA_VERSION);
        meta.put("triggerType", TRIGGER_COMMAND);
        meta.put("triggerId", Long.toString(triggerId));
        meta.put("reasonCode", reason.getCode());
        meta.put("integrityAlgorithm", INTEGRITY_ALGORITHM);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("_meta", meta);
        envelope.put("data", data);
        meta.put("integrityHash", integrity(record, version, envelope));
        return JSON.writeValueAsString(envelope);
    }

    /**
     * 合同 Root 审计视图：t_agreement 的业务字段
     *
     * @param agreement 合同
     * @return data
     */
    public static Map<String, Object> agreementData(Agreement agreement) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agreementId", id(agreement.getAgreementId()));
        data.put("agreementNo", agreement.getAgreementNo());
        data.put("agreementName", agreement.getAgreementName());
        data.put("agreementType", code(agreement.getAgreementType()));
        data.put("businessMode", code(agreement.getBusinessMode()));
        data.put("fundMode", code(agreement.getFundMode()));
        data.put("pricingAuthority", code(agreement.getPricingAuthority()));
        data.put("referralPartnerId", id(agreement.getReferralPartnerId()));
        data.put("clientId", id(agreement.getClientId()));
        data.put("localPartnerId", id(agreement.getLocalPartnerId()));
        data.put("entityNo", agreement.getEntityNo());
        data.put("agreementFormat", code(agreement.getAgreementFormat()));
        data.put("status", code(agreement.getStatus()));
        data.put("effectiveFrom", time(agreement.getEffectiveFrom()));
        data.put("effectiveTo", time(agreement.getEffectiveTo()));
        data.put("signedTime", time(agreement.getSignedTime()));
        data.put("activatedTime", time(agreement.getActivatedTime()));
        return data;
    }

    /**
     * 合同订阅 Child 审计视图
     *
     * @param product 订阅行
     * @return data
     */
    public static Map<String, Object> agreementProductData(AgreementProduct product) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agreementProductId", id(product.getAgreementProductId()));
        data.put("agreementId", id(product.getAgreementId()));
        data.put("productCode", code(product.getProductCode()));
        return data;
    }

    /**
     * 合同开通项 Child 审计视图：开通项字段与随之冻结的约束行（按 limitCode、limitValue 排序）
     *
     * @param capability 开通项
     * @param limits 约束行
     * @return data
     */
    public static Map<String, Object> agreementCapabilityData(AgreementCapability capability,
        List<AgreementCapabilityLimit> limits) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agreementCapabilityId", id(capability.getAgreementCapabilityId()));
        data.put("agreementId", id(capability.getAgreementId()));
        data.put("entityNo", capability.getEntityNo());
        data.put("capabilityCode", capability.getCapabilityCode());
        data.put("status", code(capability.getStatus()));
        data.put("effectiveFrom", time(capability.getEffectiveFrom()));
        data.put("effectiveTo", time(capability.getEffectiveTo()));
        data.put("rollingReserveRate", decimal(capability.getRollingReserveRate()));
        data.put("rollingReserveDays", capability.getRollingReserveDays() == null ? null
            : capability.getRollingReserveDays().toString());
        List<Map<String, Object>> rows = new ArrayList<>();
        limits.stream().sorted(Comparator.comparing((AgreementCapabilityLimit limit) -> limit.getLimitCode().getCode())
            .thenComparing(AgreementCapabilityLimit::getLimitValue)).forEach(limit -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("limitCode", code(limit.getLimitCode()));
                row.put("limitValue", limit.getLimitValue());
                rows.add(row);
            });
        data.put("limits", rows);
        return data;
    }

    /**
     * 定价画像 Root 审计视图：画像字段与条件行（按维度、值排序）
     *
     * @param profile 画像
     * @param conditions 条件行
     * @return data
     */
    public static Map<String, Object> pricingProfileData(PricingProfile profile, List<PricingProfileCond> conditions) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pricingProfileId", id(profile.getPricingProfileId()));
        data.put("pricingProfileCode", profile.getPricingProfileCode());
        data.put("profileName", profile.getProfileName());
        data.put("profileKind", code(profile.getProfileKind()));
        data.put("paymentMethodCode", profile.getPaymentMethodCode());
        data.put("status", code(profile.getStatus()));
        List<Map<String, Object>> rows = new ArrayList<>();
        conditions.stream().sorted(Comparator.comparing((PricingProfileCond cond) -> cond.getDimensionCode().getCode())
            .thenComparing(PricingProfileCond::getConditionValue)).forEach(cond -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("dimensionCode", code(cond.getDimensionCode()));
                row.put("conditionValue", cond.getConditionValue());
                rows.add(row);
            });
        data.put("conditions", rows);
        return data;
    }

    /** 记录 / 聚合 / 父级 Tuple、明细结构化字段与信封的 SHA-256。 */
    static String integrity(BusinessChangeRecord record, BusinessChangeVersion version, Map<String, Object> envelope) {
        Map<String, Object> tuple = new LinkedHashMap<>();
        tuple.put("domain", DOMAIN);
        tuple.put("record_type", record.getRecordType().getCode());
        tuple.put("record_id", record.getRecordId());
        tuple.put("aggregate_root_type", record.getAggregateRootType().getCode());
        tuple.put("aggregate_root_id", record.getAggregateRootId());
        tuple.put("parent_history_id", id(record.getParentHistoryId()));
        tuple.put("history_id", id(record.getHistoryId()));
        tuple.put("version_no", id(version.getVersionNo()));
        tuple.put("operation_type", version.getOperationType().getCode());
        tuple.put("create_by", id(version.getCreateBy()));
        tuple.put("create_time", time(version.getCreateTime()));
        tuple.put("snapshot", envelope);
        try {
            byte[] canonical = Rfc8785JsonCanonicalizer.canonicalizeObject(JSON.writeValueAsBytes(tuple));
            return HexFormat.of().formatHex(MessageDigest.getInstance(INTEGRITY_ALGORITHM).digest(canonical));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new ErrorException("SHA-256 不可用", unavailable);
        }
    }

    /** BIGINT 编码为十进制字符串，空为 null。 */
    static String id(Long value) {
        return value == null ? null : value.toString();
    }

    /** DECIMAL 去尾零且无指数，空为 null。 */
    static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    /** 六位微秒 UTC 字符串，空为 null。 */
    static String time(Instant value) {
        return value == null ? null : MICROS.format(value);
    }

    /** 枚举稳定 Code，空为 null。 */
    static String code(CodeEnum value) {
        return value == null ? null : value.getCode();
    }

    /**
     * 快照 JSON 的 UTF-8 字节数，用于 64 KiB 上限检查
     *
     * @param snapshot 快照 JSON
     * @return 字节数
     */
    public static int byteLength(String snapshot) {
        return snapshot.getBytes(StandardCharsets.UTF_8).length;
    }
}
