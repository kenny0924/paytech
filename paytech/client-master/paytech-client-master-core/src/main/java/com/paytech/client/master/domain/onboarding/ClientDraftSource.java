package com.paytech.client.master.domain.onboarding;

import com.paytech.client.master.core.dal.dataobject.ClientDraftFields;
import com.paytech.client.master.core.dal.dataobject.ClientDraftIdentity;

/**
 * 写入前只读准备得到的申请与被修改主体的当前资料
 *
 * @param identity 已核对归属与可编辑的申请
 * @param fields 被修改主体的当前资料
 */
public record ClientDraftSource(ClientDraftIdentity identity, ClientDraftFields fields) {
    /** 含资料字段，不输出到日志。 */
    @Override
    public String toString() {
        return "ClientDraftSource[redacted]";
    }
}
