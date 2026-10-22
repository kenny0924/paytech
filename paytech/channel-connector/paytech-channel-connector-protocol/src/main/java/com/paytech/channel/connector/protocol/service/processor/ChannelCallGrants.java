package com.paytech.channel.connector.protocol.service.processor;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelCallGrant;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.domain.ChannelCallGrantView;

/** 调用授权写入口共用的响应构造：从本次已加载的授权行与标准 API 组装，不为响应重查。 */
final class ChannelCallGrants {

    /** 工具类不实例化。 */
    private ChannelCallGrants() {
    }

    /**
     * 组装授权视图
     *
     * @param grant 授权行（写入成功后已同步）
     * @param standardApi 授权的标准 API
     * @return 授权视图
     */
    static ChannelCallGrantView view(ChannelCallGrant grant, StandardApi standardApi) {
        ChannelCallGrantView view = new ChannelCallGrantView();
        view.setCallGrantId(grant.getCallGrantId());
        view.setChannelId(grant.getChannelId());
        view.setCallerService(grant.getCallerService());
        view.setStandardApiCode(standardApi.getStandardApiCode());
        view.setStandardApiName(standardApi.getStandardApiName());
        view.setGrantStatus(grant.getGrantStatus());
        view.setVersion(grant.getVersion());
        view.setCreatedBy(grant.getCreatedBy());
        view.setUpdatedBy(grant.getUpdatedBy());
        view.setCreateTime(grant.getCreateTime());
        view.setUpdateTime(grant.getUpdateTime());
        return view;
    }
}
