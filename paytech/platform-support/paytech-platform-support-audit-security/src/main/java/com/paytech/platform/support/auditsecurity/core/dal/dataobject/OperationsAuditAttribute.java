package com.paytech.platform.support.auditsecurity.core.dal.dataobject;

import com.paytech.platform.support.auditsecurity.domain.enums.OperationsAuditClassification;

/** 只加载允许展示的属性值，不加载 Vault 引用、敏感原文或 Hash。 */
public record OperationsAuditAttribute(
    /** 事件内不可变属性序号，重复属性键仍按此排序。 */
    int number,
    /** 原始属性键。 */
    String key,
    /** 按分类投影后的安全展示值，缺失时为 null。 */
    String value,
    /** 持久化分类；未知代码由共享 TypeHandler 拒绝。 */
    OperationsAuditClassification classification) {
    @Override
    public String toString() {
        return "OperationsAuditAttribute[protected projection]";
    }
}
