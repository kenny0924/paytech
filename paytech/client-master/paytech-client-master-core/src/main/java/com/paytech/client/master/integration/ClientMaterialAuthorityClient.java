package com.paytech.client.master.integration;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
import com.paytech.client.contract.api.onboarding.ClientDocumentModels.DocumentReferenceRequestBody;
import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.domain.onboarding.ClientMaterialTarget;
import com.paytech.client.master.domain.onboarding.ProgramScope;
import com.paytech.client.master.domain.onboarding.requirement.MaterialRequirement;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;
import com.paytech.platform.support.api.enums.*;
/** Owner HTTP 证据校验：compliance 程序材料槽与 Platform 文件；不缓存材料许可，不跨库读取，也不进入 CCT 事务。 */
public final class ClientMaterialAuthorityClient {
    private final ComplianceProgramClient programs;
    private final InternalFileApi platform;
    /** 只依赖 compliance 程序端口与 Platform 文件接口。 */
    public ClientMaterialAuthorityClient(ComplianceProgramClient programs,InternalFileApi platform) { this.programs=programs;this.platform=platform; }
    /**
     * 先按申请的程序解析材料槽，再校验原文件当前可用性与上传人；客户端 Hash 从不参与
     *
     * @param accountId 当前账号
     * @param target 材料目标（主主体或关联人员）
     * @param scope 申请的程序解析范围
     * @param request 材料引用
     * @return Platform 权威内容摘要
     */
    public String verify(long accountId,ClientMaterialTarget target,ProgramScope scope,DocumentReferenceRequestBody request) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("材料 Owner 调用不能位于数据库事务内");
        FileAddress address=ClientDraftCommand.fileAddress(request.fileAddress());
        boolean matched=programs.materials(scope).stream().anyMatch(item -> applies(item,target)
            && request.requirementCode().equals(item.requirementCode())
            && (item.documentTypes().isEmpty() || item.documentTypes().contains(request.documentType())));
        if (!matched) throw new FailException(MATERIAL_REQUIREMENT_INVALID);
        return fileHash(accountId,address);
    }
    /** 主主体只用 PRIMARY 材料槽，关联人员用其他材料槽（ANY_RELATED 或按关系角色）。 */
    private static boolean applies(MaterialRequirement item,ClientMaterialTarget target) {
        return target.primary()==MaterialRequirementRoles.PRIMARY.equals(item.subjectRole());
    }
    /**
     * 经 Platform 内部文件接口核验文件当前可用、由当前账号上传，并返回 Platform 权威内容摘要。
     *
     * @param accountId 当前账号
     * @param address 已校验格式的 Platform 文件地址
     * @return 文件 SHA-256
     *         <li>Platform FILE_NOT_FOUND 与上传人不是当前账号都为 DOCUMENT_NOT_FOUND，不区分他人文件与不存在；扫描未通过或对象失效为 MATERIAL_FILE_UNAVAILABLE。</li>
     *         <li>Platform 不可用、其他 4xx/5xx 或响应不完整为 ONBOARDING_DEPENDENCY_UNAVAILABLE。</li>
     */
    public String fileHash(long accountId,FileAddress address) {
        String fileId=Long.toString(address.fileId());
        StoredFileResponseBody file;
        try { file=platform.getFile(fileId); }
        catch (FailException rejected) {
            if (FileErrorCode.FILE_NOT_FOUND.getCode().equals(rejected.getCode())) throw new FailException(DOCUMENT_NOT_FOUND);
            throw unavailable();
        } catch (RuntimeException unavailable) { throw unavailable(); }
        if (file==null || !fileId.equals(file.getFileId()) || !address.value().equals(file.getFileAddress()) || file.getContentHash()==null || !file.getContentHash().matches("[0-9a-f]{64}")
            || file.getScanStatus()==null || file.getObjectStatus()==null) throw unavailable();
        FileScanStatus scan;FileObjectStatus object;
        try { scan=FileScanStatus.fromCode(file.getScanStatus());object=FileObjectStatus.fromCode(file.getObjectStatus()); }
        catch (IllegalArgumentException unknown) { throw unavailable(); }
        if (!Long.toString(accountId).equals(file.getUploaderAccountId())) throw new FailException(DOCUMENT_NOT_FOUND);
        if (scan!=FileScanStatus.AVAILABLE || object!=FileObjectStatus.ACTIVE) throw new FailException(MATERIAL_FILE_UNAVAILABLE);
        return file.getContentHash();
    }
    /** 不透传下游原始错误和敏感元数据。 */
    private ErrorException unavailable() { return new ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE); }
}
