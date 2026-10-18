package com.paytech.client.master.ext.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.contract.api.onboarding.ClientFileTargetApi;
import com.paytech.client.contract.api.onboarding.ClientFileTargetModels.FileTargetResponseBody;
import com.paytech.client.master.domainservice.service.ClientFileTargetService;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.APPLICATION_NOT_FOUND;

/** Platform 专用私网查询；Header 只防止误调用，部署时仍需私网监听和网络隔离。 */
@RestController
@ConditionalOnProperty(prefix = "onboarding", name = "draft-enabled", havingValue = "true")
public class ClientFileTargetController implements ClientFileTargetApi {
    private final ClientFileTargetService service;

    /** 只注入 CCT 资源授权能力。 */
    public ClientFileTargetController(ClientFileTargetService service) {
        this.service = service;
    }

    /** 校验内部调用者和协议参数，返回授权投影，不返回数据库对象。 */
    @Override
    public FileTargetResponseBody read(String caller, String applicationId, String subjectPartyId, String accountId) {
        if (!"platform-support-service".equals(caller)) {
            throw new FailException(APPLICATION_NOT_FOUND);
        }
        var target = service.read(id(accountId), id(applicationId), id(subjectPartyId));
        return new FileTargetResponseBody(Long.toString(target.applicationId()), Long.toString(target.clientId()),
            Long.toString(target.subjectPartyId()), target.clientType().getCode(), target.canUpload(), target.canRead());
    }

    /** 拒绝前导零、符号、空白和超过有符号 BIGINT 的标识，不在错误中回显输入。 */
    private static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new InvalidParameterException("文件目标标识必须为规范正整数");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            throw new InvalidParameterException("文件目标标识超出允许范围", invalid);
        }
    }
}
