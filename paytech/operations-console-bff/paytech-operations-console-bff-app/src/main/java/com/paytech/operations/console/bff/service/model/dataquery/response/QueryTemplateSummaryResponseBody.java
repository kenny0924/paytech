package com.paytech.operations.console.bff.service.model.dataquery.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 模板列表中的一项，含当前版本的数据源、分页方式与参数摘要。 */
@Getter
@Setter
public class QueryTemplateSummaryResponseBody {

    /** 模板 ID，十进制字符串。 */
    private String templateId;

    /** 模板编码。 */
    private String templateCode;

    /** 模板名称。 */
    private String templateName;

    /** 说明，可为 null。 */
    private String description;

    /** 当前版本的数据源 ID，十进制字符串。 */
    private String dataSourceId;

    /** 当前版本的数据源编码。 */
    private String dataSourceCode;

    /** 当前版本的数据源名称。 */
    private String dataSourceName;

    /** 模板状态：ACTIVE、INACTIVE。 */
    private String status;

    /** 当前生效的版本号。 */
    private int currentVersionNo;

    /** 当前版本的分页方式：NONE、OFFSET、CURSOR。 */
    private String pagingMode;

    /** 当前版本的参数摘要，按声明顺序。 */
    private List<QueryParameterSummary> parameters;

    /** 模板乐观并发版本。 */
    private int version;

    /** 最后修改人账号 ID，十进制字符串。 */
    private String updatedBy;

    /** 最后修改时间，UTC ISO-8601 微秒精度。 */
    private String updateTime;
}
