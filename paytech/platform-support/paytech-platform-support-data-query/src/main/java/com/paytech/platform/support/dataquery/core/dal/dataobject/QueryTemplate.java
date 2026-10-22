package com.paytech.platform.support.dataquery.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;

import lombok.Getter;
import lombok.Setter;

/** t_query_template 映射对象；只在 data-query 模块内使用。 */
@Getter
@Setter
public class QueryTemplate {

    /** 模板 Snowflake 主键。 */
    private Long templateId;

    /**
     * 模板编码
     * <li>全局唯一，创建后不可修改；执行接口按它定位模板。</li>
     */
    private String templateCode;

    /** 模板名称。 */
    private String templateName;

    /** 说明，可为 null。 */
    private String description;

    /**
     * 当前版本的数据源 ID
     * <li>与当前版本行的 dataSourceId 同事务写入，供列表筛选与删除数据源的引用检查。</li>
     */
    private Long dataSourceId;

    /** 状态。 */
    private QueryTemplateStatus status;

    /** 当前生效的版本号。 */
    private Integer currentVersionNo;

    /**
     * 乐观并发版本
     * <li>新建为 1，每次保存、恢复或启停加一。</li>
     */
    private Integer version;

    /** 创建人账号 ID。 */
    private Long createdBy;

    /** 最后修改人账号 ID。 */
    private Long updatedBy;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}
