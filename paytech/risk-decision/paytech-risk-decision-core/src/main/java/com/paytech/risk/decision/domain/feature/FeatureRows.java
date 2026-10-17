package com.paytech.risk.decision.domain.feature;

import java.time.Instant;

import com.paytech.risk.control.domain.list.RiskListView;
import com.paytech.risk.decision.core.dal.dataobject.FeatureVersion;
import com.paytech.risk.decision.core.dal.dataobject.ListFeatureDefinition;
import com.paytech.risk.decision.core.dal.dataobject.VelocityFeatureDefinition;
import com.paytech.risk.decision.domain.enums.ExecutionVersionStatus;
import com.paytech.risk.decision.domain.variable.RiskVariable.Definition;

/**
 * 创建 Feature 变量时需要原子写入的不可变 Feature Version 及其参数行
 *
 * @param featureVersion Feature Version；编码沿用变量编码，版本号固定为 1
 * @param velocity 实时累计参数；名单命中变量为空
 * @param list 名单命中参数；实时累计变量为空
 */
public record FeatureRows(FeatureVersion featureVersion, VelocityFeatureDefinition velocity, ListFeatureDefinition list) {

    /**
     * 构造 Feature 行
     *
     * @param definition 已分配 Feature Version ID 的变量结构
     * @param variableName 变量展示名称，作为 Feature 名称
     * @param spec 已校验的 Feature 参数
     * @param list 名单命中变量引用的已校验名单；其他为空
     * @param time 创建时间
     * @return 非 Feature 变量返回空
     */
    public static FeatureRows build(Definition definition, String variableName, FeatureSpec spec, RiskListView list,
        Instant time) {
        if (!definition.featureBacked()) {
            return null;
        }
        long id = definition.featureVersionId();
        if (spec instanceof FeatureSpec.Velocity velocity) {
            return new FeatureRows(version(definition, variableName, "VELOCITY", FeatureHandlers.VELOCITY_HANDLER,
                FeatureHandlers.VELOCITY_HASH, time), velocityRow(id, velocity, time), null);
        }
        FeatureSpec.ListMembership membership = (FeatureSpec.ListMembership)spec;
        ListFeatureDefinition row = new ListFeatureDefinition();
        row.setFeatureVersionId(id);
        row.setListId(list.listId());
        row.setSubjectType(membership.subjectType());
        row.setCreateTime(time);
        return new FeatureRows(version(definition, variableName, "LIST", FeatureHandlers.LIST_HANDLER,
            FeatureHandlers.LIST_HASH, time), null, row);
    }

    /** 构造启用的 Feature Version。 */
    static FeatureVersion version(Definition definition, String variableName, String category, String handler,
        String hash, Instant time) {
        return new FeatureVersion(definition.featureVersionId(), definition.variableCode(), variableName, category, 1,
            definition.valueType(), handler, FeatureHandlers.IMPLEMENTATION_VERSION, hash, FeatureHandlers.TIMEOUT_MILLIS,
            ExecutionVersionStatus.ACTIVE, time, time);
    }

    /** 构造实时累计参数行。 */
    static VelocityFeatureDefinition velocityRow(long id, FeatureSpec.Velocity spec, Instant time) {
        VelocityFeatureDefinition row = new VelocityFeatureDefinition();
        row.setFeatureVersionId(id);
        row.setEventCode(spec.eventCode());
        row.setAnchorSubjectType(spec.anchorSubjectType());
        row.setMetric(spec.metric());
        row.setDistinctSubjectType(spec.distinctSubjectType());
        row.setWindowSeconds(spec.windowSeconds());
        row.setIncludeCurrent(spec.includeCurrent());
        row.setCreateTime(time);
        return row;
    }
}
