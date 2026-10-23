package com.paytech.platform.support.localization.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.localization.core.enums.ReleaseStatus;

import lombok.Getter;
import lombok.Setter;

/** t_i18n_release 映射对象；只在 localization 模块内使用。 */
@Getter
@Setter
public class I18nRelease {

    /** 服务侧生成的发布 Snowflake 主键。 */
    private Long releaseId;

    /** 所属应用，取值为 t_i18n_application.application_id。 */
    private Long applicationId;

    /** 应用内发布号，从 1 开始，按应用内最大值加一。 */
    private Integer releaseNo;

    /** 生成语言包时应用的文案修订号；登记时与应用当前修订号一致。 */
    private Integer basedOnContentRevision;

    /** 发布状态；PENDING_CHECK 等待语言包文件检查，READY 可作为当前发布，FAILED 有文件被拒绝。允许值：PENDING_CHECK、READY、FAILED。 */
    private ReleaseStatus status;

    /** 发布包含的 ACTIVE key 数。 */
    private Integer keyCount;

    /** 发布时各语言覆盖率 JSON 数组，每项含 locale、required、totalKeys、translatedKeys、sourceChangedKeys、coveragePercent、satisfied。 */
    private String coverageJson;

    /** 发布报告 JSON 对象，含 missing（未翻译而回退英文）与 sourceChanged（基准已变更仍沿用旧翻译），各为 {locale, keyCode} 数组。 */
    private String reportJson;

    /** 发布说明；NULL 表示未填写。 */
    private String releaseNote;

    /** 检查失败原因；非 FAILED 时为 NULL。 */
    private String failureReason;

    /** 检查通过变为 READY 的 UTC 微秒时间；未通过时为 NULL。 */
    private Instant readyTime;

    /** 发布人，取值为 t_user_account.account_id。 */
    private Long createdBy;

    /** 发布登记的 UTC 微秒时间。 */
    private Instant createTime;

    /** 该记录最后一次持久化更新（状态变化）的 UTC 微秒时间。 */
    private Instant updateTime;
}
