package com.paytech.client.console.bff.integration;

import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.platform.support.api.onboardingfiles.response.FileResponseBody;

/** Platform 入驻文件元数据的出站端口。 */
public interface PlatformOnboardingFileClient {

    /**
     * 读取一个入驻文件的状态与允许动作。
     *
     * @param actor 当前可信身份
     * @param fileId 申请材料引用的文件 ID
     * @return 文件 ID 一致、状态码已识别且只允许下载动作的元数据；否则按依赖不可用抛出
     */
    FileResponseBody file(ClientActor actor, String fileId);
}
