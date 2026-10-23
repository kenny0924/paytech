package com.paytech.platform.support.localization.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.localization.core.enums.LocaleStatus;
import com.paytech.platform.support.localization.core.enums.TextDirection;

import lombok.Getter;
import lombok.Setter;

/** t_i18n_locale 映射对象；只在 localization 模块内使用。 */
@Getter
@Setter
public class I18nLocale {

    /** 服务侧生成的语言 Snowflake 主键。 */
    private Long localeId;

    /** BCP 47 语言代码，如 en、zh-CN；全局唯一，创建后不可修改。取值来源：IETF BCP 47；非固定枚举。 */
    private String localeCode;

    /** 本语言的自称，如“简体中文”，用于语言切换显示。 */
    private String nativeName;

    /** 语言的英文名称，如 Simplified Chinese。 */
    private String englishName;

    /** 文字方向；本期只登记 LTR 语言，RTL 仅预留。允许值：LTR、RTL。 */
    private TextDirection textDirection;

    /** 语言状态；DISABLED 的语言不能被选为默认或必须覆盖语言，也不出现在 manifest 中，en 不能停用。允许值：ENABLED、DISABLED。 */
    private LocaleStatus status;

    /** 显示排序值，升序排列，取值 0 至 9999。 */
    private Integer sortOrder;

    /** 语言乐观并发版本，从 1 开始，每次修改加一。 */
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
