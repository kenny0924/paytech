package com.paytech.operations.console.bff.integration.impl;

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.RiskDictionaryModels.*;
import com.paytech.operations.console.bff.service.model.RiskConfigurationQueryModels.*;

/** 新查询合同的语义门禁，原有规则 Shape 与验证器不放宽。 */
final class RiskConfigurationResponsePolicy {
    /** 仅作为模型或转换方法的命名空间，不允许创建实例。 */
    private RiskConfigurationResponsePolicy() { }
    /** 拒绝违反已确认 Owner 响应合同的数据，使用统一上游无效响应错误。 */
    static void require(boolean valid) { if (!valid) throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE); }
    /** 验证响应业务坐标与请求期望值相同。 */
    static void equal(Object expected, Object actual) { require(Objects.equals(expected, actual)); }
    /** 仅当调用方指定筛选值时验证响应与其一致。 */
    static void filter(String expected, String actual) { if (expected != null) equal(expected, actual); }
    /** 验证十进制正 BIGINT 字符串，禁止越界或非规范表示。 */
    static void id(String value) {
        require(value != null && value.matches("[1-9][0-9]{0,18}") && new BigInteger(value).compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0);
    }
    /** 计算写入响应应返回的下一版本字符串。 */
    static String nextVersion(String value) { id(value); return new BigInteger(value).add(BigInteger.ONE).toString(); }
    /** 验证 Owner 返回的不可变业务编码格式。 */
    static void code(String value) { require(value != null && value.matches("[A-Z][A-Z0-9_]{0,63}")); }
    /** 验证必填文本的非空及 Unicode 码点长度。 */
    static void text(String value, int max) { require(value != null && !value.isBlank() && value.codePointCount(0, value.length()) <= max); }
    /** 验证 UTC 六位小数的真实时间，拒绝无效日期。 */
    static void time(String value) {
        require(value != null && value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{6}Z"));
        try { Instant.parse(value); } catch (RuntimeException failure) { require(false); }
    }
    /** 保留未发生的可空时间；非空时执行严格时间校验。 */
    static void nullableTime(String value) { if (value != null) time(value); }
    /** 验证 Owner 返回的状态或枚举代码属于合同集合。 */
    static void one(String value, String... values) { require(value != null && List.of(values).contains(value)); }
    /** 验证分页坐标、条数与总数闭合，防止错误分页作为正常结果呈现。 */
    static void page(PagingResponseBody<?> value, int page, int size) {
        require(value != null && value.items() != null && value.page() == page && value.size() == size
            && value.total() >= 0 && value.total() <= 9007199254740991L && value.items().size() <= size
            && value.items().stream().noneMatch(Objects::isNull));
        long offset = (long)(page - 1) * size;
        require(value.items().size() == Math.min(size, Math.max(0L, value.total() - offset)));
    }
    /** 校验字典当前名称、说明、状态、版本及操作者时间元数据。 */
    static void metadata(String name, String description, String status, String version, String creator, String updater, String created, String updated) {
        text(name, 100); require(name.strip().equals(name));
        require(description == null || description.codePointCount(0, description.length()) <= 500);
        one(status, "ACTIVE", "DISABLED"); id(version); id(creator); id(updater); time(created); time(updated);
    }
    /** 校验原因当前值及默认内部文案，不以历史规则内容补齐。 */
    static void reason(RejectionReasonResponseBody r) {
        require(r != null); code(r.reasonCode()); text(r.defaultMessage(), 500);
        metadata(r.name(), r.description(), r.status(), r.version(), r.createdBy(), r.updatedBy(), r.createTime(), r.updateTime());
    }
    /** 校验标签当前值的完整元数据。 */
    static void tag(SubjectTagResponseBody r) {
        require(r != null); code(r.tagCode()); metadata(r.name(), r.description(), r.status(), r.version(), r.createdBy(), r.updatedBy(), r.createTime(), r.updateTime());
    }
    /** 校验写入结果的类型、代码、预期下一版本与可信操作者。 */
    static void write(DictionaryWriteResponseBody r, String type, String code, String version, String actor) {
        require(r != null); equal(type, r.dictionaryType()); equal(code, r.code()); id(r.version()); equal(version, r.version());
        one(r.status(), "ACTIVE", "DISABLED"); id(r.changedBy()); equal(actor, r.changedBy()); time(r.changedTime());
    }
    /** 校验历史版本摘要的规则根、状态及创建信息。 */
    static void history(RuleSetHistoryItem r) {
        id(r.ruleSetId()); id(r.ruleSetVersionId()); code(r.pointCode()); code(r.ruleSetCode()); text(r.ruleSetName(), 255);
        require(r.versionNo() > 0); one(r.status(), "DRAFT", "FROZEN", "RETIRED"); id(r.createdBy()); text(r.changeReason(), 500); time(r.createTime());
    }
    /** 校验发布到点版本及规则版本的坐标和状态集合。 */
    static void release(ReleaseReference r) {
        id(r.releaseId()); text(r.releaseNo(), 64); one(r.environmentCode(), "TEST", "PRODUCTION");
        one(r.status(), "DRAFT", "VALIDATING", "SHADOWING", "REVIEW_PENDING", "APPROVED", "ACTIVE", "SUPERSEDED", "REJECTED");
        code(r.pointCode()); id(r.pointVersionId()); id(r.ruleSetVersionId()); require(r.pointVersionNo() > 0 && r.versionNo() > 0);
        nullableTime(r.activatedTime()); nullableTime(r.supersededTime());
    }
    /** 校验字典引用类型、归属、顺序和活动环境；删除根的当前指针可保留。 */
    static void references(List<DictionaryReference> rows, String type, String scope) {
        var seen = new HashSet<String>(); BigInteger last = null;
        for (var r : rows) {
            id(r.ruleSetId()); id(r.ruleSetVersionId()); code(r.pointCode()); code(r.ruleSetCode()); text(r.ruleSetName(), 255);
            code(r.ruleCode()); text(r.ruleName(), 255); require(r.versionNo() > 0); equal(type, r.referenceKind());
            one(r.ruleSetStatus(), "CONFIGURABLE", "DELETED");
            if (type.equals("REJECTION_REASON")) require(r.actionSequenceNo() == null && r.targetBindingCode() == null);
            else { require(r.actionSequenceNo() != null && r.actionSequenceNo() > 0); code(r.targetBindingCode()); }
            require(r.activeEnvironments() != null && r.activeEnvironments().equals(r.activeEnvironments().stream().distinct().sorted().toList()));
            r.activeEnvironments().forEach(e -> one(e, "TEST", "PRODUCTION"));
            if (r.ruleSetStatus().equals("DELETED")) require(r.activeEnvironments().isEmpty());
            if ("DELETED".equals(scope)) equal("DELETED", r.ruleSetStatus());
            if ("CURRENT".equals(scope) || "HISTORY".equals(scope)) { equal("CONFIGURABLE", r.ruleSetStatus()); equal("CURRENT".equals(scope), r.isCurrentConfiguration()); }
            require(seen.add(r.ruleSetVersionId() + ":" + r.ruleCode() + ":" + r.actionSequenceNo()));
            BigInteger current = new BigInteger(r.ruleSetVersionId()); require(last == null || current.compareTo(last) <= 0); last = current;
        }
    }
    /** 统一来源顺序及互斥矩阵；不把流程配置当作运行引用。 */
    static void reasonReferences(java.util.List<RejectionReasonReference> rows, String scope) {
        java.math.BigInteger lastVersion = null;
        var coordinates = new java.util.HashSet<String>();
        for (var r : rows) {
            code(r.pointCode()); equal("REJECTION_REASON", r.referenceKind());
            require(r.actionSequenceNo() == null && r.targetBindingCode() == null);
            String version;
            if ("RULE_SET".equals(r.sourceType())) {
                require(r.versionNo() != null && r.flowId() == null && r.flowCode() == null && r.flowName() == null
                    && r.flowVersionId() == null && r.flowVersionNo() == null && r.nodeKey() == null && r.nodeName() == null && r.flowStatus() == null);
                references(java.util.List.of(new DictionaryReference(r.ruleSetId(), r.pointCode(), r.ruleSetCode(), r.ruleSetName(),
                    r.ruleSetVersionId(), r.versionNo(), r.ruleCode(), r.ruleName(), r.referenceKind(), null, null, r.ruleSetStatus(),
                    r.isCurrentConfiguration(), r.activeEnvironments())), "REJECTION_REASON", scope);
                version = r.ruleSetVersionId();
                require(coordinates.add("RULE_SET:" + version + ":" + r.ruleCode()));
            } else {
                equal("FLOW_END", r.sourceType());
                require(r.ruleSetId() == null && r.ruleSetCode() == null && r.ruleSetName() == null && r.ruleSetVersionId() == null
                    && r.versionNo() == null && r.ruleCode() == null && r.ruleName() == null && r.ruleSetStatus() == null);
                id(r.flowId()); id(r.flowVersionId()); code(r.flowCode()); text(r.flowName(), 100);
                require(r.flowVersionNo() != null && r.flowVersionNo() > 0 && r.nodeKey() != null
                    && r.nodeKey().matches("[A-Za-z][A-Za-z0-9_-]{0,63}")); text(r.nodeName(), 100);
                require(java.util.Set.of("CONFIGURABLE", "DELETED").contains(r.flowStatus()) && r.activeEnvironments() != null && r.activeEnvironments().isEmpty());
                if ("CURRENT".equals(scope)) require("CONFIGURABLE".equals(r.flowStatus()) && r.isCurrentConfiguration());
                if ("HISTORY".equals(scope)) require("CONFIGURABLE".equals(r.flowStatus()) && !r.isCurrentConfiguration());
                if ("DELETED".equals(scope)) equal("DELETED", r.flowStatus());
                version = r.flowVersionId();
                require(coordinates.add("FLOW_END:" + version + ":" + r.nodeKey()));
            }
            var current = new java.math.BigInteger(version);
            require(lastVersion == null || lastVersion.compareTo(current) >= 0);
            lastVersion = current;
        }
    }
}
