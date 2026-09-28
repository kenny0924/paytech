package com.paytech.client.contract.integration;
import java.time.Clock;
import java.util.HashSet;
import com.paytech.client.contract.api.onboarding.ClientDocumentModels.DocumentReferenceRequestBody;
import com.paytech.client.contract.domain.onboarding.ClientMaterialTarget;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileVerificationApi;
import com.paytech.platform.support.api.enums.*;
import com.paytech.risk.api.compliance.*;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** Owner HTTP 证据校验，不缓存材料许可，不跨库读取，也不进入 CCT 事务。 */
public final class ClientMaterialAuthorityClient {
    private final MaterialRequirementsApi risk;
    private final OnboardingFileVerificationApi platform;
    private final Clock clock;
    /** 只依赖两份轻量 Owner API。 */
    public ClientMaterialAuthorityClient(MaterialRequirementsApi risk,OnboardingFileVerificationApi platform,Clock clock) { this.risk=risk;this.platform=platform;this.clock=clock; }
    /** 先读取真实材料要求，再校验原文件当前可用性；客户端 Hash 从不参与。 */
    public String verify(long accountId,ClientMaterialTarget target,DocumentReferenceRequestBody request) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("材料 Owner 调用不能位于数据库事务内");
        com.paytech.risk.api.compliance.MaterialRequirementsModels.RequirementsResponseBody requirements;
        try { requirements=risk.read("client-contract-service",target.qualificationType().getCode(),target.countryCode()); }
        catch (RuntimeException unavailable) { throw unavailable(); }
        if (requirements==null || !id(requirements.programId()) || !id(requirements.programVersionId())
            || requirements.items()==null || requirements.items().isEmpty() || requirements.items().size()>1024) throw unavailable();
        var codes=new HashSet<String>();boolean matched=false;
        for (var item:requirements.items()) {
            if (item==null || !code(item.requirementCode()) || !code(item.documentType()) || !codes.add(item.requirementCode())) throw unavailable();
            if (request.requirementCode().equals(item.requirementCode()) && request.documentType().equals(item.documentType())) matched=true;
        }
        if (!matched) throw new FailException(MATERIAL_REQUIREMENT_INVALID);
        com.paytech.platform.support.api.onboardingfiles.OnboardingFileModels.VerificationResponseBody proof;
        try { proof=platform.verify("client-contract-service",request.fileId(),Long.toString(target.applicationId()),Long.toString(target.subjectPartyId()),Long.toString(target.clientId()),Long.toString(accountId),OnboardingFilePurpose.ASSOCIATE.getCode()); }
        catch (FailException rejected) {
            if ("FILE_NOT_FOUND".equals(rejected.getCode())) throw new FailException(DOCUMENT_NOT_FOUND);
            throw unavailable();
        } catch (RuntimeException unavailable) { throw unavailable(); }
        if (proof==null || !request.fileId().equals(proof.fileId()) || !id(proof.uploadId()) || !id(proof.sizeBytes())
            || proof.contentHash()==null || !proof.contentHash().matches("[0-9a-f]{64}")
            || proof.mimeType()==null || proof.mimeType().isBlank() || proof.mimeType().length()>128
            || proof.scanStatus()==null || proof.objectStatus()==null || proof.checkedAt()==null
            || proof.checkedAt().isBefore(clock.instant().minusSeconds(30)) || proof.checkedAt().isAfter(clock.instant().plusSeconds(5))) throw unavailable();
        FileScanStatus scan;FileObjectStatus object;
        try { scan=FileScanStatus.fromCode(proof.scanStatus());object=FileObjectStatus.fromCode(proof.objectStatus()); }
        catch (IllegalArgumentException unknown) { throw unavailable(); }
        if (scan!=FileScanStatus.AVAILABLE || object!=FileObjectStatus.ACTIVE) throw new FailException(MATERIAL_FILE_UNAVAILABLE);
        return proof.contentHash();
    }
    /** 下游标识和字节数必须为规范正整数。 */
    private boolean id(String value) {
        try { return value!=null && value.matches("[1-9][0-9]{0,18}") && Long.parseLong(value)>0; }
        catch (NumberFormatException invalid) { return false; }
    }
    /** 稳定材料代码不能由浏览器自由扩展。 */
    private boolean code(String value) { return value!=null && value.matches("[A-Z][A-Z0-9_]{0,63}"); }
    /** 不透传下游原始错误和敏感元数据。 */
    private com.paytech.foundation.common.error.ErrorException unavailable() { return new com.paytech.foundation.common.error.ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE); }
}
