package com.paytech.ledger.core.dal.dataobject;

import com.paytech.ledger.core.enums.AdjustmentStatus;
import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_manual_adjustment：维护人工会计调整申请头、平衡金额、受控文件证据和提交审批状态。 */
@Getter
@Setter
public class ManualAdjustment {
    /**
     * 人工会计调整申请的服务侧雪花主键
     */
    private Long id;

    /**
     * 人工会计调整申请的稳定业务单号
     */
    private String adjustmentNo;

    /**
     * 人工调整唯一适用的账簿主键
     */
    private Long ledgerBookId;

    /**
     * 人工调整唯一适用且提交时必须开放的会计期间主键
     */
    private Long accountingPeriodId;

    /**
     * 人工调整全部明细共同使用的 ISO 4217 三字母币种
     * <li>取值来源：ISO 4217；非固定枚举。</li>
     */
    private String currency;

    /**
     * 人工会计调整使用的受控业务原因代码
     * <li>取值来源：Finance 人工会计调整原因目录；非固定枚举。</li>
     */
    private String reasonCode;

    /**
     * Platform 文件服务保存的受控调整证据文件主键
     * <li>可为空。</li>
     */
    private Long fileId;

    /**
     * Platform 文件服务返回且与证据文件主键成组保存的 SHA-256 小写十六进制摘要
     * <li>可为空。</li>
     */
    private String fileContentHash;

    /**
     * 提交时必须大于零并等于贷方合计的借方总金额
     */
    private BigDecimal totalDebit;

    /**
     * 提交时必须大于零并等于借方合计的贷方总金额
     */
    private BigDecimal totalCredit;

    /**
     * 覆盖申请头和有序明细的规范化 SHA-256 小写十六进制摘要
     */
    private String contentHash;

    /**
     * 人工调整进入不可修改提交态的 UTC 微秒时间
     * <li>可为空。</li>
     */
    private Instant submittedTime;

    /**
     * 人工会计调整申请的当前生命周期状态
     * <li>允许值：DRAFT、SUBMITTED、APPROVED、REJECTED、POSTED、CANCELLED。</li>
     */
    private AdjustmentStatus status;

    /**
     * 创建并提交人工调整的平台用户主键
     */
    private Long makerId;

    /**
     * 人工调整条件更新使用的乐观锁版本
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
