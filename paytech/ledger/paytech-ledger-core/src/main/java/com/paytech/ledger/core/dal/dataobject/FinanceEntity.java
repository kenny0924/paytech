package com.paytech.ledger.core.dal.dataobject;

import com.paytech.ledger.core.enums.FinanceEntityStatus;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_finance_entity：保存 Finance 受控人工维护的内部财务主体，并以唯一法律 Party 稳定标识映射 CCT 中的平台签约法律主体；不复制法律资料。 */
@Getter
@Setter
public class FinanceEntity {
    /**
     * 服务侧生成的内部财务主体 Snowflake 主键
     */
    private Long id;

    /**
     * Finance 内部人工分配且永久唯一的财务主体代码
     * <li>取值来源：Finance Entity Code；非固定枚举。</li>
     */
    private String entityCode;

    /**
     * CCT Party 的稳定业务ID；由 Partner Agreement 唯一 PLATFORM_SIGNATORY 提供，不建立跨库外键
     */
    private Long legalPartyId;

    /**
     * 供 Finance 内部操作和审计识别该财务主体的显示名称，不替代 CCT 法律资料
     */
    private String displayName;

    /**
     * 控制该财务主体能否用于创建新账簿和新交易；关闭后不得恢复激活
     * <li>允许值：ACTIVE、CLOSED。</li>
     */
    private FinanceEntityStatus status;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
