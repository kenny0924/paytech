package com.paytech.platform.support.api.dataquery.response;

import java.util.List;
import com.paytech.platform.support.api.dataquery.request.QueryTemplateColumn;
import com.paytech.platform.support.api.dataquery.request.QueryTemplateConstraints;
import tools.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;

/** 模板某个版本的完整内容，用于编辑器加载、版本对比与恢复预览；版本行不可变。 */
@Getter
@Setter
public class QueryTemplateVersionResponseBody {

    /** 模板 ID，十进制字符串。 */
    private String templateId;

    /** 版本号。 */
    private int versionNo;

    /** 该版本的数据源 ID，十进制字符串。 */
    private String dataSourceId;

    /** 该版本的数据源编码（数据源已删除时仍返回原编码）。 */
    private String dataSourceCode;

    /** 该版本的数据源名称。 */
    private String dataSourceName;

    /** 该版本数据源的当前状态：INACTIVE、ACTIVE、DELETED。 */
    private String dataSourceStatus;

    /** 模板正文。 */
    private String body;

    /** 参数声明（parameters_json 原样）。 */
    private JsonNode parameters;

    /** 出参列声明；没有时为空列表。 */
    private List<QueryTemplateColumn> columns;

    /** 约束。 */
    private QueryTemplateConstraints constraints;

    /** 恢复来源版本号；不是恢复生成的版本为 null。 */
    private Integer restoredFromVersionNo;

    /** 变更说明，可为 null。 */
    private String changeNote;

    /** 保存人账号 ID，十进制字符串。 */
    private String createdBy;

    /** 保存时间，UTC ISO-8601 微秒精度。 */
    private String createTime;
}
