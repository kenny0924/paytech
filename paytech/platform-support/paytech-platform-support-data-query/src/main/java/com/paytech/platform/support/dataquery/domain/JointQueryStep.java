package com.paytech.platform.support.dataquery.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已校验、已规范化的一步联合查询输入。 */
@Getter
@AllArgsConstructor
public final class JointQueryStep {

    /** 层号，从 0 开始。 */
    private final int depth;

    /** 本层键值：键类型不重复，值已规范化并去重。 */
    private final List<JointQueryKey> keys;

    /** 只执行这些模板；为 null 时执行本层全部参与模板。 */
    private final Set<String> templateCodes;

    /** 页面已加载的对象：行主体键类型 → 规范值；没有时为空映射。只对 depth &gt; 0 且 templateCodes 为 null 的“按自身 ID 查”任务生效。 */
    private final Map<String, Set<String>> loadedSubjects;

    /** 操作人账号 ID，注入为 __operator.accountId。 */
    private final Long operatorAccountId;
}
