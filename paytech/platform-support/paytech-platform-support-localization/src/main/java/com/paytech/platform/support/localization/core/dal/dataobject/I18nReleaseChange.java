package com.paytech.platform.support.localization.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.localization.core.enums.ReleaseChangeType;

import lombok.Getter;
import lombok.Setter;

/** t_i18n_release_change 映射对象；只在 localization 模块内使用，创建后不修改。 */
@Getter
@Setter
public class I18nReleaseChange {

    /** 服务侧生成的文案变化 Snowflake 主键。 */
    private Long changeId;

    /** 所属应用，取值为 t_i18n_application.application_id。 */
    private Long applicationId;

    /** 发生变化的发布号；变化相对同一应用发布号减一的发布，发布号 1 相对空内容。 */
    private Integer releaseNo;

    /** 文案语言，取值为 t_i18n_locale.locale_code；en 为英文基准。 */
    private String localeCode;

    /** 完整 key，发布时的 t_i18n_key.key_code。 */
    private String keyCode;

    /** 变化类型。允许值：ADDED、CHANGED、REMOVED。 */
    private ReleaseChangeType changeType;

    /** 本发布中的文案；REMOVED 时为 NULL。 */
    private String text;

    /** 该记录在 Platform Support 数据库中的 UTC 微秒创建时间；创建后不可变，没有更新时间。 */
    private Instant createTime;
}
