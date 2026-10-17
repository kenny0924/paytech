package com.paytech.foundation.internalauth.contract;

/**
 * Platform 签发内部调用 Token 的请求（{@code POST /platform/v1/internal-tokens}）；调用方身份由 Platform 侧认证确定。
 *
 * @author Claude
 */
public class InternalTokenIssueRequest {

    /**
     * 目标服务名。
     * <li>服务发现名，即 Token {@code aud}。</li>
     */
    private String targetService;

    /**
     * Purpose Code。
     * <li>大写 Code，默认业务调用使用 {@code GENERAL}。</li>
     */
    private String purpose;

    /**
     * 创建空请求，供 JSON 绑定。
     */
    public InternalTokenIssueRequest() {
    }

    /**
     * 创建请求。
     *
     * @param targetService 目标服务名
     * @param purpose Purpose Code
     */
    public InternalTokenIssueRequest(String targetService, String purpose) {
        this.targetService = targetService;
        this.purpose = purpose;
    }

    public String getTargetService() {
        return targetService;
    }

    public void setTargetService(String targetService) {
        this.targetService = targetService;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }
}
