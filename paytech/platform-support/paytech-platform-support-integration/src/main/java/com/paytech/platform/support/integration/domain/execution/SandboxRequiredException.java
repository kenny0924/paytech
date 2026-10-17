package com.paytech.platform.support.integration.domain.execution;

/** 验证运行用到的出站地址未标记沙箱；在任何外部调用之前抛出，消息只含地址代码与接入方编码。 */
public class SandboxRequiredException extends RuntimeException {

    public SandboxRequiredException(String message) {
        super(message);
    }
}
