package com.paytech.payout.api.beneficiary.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/**
 * 对一个收款账户的状态命令请求：设默认（P4）、关闭（P5）、停用（P6）、恢复（P7）
 * <li>ownerType / ownerId：Client BFF 必须传当前工作区 Owner，与账户不符时 404；运营可省略。</li>
 * <li>reasonCode：关闭取 BeneficiaryCloseReason、停用取 BeneficiarySuspendReason，必填；设默认与恢复不传。</li>
 * <li>expectedVersion 与当前账户版本不同时 409 CONCURRENT_REQUEST。</li>
 */
@Getter
@Setter
public class BeneficiaryAccountCommandRequestBody {
    /**
     * Owner 类型
     * <li>可空；给出时与 ownerId 一起校验归属。</li>
     */
    @Pattern(regexp = "CLIENT|LOCAL_PARTNER", message = "只能为 CLIENT 或 LOCAL_PARTNER")
    private String ownerType;

    /**
     * Owner 标识
     * <li>可空；给出时必须同时给出 ownerType。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须为规范正整数字符串")
    private String ownerId;

    /**
     * 原因代码
     * <li>关闭、停用必填，取对应受控目录；设默认、恢复为空。</li>
     */
    @Pattern(regexp = "[A-Z][A-Z_]{0,63}", message = "必须是大写原因代码")
    private String reasonCode;

    /**
     * 期望的账户版本
     * <li>取自最近一次读取的 version。</li>
     */
    @NotBlank
    @Pattern(regexp = "0|[1-9][0-9]{0,18}", message = "必须为非负整数字符串")
    private String expectedVersion;

    /**
     * 操作人类型
     * <li>CLIENT_MEMBER 或 OPERATIONS；停用与恢复只允许 OPERATIONS。</li>
     */
    @NotBlank
    @Pattern(regexp = "CLIENT_MEMBER|OPERATIONS", message = "只能为 CLIENT_MEMBER 或 OPERATIONS")
    private String actorType;

    /** 操作人 Platform 账号 ID */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须为规范正整数字符串")
    private String actorId;
}
