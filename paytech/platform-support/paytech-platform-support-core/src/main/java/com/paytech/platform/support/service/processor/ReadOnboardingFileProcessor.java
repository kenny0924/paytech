package com.paytech.platform.support.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.accesscontrol.domainservice.service.ClientOnboardingAccessService;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.api.enums.OnboardingFileAction;
import com.paytech.platform.support.core.integration.files.CctFileTargetClient;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domain.file.FileModels.Owned;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.service.processor.request.OnboardingFileAccessRequest;
import com.paytech.platform.support.service.processor.response.ReadOnboardingFileResponse;

/**
 * 客户读取本人入驻文件的当前元数据：两次只读快照之间向 CCT 确认原申请主体。
 * <li>纯查询，不写业务数据、不加锁；CCT 调用位于两次只读事务之间。</li>
 * <li>结果不包含摘要、存储坐标、临时 URL 或其他账号的存在性信息；允许的动作只是提示，执行时重新授权。</li>
 */
@Component
@ConditionalOnProperty(name = "files.onboarding.enabled", havingValue = "true")
public class ReadOnboardingFileProcessor extends AbstractOnboardingFileAccessProcessor<ReadOnboardingFileResponse> {

    /** 注入公共授权步骤的依赖。 */
    public ReadOnboardingFileProcessor(CctFileTargetClient targets, FileLifecycleService files,
        ClientOnboardingAccessService access, CustomerAuthenticationService authentication,
        OperationsTransactionExecutor transactions) {
        super(targets, files, access, authentication, transactions);
    }

    @Override
    protected ReadOnboardingFileResponse processRequest(OnboardingFileAccessRequest request) {
        // 授权读取
        Owned owned = authorized(request);

        // 响应处理
        return response(owned);
    }

    /** 从当前文件事实构造元数据；扫描通过且对象有效时提示可下载。 */
    ReadOnboardingFileResponse response(Owned owned) {
        File file = owned.file();
        List<OnboardingFileAction> actions = downloadable(owned) ? List.of(OnboardingFileAction.DOWNLOAD) : List.of();
        return new ReadOnboardingFileResponse(file.fileId(), owned.upload().displayName(), file.verified().sizeBytes(),
            file.verified().mimeType(), file.scanStatus(), file.objectStatus(), actions);
    }
}
