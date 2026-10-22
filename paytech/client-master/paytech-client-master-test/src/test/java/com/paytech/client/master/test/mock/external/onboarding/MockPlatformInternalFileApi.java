package com.paytech.client.master.test.mock.external.onboarding;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.client.master.test.mocker.onboarding.OnboardingOwnerMocker;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.files.response.StoredFileResponseBody;

/** Platform 内部文件接口的测试 HTTP 实现；CCT 只应查询文件，上传与下载视为意外调用。路径前缀由 MockPlatformAuthenticationApi 统一配置。 */
@RestController
@TestComponent
public class MockPlatformInternalFileApi implements InternalFileApi {
    private final OnboardingOwnerMocker mocker;

    /** 与用例共享配置与记录。 */
    public MockPlatformInternalFileApi(OnboardingOwnerMocker mocker) {
        this.mocker = mocker;
    }

    /** CCT 不上传文件。 */
    @Override
    public FileUploadResponseBody startUpload(StartFileUploadRequestBody requestBody) {
        throw MockPlatformAuthenticationApi.unexpected();
    }

    /** CCT 不完成上传。 */
    @Override
    public FileUploadCompletionResponseBody completeUpload(String uploadId) {
        throw MockPlatformAuthenticationApi.unexpected();
    }

    /** 返回本例配置的文件元数据或故障，并记录调用身份头。 */
    @Override
    public StoredFileResponseBody getFile(String fileId) {
        MockPlatformAuthenticationApi.requestId();
        var request = ((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest();
        Object reply = mocker.file(fileId, request.getHeader("X-Caller-Service"));
        if (reply instanceof OnboardingOwnerMocker.Fault fault) {
            throw fault.disconnect() ? MockHttpFault.disconnect() : MockHttpFault.json(fault.status(), fault.problem());
        }
        return (StoredFileResponseBody)MockPlatformAuthenticationApi.required(reply);
    }

    /** CCT 不签发下载。 */
    @Override
    public FileDownloadResponseBody authorizeDownload(String fileId, String operatorAccountId, String disposition) {
        throw MockPlatformAuthenticationApi.unexpected();
    }
}
