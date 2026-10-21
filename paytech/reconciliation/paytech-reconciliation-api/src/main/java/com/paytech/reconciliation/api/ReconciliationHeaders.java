package com.paytech.reconciliation.api;

/**
 * reconciliation 内部 HTTP 合同共用的请求头名称
 * <li>与 ledger 人工调整（ManualAdjustmentApi）同一机制：Gateway 按会话写入，Operations BFF 透传；运营写命令两个头都必填，查询与服务间内部
 * 接口不带。</li>
 */
public final class ReconciliationHeaders {
    /**
     * 操作人 Operations Account ID 请求头
     * <li>规范正十进制字符串；作为上传人、提交人（Maker）或复核人（Checker）落库，复核人必须不同于提交人。</li>
     */
    public static final String ACTOR_HEADER = "X-Gateway-Actor-Subject";

    /**
     * 操作人会话 ID 请求头
     * <li>只用于审计关联，不参与业务判断。</li>
     */
    public static final String SESSION_HEADER = "X-Gateway-Session-Id";

    /** 常量类不实例化。 */
    private ReconciliationHeaders() {
    }
}
