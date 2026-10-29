package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** DATA_QUERY 检查的触发来源；只用于日志、指标与是否写 Operations 审计，不持久化。 */
@Getter
@RequiredArgsConstructor
public enum DataQueryCheckTrigger implements CodeEnum {

    /** 动作运行新建后的首查（补偿任务）。 */
    ACTIVATION("ACTIVATION"),

    /** XXL 定时重查。 */
    SCHEDULE("SCHEDULE"),

    /** 运营在任务卡点“立即检查”。 */
    MANUAL("MANUAL");

    private final String code;
}
