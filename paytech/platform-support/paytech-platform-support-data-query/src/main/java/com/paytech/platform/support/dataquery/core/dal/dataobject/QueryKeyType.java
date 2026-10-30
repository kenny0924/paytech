package com.paytech.platform.support.dataquery.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.dataquery.core.enums.QueryKeyRole;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyTypeStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyValueType;

import lombok.Getter;
import lombok.Setter;

/** t_query_key_type 映射对象；只在 data-query 模块内使用。 */
@Getter
@Setter
public class QueryKeyType {

    /** 键类型 Snowflake 主键。 */
    private Long keyTypeId;

    /**
     * 键类型编码
     * <li>全局唯一，创建后不可修改；模板 joint_json 按编码引用。</li>
     */
    private String keyTypeCode;

    /** 显示名称。 */
    private String keyTypeName;

    /**
     * 业务分组显示名
     * <li>同名即同组；以该键类型为行主体的节点画在该分组泳道。</li>
     */
    private String groupName;

    /** 值类型。 */
    private QueryKeyValueType valueType;

    /** 编辑器中标注该键类型时的默认角色。 */
    private QueryKeyRole defaultRole;

    /** 是否可作为联合查询入口。 */
    private Boolean entryAllowed;

    /**
     * 入口值校验正则
     * <li>整串匹配，不含 ^$；为 null 时不校验格式。</li>
     */
    private String valuePattern;

    /** 入口输入提示，可为 null。 */
    private String inputHint;

    /** 业务流程顺序号，升序。 */
    private Integer sortNo;

    /** 说明，可为 null。 */
    private String description;

    /** 状态。 */
    private QueryKeyTypeStatus status;

    /**
     * 乐观并发版本
     * <li>新建为 1，每次修改、启停或重排加一。</li>
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
