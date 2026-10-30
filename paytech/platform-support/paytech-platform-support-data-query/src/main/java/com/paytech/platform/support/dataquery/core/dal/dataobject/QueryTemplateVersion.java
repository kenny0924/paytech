package com.paytech.platform.support.dataquery.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_query_template_version 映射对象；版本行创建后不可变，只在 data-query 模块内使用。 */
@Getter
@Setter
public class QueryTemplateVersion {

    /** 模板版本 Snowflake 主键。 */
    private Long templateVersionId;

    /** 所属模板 ID。 */
    private Long templateId;

    /** 模板内版本号，从 1 开始。 */
    private Integer versionNo;

    /** 该版本的数据源 ID。 */
    private Long dataSourceId;

    /** 模板正文。 */
    private String body;

    /** 参数声明 JSON 数组原文；无参数时为 []。 */
    private String parametersJson;

    /** 出参列声明 JSON 数组原文；为 null 表示全部列原样返回。 */
    private String columnsJson;

    /** 约束 JSON 对象原文。 */
    private String constraintsJson;

    /** 联合查询配置 JSON 对象原文；为 null 表示该版本不参与联合查询。 */
    private String jointJson;

    /** 恢复来源版本号；不是恢复生成时为 null。 */
    private Integer restoredFromVersionNo;

    /** 变更说明，可为 null。 */
    private String changeNote;

    /** 保存人账号 ID。 */
    private Long createdBy;

    /** 保存时间（UTC）。 */
    private Instant createTime;
}
