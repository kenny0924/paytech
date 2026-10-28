package com.paytech.commercial.service.processor.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一轮批量重查的结果。 */
@Getter
@AllArgsConstructor
public class CapabilityPrerequisiteRecheckResponse {
    /** 核对的合同数 */
    private final int checkedCount;

    /** 有执行状态迁移的合同数 */
    private final int changedCount;

    /** 资格查询不可用（结论含 UNKNOWN）的合同数 */
    private final int unknownCount;

    /** 核对失败（冲突、系统错误等）的合同数，留待下一轮 */
    private final int failedCount;
}
