package com.paytech.risk.decision.domainservice.policy;

import java.util.regex.Pattern;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.decision.domainservice.service.RuleSetManagementConflictException;
import com.paytech.risk.decision.domainservice.service.RuleSetManagementConflictException.Reason;

/** 同一 Owner 内保存、冻结、候选与删除共用的管理根不变量。 */
public final class RuleSetManagementPolicy {
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    /** 纯规则容器不持有可变状态或外部依赖。 */
    private RuleSetManagementPolicy() { }

    /** 精确且有界的业务坐标，不包含操作者或幂等键。 */
    public static String managementResourceKey(String pointCode, String code) {
        if (pointCode == null || code == null || !CODE.matcher(pointCode).matches() || !CODE.matcher(code).matches())
            throw corruption("规则集资源坐标损坏");
        return pointCode + ":" + code;
    }

    /** 拒绝已删除身份的新管理命令，编码不能被隐式复活。 */
    public static void requireConfigurable(RuleSet root) {
        if (root == null)
            throw corruption("规则集管理根损坏");
        root.verifyIntegrity();
        if (root.getStatus() == RuleSetManagementStatus.DELETED)
            throw new RuleSetManagementConflictException(Reason.DELETED, "规则集已删除，编码已保留，请使用新编码");
    }

    /** 防止管理版本溢出后使 CAS 失去意义。 */
    public static void requireIncrementable(RuleSet root) {
        if (root.getVersion() == Long.MAX_VALUE)
            throw corruption("规则集管理版本已达上限");
    }

    /** 将持久化事实不一致保留为内部错误，不伪装成业务允许。 */
    private static ErrorException corruption(String message) {
        return new ErrorException(message);
    }
}
