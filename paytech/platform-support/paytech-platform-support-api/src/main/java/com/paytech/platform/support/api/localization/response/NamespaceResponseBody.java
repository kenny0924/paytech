package com.paytech.platform.support.api.localization.response;

import lombok.Getter;
import lombok.Setter;

/** 应用下的一个命名空间。 */
@Getter
@Setter
public class NamespaceResponseBody {

    /** 命名空间编码，创建后不可修改。 */
    private String namespaceCode;

    /** 命名空间名称。 */
    private String namespaceName;

    /** 说明，可为 null。 */
    private String description;

    /** 排序值。 */
    private int sortOrder;

    /** 在用 key 数。 */
    private long activeKeyCount;

    /** 已废弃 key 数；两者都为 0 时才能删除。 */
    private long deprecatedKeyCount;

    /** 乐观并发版本。 */
    private int version;

    /** 最后修改人账号 ID，十进制字符串。 */
    private String updatedBy;

    /** 最后修改时间，UTC ISO-8601 微秒精度。 */
    private String updateTime;
}
