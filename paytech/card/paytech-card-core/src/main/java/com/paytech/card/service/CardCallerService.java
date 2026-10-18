package com.paytech.card.service;

/**
 * Card 入口的调用方授权（D26，design/05 §4）：每个入口只接受登记的调用方服务名
 * <li>调用方由入口按内部 Token（已验证时）或 X-Caller-Service（mode=OFF / DUAL 未带 Token）解析后传入。</li>
 * <li>不在允许集合内一律视为资源不存在（404），不返回任何卡数据，也不暴露资源是否存在。</li>
 */
public interface CardCallerService {
    /**
     * 要求调用方属于允许集合
     *
     * @param caller 已解析的调用方服务名，可为空
     * @param allowed 本入口允许的调用方
     */
    void require(String caller, String... allowed);
}
