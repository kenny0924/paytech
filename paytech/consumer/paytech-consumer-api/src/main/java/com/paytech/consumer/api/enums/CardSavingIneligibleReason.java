package com.paytech.consumer.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 不可存卡的原因；资格判断失败关闭，任何一项成立都不可保存卡、不可使用已存卡。 */
public enum CardSavingIneligibleReason implements CodeEnum {
    /** Customer 不存在。 */
    CUSTOMER_NOT_FOUND,

    /** Customer 已删除。 */
    CUSTOMER_INACTIVE,

    /** Customer 不属于请求的 Client。 */
    CLIENT_MISMATCH,

    /** Client 未开通存卡。 */
    SETTING_DISABLED,

    /** Client 存卡开关暂时无法读取（client-master 不可用或响应无效）。 */
    SETTING_UNAVAILABLE;

    @Override
    public String getCode() {
        return name();
    }
}
