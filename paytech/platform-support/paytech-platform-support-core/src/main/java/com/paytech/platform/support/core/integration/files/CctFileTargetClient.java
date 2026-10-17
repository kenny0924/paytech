package com.paytech.platform.support.core.integration.files;

import java.util.Set;
import com.paytech.client.contract.api.onboarding.ClientFileTargetApi;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;

/** 私网 CCT 授权适配；不缓存许可或把不可用当作通过。 */
@lombok.AllArgsConstructor
public final class CctFileTargetClient {
    private final ClientFileTargetApi api;
    /** 返回 CCT 确认的 Client，供本地 IAM 再次核对。 */
    public long authorize(long accountId, long applicationId, long subjectPartyId) {
        var target=read(accountId,applicationId,subjectPartyId);
        if (!target.canUpload()) throw new FailException(OnboardingFileErrorCode.FILE_STATE_CONFLICT);
        return target.clientId();
    }
    /** 读取原资源权限；已成功命令重放不重新触发写入。 */
    public Target read(long accountId,long applicationId,long subjectPartyId) {
        com.paytech.client.contract.api.onboarding.ClientFileTargetModels.FileTargetResponseBody value;
        try { value=api.read("platform-support-service",Long.toString(applicationId),Long.toString(subjectPartyId),Long.toString(accountId)); }
        catch (FailException rejected) {
            if (Set.of("APPLICATION_NOT_FOUND","PERSON_NOT_FOUND").contains(rejected.getCode()))
                throw new FailException(OnboardingFileErrorCode.FILE_NOT_FOUND);
            throw new ServiceUnavailableException();
        } catch (RuntimeException unavailable) { throw new ServiceUnavailableException(); }
        if (value==null || !Long.toString(applicationId).equals(value.applicationId())
            || !Long.toString(subjectPartyId).equals(value.subjectPartyId()) || !value.canRead()
            || value.clientType()==null || !Set.of("INDIVIDUAL","ORGANIZATION").contains(value.clientType()))
            throw new ServiceUnavailableException();
        long client;
        try {
            if (value.clientId()==null || !value.clientId().matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException();
            client=Long.parseLong(value.clientId());
        } catch (RuntimeException invalid) { throw new ServiceUnavailableException(); }
        return new Target(client,value.canUpload());
    }
    /**
     * @param clientId CCT 当前资源所属 Client
     * @param canUpload 当前资源是否可写
     */
    public record Target(long clientId,boolean canUpload) {}
}
