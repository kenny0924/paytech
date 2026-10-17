package com.paytech.platform.support.filedocument.domain.enums;
import com.paytech.foundation.common.enums.CodeEnum;
/** 文件生命周期受控状态。 */
public enum FileScanType implements CodeEnum {
    ANTI_MALWARE,

    DLP,

    FORMAT;
    /** 持久化稳定代码。 */
    @Override public String getCode() { return name(); }
}
