package com.paytech.platform.support.dataquery.domain;

import lombok.Builder;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

/** 一次执行或试运行的输入：模板内容、数据源与调用方的参数和分页。 */
@Getter
@Builder
public final class DataQueryRun {

    /** 日志与指标中的模板标识：执行为模板编码，试运行为 TRIAL。 */
    private final String templateLabel;

    /** 模板版本号；试运行为 0，游标也以它校验。 */
    private final int versionNo;

    /** 模板内容。 */
    private final QueryTemplateDraft content;

    /** 数据源，必须为 ACTIVE。 */
    private final DataSourceDetail dataSource;

    /** 调用方参数 JSON 对象，可为 null。 */
    private final JsonNode params;

    /** OFFSET 页码，可为 null。 */
    private final Integer page;

    /** OFFSET、CURSOR 页大小，可为 null。 */
    private final Integer size;

    /** 是否返回总数。 */
    private final boolean withTotal;

    /** CURSOR 游标，可为 null。 */
    private final String cursor;

    /** 操作人账号 ID，注入为 __operator.accountId。 */
    private final long operatorAccountId;

    /** 调用方服务名，只用于日志；试运行为 null。 */
    private final String callerService;
}
