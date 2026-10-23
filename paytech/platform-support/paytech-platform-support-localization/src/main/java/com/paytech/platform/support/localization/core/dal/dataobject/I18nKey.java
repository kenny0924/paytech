package com.paytech.platform.support.localization.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.localization.core.enums.KeyStatus;

import lombok.Getter;
import lombok.Setter;

/** t_i18n_key 映射对象；只在 localization 模块内使用。 */
@Getter
@Setter
public class I18nKey {

    /** 服务侧生成的 key Snowflake 主键。 */
    private Long keyId;

    /** 所属应用，取值为 t_i18n_application.application_id；与命名空间所属应用一致。 */
    private Long applicationId;

    /** 所属命名空间，取值为 t_i18n_namespace.namespace_id。 */
    private Long namespaceId;

    /** 完整 key，如 team.invite.submit；必须以所属命名空间编码加点号开头。 */
    private String keyCode;

    /** 英文基准文案，ICU MessageFormat；由导入写入，运营不能修改。 */
    private String sourceText;

    /** 开发者说明，由导入写入；NULL 表示未提供。 */
    private String description;

    /** 英文基准修订号，从 1 开始，基准文案变化时加一；翻译依据的修订号小于它即为基准已变更。 */
    private Integer sourceRevision;

    /** key 状态；只有 ACTIVE 参与翻译与发布。允许值：ACTIVE、DEPRECATED。 */
    private KeyStatus status;

    /** key 乐观并发版本，从 1 开始，每次导入修改该行时加一。 */
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
