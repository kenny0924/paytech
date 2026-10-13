package com.paytech.platform.support.integration.core.dal.dataobject;

import com.paytech.platform.support.api.enums.ScriptExecutionStage;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 接入方内可复用的请求或响应阶段脚本。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationScript {

    /** 公共脚本内部技术标识。 */
    private Long integrationScriptId;

    /** 所属接入方标识。 */
    private Long integrationId;

    /** 接入方内唯一的脚本编码，创建后不可修改。 */
    private String scriptCode;

    /** 脚本名称。 */
    private String scriptName;

    /** 脚本运行阶段。 */
    private ScriptExecutionStage executionStage;

    /** 参数声明 JSON 数组，由领域层解析。 */
    private String parameterDefinition;

    /** Aviator 脚本源码，最多 32 KiB UTF-8。 */
    private String sourceCode;

    /** 说明，未填写时为空。 */
    private String description;

    /** Operations 保存时按版本条件更新的并发版本，初始为 1。 */
    private int version;

    /** 创建该配置的 Operations 账号标识。 */
    private Long createdBy;

    /** 最后一次修改该配置的 Operations 账号标识。 */
    private Long updatedBy;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;
}
