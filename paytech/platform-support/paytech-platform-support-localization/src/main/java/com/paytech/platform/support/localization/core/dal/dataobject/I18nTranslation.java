package com.paytech.platform.support.localization.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_i18n_translation 映射对象；只在 localization 模块内使用。 */
@Getter
@Setter
public class I18nTranslation {

    /** 服务侧生成的翻译 Snowflake 主键。 */
    private Long translationId;

    /** 所属 key，取值为 t_i18n_key.key_id。 */
    private Long keyId;

    /** 翻译语言，取值为 t_i18n_locale.locale_code，不为 en。 */
    private String localeCode;

    /** 翻译文案，ICU MessageFormat；参数名集合与英文基准一致，保存与导入时校验。 */
    private String text;

    /** 保存该翻译时 key 的英文基准修订号；小于 t_i18n_key.source_revision 表示基准已变更。 */
    private Integer basedOnSourceRevision;

    /** 翻译乐观并发版本，从 1 开始，每次修改加一；批量保存按它逐条 CAS。 */
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
