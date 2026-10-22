package com.paytech.platform.support.api.dataquery.response;

import lombok.Getter;
import lombok.Setter;

/** 版本列表中的一项；内容通过版本详情接口读取。 */
@Getter
@Setter
public class QueryTemplateVersionSummaryResponseBody {

    /** 版本号。 */
    private int versionNo;

    /** 该版本的数据源 ID，十进制字符串。 */
    private String dataSourceId;

    /** 该版本的数据源编码。 */
    private String dataSourceCode;

    /** 恢复来源版本号；不是恢复生成的版本为 null。 */
    private Integer restoredFromVersionNo;

    /** 变更说明，可为 null。 */
    private String changeNote;

    /** 保存人账号 ID，十进制字符串。 */
    private String createdBy;

    /** 保存时间，UTC ISO-8601 微秒精度。 */
    private String createTime;
}
