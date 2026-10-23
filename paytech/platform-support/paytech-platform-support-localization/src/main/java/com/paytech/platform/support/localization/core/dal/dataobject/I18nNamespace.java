package com.paytech.platform.support.localization.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_i18n_namespace 映射对象；只在 localization 模块内使用。 */
@Getter
@Setter
public class I18nNamespace {

    /** 服务侧生成的命名空间 Snowflake 主键。 */
    private Long namespaceId;

    /** 所属应用，取值为 t_i18n_application.application_id。 */
    private Long applicationId;

    /** 应用内唯一的命名空间编码，小写字母开头的小写字母、数字与连字符，不含点号，创建后不可修改。 */
    private String namespaceCode;

    /** 在 Operations 页面显示的命名空间名称。 */
    private String namespaceName;

    /** 命名空间用途说明；NULL 表示未填写。 */
    private String description;

    /** 显示排序值，升序排列，取值 0 至 9999。 */
    private Integer sortOrder;

    /** 命名空间乐观并发版本，从 1 开始，每次修改加一。 */
    private Integer version;

    /** 创建该记录的操作人，取值为 t_user_account.account_id。 */
    private Long createdBy;

    /** 最后修改该记录的操作人，取值为 t_user_account.account_id。 */
    private Long updatedBy;

    /** 该记录在 Platform Support 数据库中的 UTC 微秒创建时间。 */
    private Instant createTime;

    /** 该可变记录最后一次持久化更新的 UTC 微秒时间。 */
    private Instant updateTime;
}
