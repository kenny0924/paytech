package com.paytech.operations.console.bff.service.model.dataquery.response;

import lombok.Getter;
import lombok.Setter;

/** 模板详情：模板头与当前版本内容。 */
@Getter
@Setter
public class QueryTemplateDetailResponseBody {

    /** 模板 ID，十进制字符串。 */
    private String templateId;

    /** 模板编码。 */
    private String templateCode;

    /** 模板名称。 */
    private String templateName;

    /** 说明，可为 null。 */
    private String description;

    /** 模板状态：ACTIVE、INACTIVE。 */
    private String status;

    /** 当前生效的版本号。 */
    private int currentVersionNo;

    /** 模板乐观并发版本，写接口按此传 expectedVersion。 */
    private int version;

    /** 创建人账号 ID，十进制字符串。 */
    private String createdBy;

    /** 最后修改人账号 ID，十进制字符串。 */
    private String updatedBy;

    /** 创建时间，UTC ISO-8601 微秒精度。 */
    private String createTime;

    /** 最后修改时间，UTC ISO-8601 微秒精度。 */
    private String updateTime;

    /** 当前版本内容。 */
    private QueryTemplateVersionResponseBody currentVersion;
}
