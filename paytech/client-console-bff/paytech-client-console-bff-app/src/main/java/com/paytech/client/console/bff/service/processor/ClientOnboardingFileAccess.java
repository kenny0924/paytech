package com.paytech.client.console.bff.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.CallerServiceCodes;
import com.paytech.client.console.bff.integration.CctOnboardingClient;
import com.paytech.client.console.bff.integration.PlatformAuthenticationClient;
import com.paytech.client.console.bff.integration.PlatformFileClient;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;

import lombok.AllArgsConstructor;

/**
 * 客户入驻文件的读取授权，查文件与下载共用
 * <li>Platform 内部文件接口不做文件级授权，也不核验客户会话；这里先实时回读 Platform 会话，再判断文件归属。</li>
 * <li>可读条件二选一：文件由当前账号经本 BFF 上传；或其地址出现在本人可读申请的材料中（向 CCT 查申请导航与草稿）。</li>
 * <li>其他情况一律按文件不存在拒绝，不区分文件不存在还是属于他人。</li>
 * <li>申请只取 CCT 导航返回的范围（最多 20 条），逐份读取草稿，命中即停。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientOnboardingFileAccess {
    private final PlatformFileClient files;
    private final CctOnboardingClient onboarding;
    private final PlatformAuthenticationClient authentication;

    /**
     * 读取当前账号可读的文件元数据。
     *
     * @param actor 当前可信身份
     * @param fileId 文件 ID
     * @return 已核对归属的 Platform 元数据
     */
    StoredFileResponseBody readableFile(ClientActor actor, String fileId) {
        authentication.context(actor);
        var file = files.file(fileId);
        if (uploadedBy(file, actor) || linkedToOwnApplication(actor, file.getFileAddress())) {
            return file;
        }
        throw new FailException(FileErrorCode.FILE_NOT_FOUND);
    }

    /** 文件由当前账号经本 BFF 上传；其他来源系统记录的上传人不代表客户账号。 */
    static boolean uploadedBy(StoredFileResponseBody file, ClientActor actor) {
        return CallerServiceCodes.CLIENT_CONSOLE_BFF.equals(file.getSourceSystem())
            && actor.getAccountId().equals(file.getUploaderAccountId());
    }

    /** 文件地址出现在本人任一可读申请的材料中。 */
    boolean linkedToOwnApplication(ClientActor actor, String fileAddress) {
        for (var application : onboarding.start(actor).applications()) {
            var draft = onboarding.draft(actor, application.applicationId());
            if (draft.documents().stream().anyMatch(document -> fileAddress.equals(document.fileAddress()))) {
                return true;
            }
        }
        return false;
    }
}
