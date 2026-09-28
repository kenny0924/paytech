package com.paytech.vault.client;

/** 内部非卡敏感值客户端；调用者负责业务授权，不执行透明重试。 */
public interface VaultClient {
    /**
     * 保护调用方已完成业务校验和规范化的精确原值。 同一逻辑命令重放必须复用原幂等键；STABLE 跨调用者复用由数据类型和原值决定， 与各调用者的幂等键无关。提交结果未知时，本客户端不会自动重试。
     *
     * @param command
     *            不可变保护命令；调用者身份由客户端配置提供
     * @return 已成功提交的 Token，不得进入普通日志、事件或外部接口
     * @throws com.paytech.foundation.common.error.FailException
     *             输入无效或服务端明确拒绝
     * @throws com.paytech.foundation.common.error.ErrorException
     *             依赖不可用、结果未知或响应不符合契约
     */
    ProtectResult protect(ProtectCommand command);

    /**
     * 按明确用途读取原值；调用方必须先完成自身的业务授权。 服务端完成用途检查、完整性校验和成功审计提交后才返回原值，客户端不透明重试。
     *
     * @param query
     *            包含 Token 和本次访问用途的不可变查询
     * @return 仅供当前授权调用使用的原值，不得缓存、持久化或写入普通日志
     * @throws com.paytech.foundation.common.error.FailException
     *             输入无效、Token 不存在或服务端明确拒绝
     * @throws com.paytech.foundation.common.error.ErrorException
     *             依赖或审计不可用、完整性失败或响应不符合契约
     */
    RevealResult reveal(RevealQuery query);
}
