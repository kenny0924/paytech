package com.paytech.finance.accounting.core.dal.dataobject;

import com.paytech.finance.accounting.core.enums.InstructionOrigin;
import com.paytech.finance.accounting.core.enums.InstructionStatus;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_accounting_instruction：维护一个自动业务事实或已批准人工调整在单一账簿产生的平衡记账指令组及其验证、入账状态；同一服务费以 PARTNER 与 PLATFORM 两类指令分别绑定两本账。 */
@Getter
@Setter
public class AccountingInstruction {
    /**
     * 会计记账指令的服务侧雪花主键
     */
    private Long id;

    /**
     * 该指令唯一适用的本库 Ledger Book；全部指令行账户和最终 Journal Entry 必须逐值属于同一账簿
     */
    private Long ledgerBookId;

    /**
     * 该指令所表达的业务会计动作类型
     * <li>取值来源：Finance 会计指令类型目录；非固定枚举。</li>
     */
    private String instructionType;

    /**
     * 区分规则自动生成和人工调整生成的指令
     * <li>允许值：AUTO、MANUAL。</li>
     */
    private InstructionOrigin originType;

    /**
     * 自动指令所消费权威事件的来源系统，人工指令为空
     * <li>取值来源：Code-MoR 系统代码目录；非固定枚举。</li>
     * <li>可为空。</li>
     */
    private String sourceSystem;

    /**
     * 自动指令所消费权威事件的稳定事件键，人工指令为空
     * <li>可为空。</li>
     */
    private String sourceEventKey;

    /**
     * 自动指令所消费权威事件或事实的版本，人工指令为空
     * <li>可为空。</li>
     */
    private Long sourceVersion;

    /**
     * 自动指令来源规范载荷的 SHA-256 小写十六进制摘要，人工指令为空
     * <li>可为空。</li>
     */
    private String sourcePayloadHash;

    /**
     * 自动交易类指令对应的本库不可变交易经济结果主键
     * <li>可为空。</li>
     */
    private Long transactionEconomicsId;

    /**
     * 人工指令对应且已经固定双人审批的人工调整主键
     * <li>可为空。</li>
     */
    private Long manualAdjustmentId;

    /**
     * 自动指令冻结使用的自动记账规则版本主键
     * <li>可为空。</li>
     */
    private Long accountingRuleBundleVerId;

    /**
     * 自动指令确定性输入或人工调整冻结内容的 SHA-256 小写十六进制摘要
     * <li>可为空。</li>
     */
    private String inputHash;

    /**
     * 记账指令当前验证和入账状态
     * <li>允许值：RECEIVED、VALIDATING、VALIDATED、POSTED、REJECTED、CONFLICT。</li>
     */
    private InstructionStatus status;

    /**
     * 人工指令对应的调整编制人主键，自动指令为空
     * <li>可为空。</li>
     */
    private Long makerId;

    /**
     * 人工指令对应的调整复核人主键，自动指令为空且必须与编制人不同
     * <li>可为空。</li>
     */
    private Long checkerId;

    /**
     * 记账指令状态条件更新使用的乐观锁版本
     */
    private Long version;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
