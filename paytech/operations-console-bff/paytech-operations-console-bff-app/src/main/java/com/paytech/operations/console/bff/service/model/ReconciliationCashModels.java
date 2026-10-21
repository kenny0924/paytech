package com.paytech.operations.console.bff.service.model;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 对账银行账户页面模型
 * <li>字段与 reconciliation-api 合同一一对应，由 Client 适配器严格转换；Owner 新增字段即视为合同漂移。</li>
 * <li>操作人不在请求体中，由 BFF 从会话填入 X-Gateway-Actor-Subject / X-Gateway-Session-Id。</li>
 *
 * @author Claude
 */
public final class ReconciliationCashModels {

    /** 只承载类型，禁止创建实例。 */
    private ReconciliationCashModels() {
    }

    /**
     * 提交新建银行账户请求
     * <li>含银行账号明文：不生成 toString，不得进入普通日志。</li>
     */
    @Getter
    @Setter
    public static class CreateCashAccountRequestBody {
        /**
         * 主体类型
         * <li>OPERATING_ENTITY 或 FINANCE_ENTITY。</li>
         */
        @NotBlank
        @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY", message = "只能为 OPERATING_ENTITY 或 FINANCE_ENTITY")
        private String entityType;

        /**
         * 主体标识
         * <li>经营主体为 entityNo（两位大写字母加三位数字），财务主体为 ledger 主键十进制字符串；本期不向 Owner 核对。</li>
         */
        @NotBlank
        @Pattern(regexp = "[A-Z]{2}[0-9]{3}|[1-9][0-9]{0,18}", message = "必须是经营主体编号或正十进制整数字符串")
        private String entityId;

        /**
         * 资金用途
         * <li>CHANNEL_SETTLEMENT、PAYOUT 或 TAX；创建后不可改。</li>
         */
        @NotBlank
        @Pattern(regexp = "CHANNEL_SETTLEMENT|PAYOUT|TAX", message = "只能为 CHANNEL_SETTLEMENT、PAYOUT 或 TAX")
        private String accountPurpose;

        /**
         * 开户银行代码
         * <li>运营录入的银行机构代码，非固定枚举。</li>
         */
        @NotBlank
        @Size(max = 64)
        private String bankCode;

        /**
         * 账户显示名称
         */
        @NotBlank
        @Size(max = 128)
        private String accountName;

        /**
         * 账户币种
         * <li>大写 ISO 4217。</li>
         */
        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
        private String currency;

        /**
         * 银行账号明文
         * <li>4–34 位字母或数字（含 IBAN）；事务外经 Vault Protect 后只存令牌与末四位，不落库、不回显。</li>
         */
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9]{4,34}", message = "必须是 4–34 位字母或数字")
        private String accountNumber;
    }

    /** 复核银行账户新建或关闭申请请求；复核人取自 X-Gateway-Actor-Subject。 */
    @Getter
    @Setter
    public static class ReviewCashAccountRequestBody {
        /**
         * 复核决定
         * <li>APPROVED 或 REJECTED。拒绝新建账户时账户 CLOSED；拒绝关闭申请时清空申请。</li>
         */
        @NotBlank
        @Pattern(regexp = "APPROVED|REJECTED", message = "只能为 APPROVED 或 REJECTED")
        private String decision;

        /**
         * 复核人确认的内容摘要
         * <li>必须等于最近一次提交返回的 contentHash。</li>
         */
        @NotBlank
        @Pattern(regexp = "[0-9a-f]{64}", message = "必须是 64 位小写十六进制")
        private String approvedContentHash;
    }

    /** 平台银行账户；只含账号末四位。 */
    @Getter
    @Setter
    public static class CashAccountResponseBody {
        /** 银行账户主键 */
        private String cashAccountId;

        /** 主体类型：OPERATING_ENTITY、FINANCE_ENTITY */
        private String entityType;

        /** 主体标识 */
        private String entityId;

        /** 资金用途：CHANNEL_SETTLEMENT、PAYOUT、TAX */
        private String accountPurpose;

        /** 开户银行代码 */
        private String bankCode;

        /** 账户显示名称 */
        private String accountName;

        /** 账户币种 */
        private String currency;

        /** 银行账号末四位 */
        private String accountLast4;

        /** 状态：PENDING_VERIFICATION、ACTIVE、SUSPENDED、CLOSED */
        private String status;

        /**
         * 待复核的变更
         * <li>CLOSE 表示有待复核的关闭申请；为空表示没有（新建待复核由 status 表示）。</li>
         */
        private String pendingAction;

        /**
         * 最近一次提交复核内容的摘要
         * <li>复核时须原样确认。</li>
         */
        private String contentHash;

        /** 最近一次提交人 Account ID */
        private String makerId;

        /**
         * 最近一次复核人 Account ID
         * <li>未复核为空。</li>
         */
        private String checkerId;

        /** 乐观锁版本 */
        private String version;

        /** 创建的 UTC 时间 */
        private Instant createTime;

        /** 最后更新的 UTC 时间 */
        private Instant updateTime;
    }
}
