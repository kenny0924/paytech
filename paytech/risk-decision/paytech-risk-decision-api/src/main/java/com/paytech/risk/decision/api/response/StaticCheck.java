package com.paytech.risk.api.decision.response;

/** 一项静态校验结果。 */
public class StaticCheck {

    /** 检查编码。 */
    private String checkCode;

    /** PASSED 或 FAILED。 */
    private String checkStatus;

    /** 失败代码；通过为空。 */
    private String errorCode;

    /** 失败摘要，不含输入值；通过为空。 */
    private String errorSummary;

    /** 读取检查编码。 */
    public String getCheckCode() { return checkCode; }

    /** 设置检查编码。 */
    public void setCheckCode(String checkCode) { this.checkCode = checkCode; }

    /** 读取检查结论。 */
    public String getCheckStatus() { return checkStatus; }

    /** 设置检查结论。 */
    public void setCheckStatus(String checkStatus) { this.checkStatus = checkStatus; }

    /** 读取失败代码。 */
    public String getErrorCode() { return errorCode; }

    /** 设置失败代码。 */
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    /** 读取失败摘要。 */
    public String getErrorSummary() { return errorSummary; }

    /** 设置失败摘要。 */
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
}
