package com.paytech.client.master.core.dal.dataobject;
/** 联系方式受控引用，仅在 Owner 内使用。 */
public record ClientDraftContact(
    /** EMAIL 或 PHONE 目录代码。 */ String contactType,
    /** Vault 稳定引用，不允许进入普通日志或 API。 */ String token,
    /** CCT 生成的展示掩码。 */ String maskedValue) {
    @Override public String toString() { return "ClientDraftContact[redacted]"; }
}
