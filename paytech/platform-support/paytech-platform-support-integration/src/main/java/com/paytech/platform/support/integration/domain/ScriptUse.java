package com.paytech.platform.support.integration.domain;

import com.paytech.platform.support.api.enums.ScriptExecutionStage;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 原子 API 定义中按顺序引用的一个脚本。 */
@Getter
@AllArgsConstructor
public class ScriptUse {

    /** 本 API 内唯一的引用标识。 */
    private final String id;

    /** 被引用的脚本编码。 */
    private final String scriptCode;

    /** 执行阶段。 */
    private final ScriptExecutionStage stage;
}
