package com.paytech.client.master.integration;
import java.time.Clock;
import java.util.HashSet;
import com.paytech.client.contract.api.onboarding.ClientDocumentModels.DocumentReferenceRequestBody;
import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.domain.onboarding.ClientMaterialTarget;
import com.paytech.compliance.api.MaterialRequirementsModels.RequirementBody;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileVerificationApi;
import com.paytech.platform.support.api.enums.*;
import com.paytech.compliance.api.*;
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
        boolean matched=requirements(target.qualificationType().getCode()).stream()
            .anyMatch(item -> request.requirementCode().equals(item.requirementCode()) && request.documentType().equals(item.documentType()));
        if (!matched) throw new FailException(MATERIAL_REQUIREMENT_INVALID);
        return fileHash(accountId,target,ClientDraftCommand.id(request.fileId()));
    }
    /**
     * 读取资格类型的通用材料要求；Risk 不可用或目录损坏时依赖不可用，不视为无需材料。
     *
     * @param qualificationType 资格类型代码
     * @return 已校验的材料要求，至少一项
     */
    public java.util.List<RequirementBody> requirements(String qualificationType) {
        com.paytech.compliance.api.MaterialRequirementsModels.RequirementsResponseBody requirements;
        try { requirements=risk.read("client-master-service",qualificationType); }
        catch (RuntimeException unavailable) { throw unavailable(); }
        if (requirements==null || !id(requirements.programId()) || !id(requirements.programVersionId())
            || requirements.items()==null || requirements.items().isEmpty() || requirements.items().size()>1024) throw unavailable();
        var codes=new HashSet<String>();
        for (var item:requirements.items()) {
            if (item==null || !code(item.requirementCode()) || !code(item.documentType()) || !codes.add(item.requirementCode())) throw unavailable();
        }
        return requirements.items();
    }
    /**
     * 核验原文件当前可用并返回 Platform 权威内容摘要。
     *
     * @param accountId 当前账号
     * @param target 原申请与主体
     * @param fileId Platform 文件
     * @return 文件 SHA-256
     *         <li>文件不存在 DOCUMENT_NOT_FOUND；扫描未通过或对象失效 MATERIAL_FILE_UNAVAILABLE；其他为依赖不可用。</li>
     */
    public String fileHash(long accountId,ClientMaterialTarget target,long fileId) {
        com.paytech.platform.support.api.onboardingfiles.response.FileVerificationResponseBody proof;
        try { proof=platform.verify("client-master-service",Long.toString(fileId),Long.toString(target.applicationId()),Long.toString(target.subjectPartyId()),Long.toString(target.clientId()),Long.toString(accountId),OnboardingFilePurpose.ASSOCIATE.getCode()); }
        catch (FailException rejected) {
            if ("FILE_NOT_FOUND".equals(rejected.getCode())) throw new FailException(DOCUMENT_NOT_FOUND);
            throw unavailable();
        } catch (RuntimeException unavailable) { throw unavailable(); }
        if (proof==null || !Long.toString(fileId).equals(proof.getFileId()) || !id(proof.getUploadId()) || !id(proof.getSizeBytes())
            || proof.getContentHash()==null || !proof.getContentHash().matches("[0-9a-f]{64}")
            || proof.getMimeType()==null || proof.getMimeType().isBlank() || proof.getMimeType().length()>128
            || proof.getScanStatus()==null || proof.getObjectStatus()==null || proof.getCheckedAt()==null
            || proof.getCheckedAt().isBefore(clock.instant().minusSeconds(30)) || proof.getCheckedAt().isAfter(clock.instant().plusSeconds(5))) throw unavailable();
        FileScanStatus scan;FileObjectStatus object;
        try { scan=FileScanStatus.fromCode(proof.getScanStatus());object=FileObjectStatus.fromCode(proof.getObjectStatus()); }
        catch (IllegalArgumentException unknown) { throw unavailable(); }
        if (scan!=FileScanStatus.AVAILABLE || object!=FileObjectStatus.ACTIVE) throw new FailException(MATERIAL_FILE_UNAVAILABLE);
        return proof.getContentHash();
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
