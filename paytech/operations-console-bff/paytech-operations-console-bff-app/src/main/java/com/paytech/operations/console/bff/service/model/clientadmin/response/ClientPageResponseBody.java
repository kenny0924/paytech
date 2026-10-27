package com.paytech.operations.console.bff.service.model.clientadmin.response;

import java.util.List;

import com.paytech.compliance.api.response.QualificationResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminClientDetailResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Client 详情页：client-master 主档为主体，compliance 资格单独标记可用性
 * <li>qualificationsStatus 取 ReadAvailability：AVAILABLE 或 UNAVAILABLE；不可用时列表为空，页面不得推断为“未登记”。</li>
 */
@Getter
@AllArgsConstructor
public class ClientPageResponseBody {

    /** client-master Client 详情：摘要、当前主档值（证件只有掩码）、关系人、关系与申请历史 */
    private final AdminClientDetailResponseBody client;

    /** Client（CLIENT + clientId）资格的读取结果 */
    private final String qualificationsStatus;

    /** Client 的全部角色资格 */
    private final List<QualificationResponseBody> qualifications;

    /** 含法定名称与关系人姓名，不进入日志。 */
    @Override
    public String toString() {
        return "ClientPageResponseBody[REDACTED]";
    }
}
