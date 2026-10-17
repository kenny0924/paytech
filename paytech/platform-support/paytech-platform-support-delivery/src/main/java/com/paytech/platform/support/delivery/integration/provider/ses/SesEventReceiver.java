package com.paytech.platform.support.delivery.integration.provider.ses;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

/** 一个账号一轮拉取使用的接收器：持有该轮从 Vault 读取的凭据，轮次结束即丢弃。 */
@RequiredArgsConstructor
public class SesEventReceiver {

    private final SesEventQueue queue;

    private final String queueUrl;

    private final AwsCredentialsProvider credential;

    /** 接收一批消息；失败时返回错误码。 */
    public SesEventBatch receive() {
        return queue.receive(queueUrl, credential);
    }

    /** 删除已处理的消息；成功返回空，失败返回错误码。 */
    public String delete(SesEventMessage message) {
        return queue.delete(queueUrl, message.getReceiptHandle(), credential);
    }

    /** 不输出凭据与队列地址。 */
    @Override
    public String toString() {
        return "SesEventReceiver";
    }
}
