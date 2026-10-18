package com.paytech.platform.support.integration.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.api.enums.ScriptExecutionStage;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 公共脚本列表项，组合脚本与原子 API 引用。 */
@Getter
@AllArgsConstructor
public class IntegrationScriptSummaryView {

    /** 脚本编码。 */
    private final String code;

    /** 名称。 */
    private final String name;

    /** 执行阶段。 */
    private final ScriptExecutionStage stage;

    /** 参数名，按声明顺序。 */
    private final List<String> parameterNames;

    /** 引用该脚本的原子 API 编码，按编码排序。 */
    private final List<String> referencedApiCodes;

    /** 当前版本。 */
    private final int version;

    /** 最后修改时间。 */
    private final Instant updateTime;
}
