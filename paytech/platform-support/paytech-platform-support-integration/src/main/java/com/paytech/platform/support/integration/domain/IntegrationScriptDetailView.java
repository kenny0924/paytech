package com.paytech.platform.support.integration.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.api.enums.ScriptExecutionStage;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 公共脚本详情，组合脚本与原子 API 引用。 */
@Getter
@AllArgsConstructor
public class IntegrationScriptDetailView {

    /** 脚本编码。 */
    private final String code;

    /** 名称。 */
    private final String name;

    /** 执行阶段。 */
    private final ScriptExecutionStage stage;

    /** 参数声明。 */
    private final List<ParameterDeclaration> parameters;

    /** 脚本源码。 */
    private final String sourceCode;

    /** 说明，可为 null。 */
    private final String description;

    /** 引用该脚本的原子 API 编码。 */
    private final List<String> referencedApiCodes;

    /** 当前版本。 */
    private final int version;

    /** 创建时间。 */
    private final Instant createTime;

    /** 最后修改时间。 */
    private final Instant updateTime;

    /** 最后修改的 Operations 账号。 */
    private final long updatedBy;
}
