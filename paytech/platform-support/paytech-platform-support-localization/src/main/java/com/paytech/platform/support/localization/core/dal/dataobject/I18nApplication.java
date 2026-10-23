package com.paytech.platform.support.localization.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.localization.core.enums.ApplicationStatus;

import lombok.Getter;
import lombok.Setter;

/** t_i18n_application 映射对象；只在 localization 模块内使用。 */
@Getter
@Setter
public class I18nApplication {

    /** 服务侧生成的应用 Snowflake 主键。 */
    private Long applicationId;

    /** 全局唯一的应用编码，如 client-console；小写字母开头的小写字母、数字与连字符，创建后不可修改。 */
    private String applicationCode;

    /** 在 Operations 页面显示的应用名称。 */
    private String applicationName;

    /** 应用默认语言，取值为 ENABLED 的 t_i18n_locale.locale_code；取代需求中的渠道默认语言。 */
    private String defaultLocale;

    /** 必须覆盖的语言代码 JSON 数组，元素为 ENABLED 的 t_i18n_locale.locale_code、不重复、最多 20 个；发布时这些语言的覆盖率须达到阈值，可为空数组。 */
    private String requiredLocalesJson;

    /** 必须覆盖语言的翻译覆盖率阈值百分比，取值 0 至 100；translated × 100 ≥ 阈值 × total 才能发布。 */
    private Integer coverageThresholdPercent;

    /** 当前发布，取值为状态 READY 的 t_i18n_release.release_id；NULL 表示还没有上线的发布，前端只用构建内置文案。 */
    private Long currentReleaseId;

    /** key 集合修订号，从 0 开始，每次确认 key 导入加一；导入预览摘要依据它判断预览是否过期。 */
    private Integer keySetRevision;

    /** 文案修订号，从 0 开始，每次导入 key、保存或导入翻译加一；发布登记时核对生成语言包期间它没有变化。 */
    private Integer contentRevision;

    /** 应用状态；DISABLED 时不提供 manifest。允许值：ENABLED、DISABLED。 */
    private ApplicationStatus status;

    /** 应用设置的乐观并发版本，从 1 开始，修改名称、默认语言、必须覆盖语言或阈值时加一；修订号与当前发布变化不改变它。 */
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
