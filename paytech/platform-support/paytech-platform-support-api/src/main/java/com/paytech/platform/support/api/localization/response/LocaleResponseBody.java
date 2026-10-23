package com.paytech.platform.support.api.localization.response;

import lombok.Getter;
import lombok.Setter;

/** 一个系统支持的语言。 */
@Getter
@Setter
public class LocaleResponseBody {

    /** BCP 47 语言代码，如 en、zh-CN。 */
    private String localeCode;

    /** 本语言的自称。 */
    private String nativeName;

    /** 英文名称。 */
    private String englishName;

    /** 文字方向：LTR、RTL。 */
    private String textDirection;

    /** 状态：ENABLED、DISABLED。 */
    private String status;

    /** 排序值，升序。 */
    private int sortOrder;

    /** 乐观并发版本，修改时按此传 expectedVersion。 */
    private int version;

    /** 最后修改人账号 ID，十进制字符串。 */
    private String updatedBy;

    /** 最后修改时间，UTC ISO-8601 微秒精度。 */
    private String updateTime;
}
