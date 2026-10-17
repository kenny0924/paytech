package com.paytech.risk.api.decision.response;

/** 发布到决策点版本、再到规则集版本的真实引用。 */
public class RuleSetReleaseReferenceResponseBody {

    /**
     * 发布记录 ID。
     * <li>正 BIGINT 的十进制字符串。</li>
     */
    private String releaseId;

    /**
     * 发布业务编号。
     */
    private String releaseNo;

    /**
     * 发布所在环境。
     * <li>仅 TEST 或 PRODUCTION。</li>
     */
    private String environmentCode;

    /**
     * 发布生命周期状态。
     * <li>DRAFT、VALIDATING、SHADOWING、REVIEW_PENDING、APPROVED、ACTIVE、SUPERSEDED 或 REJECTED。</li>
     */
    private String status;

    /**
     * 规则所属决策点编码。
     */
    private String pointCode;

    /**
     * 发布引用的决策点版本 ID。
     * <li>正 BIGINT 的十进制字符串。</li>
     */
    private String pointVersionId;

    /**
     * 决策点业务版本号。
     */
    private int pointVersionNo;

    /**
     * 绑定的精确规则集版本 ID。
     * <li>正 BIGINT 的十进制字符串。</li>
     */
    private String ruleSetVersionId;

    /**
     * 规则集业务版本号。
     */
    private int versionNo;

    /**
     * 激活时间。
     * <li>UTC RFC 3339 字符串，固定六位小数；尚未激活时为空。</li>
     */
    private String activatedTime;

    /**
     * 被替代时间。
     * <li>UTC RFC 3339 字符串，固定六位小数；尚未被替代时为空。</li>
     */
    private String supersededTime;

    /** 返回发布记录 ID。 */
    public String getReleaseId() {
        return releaseId;
    }

    /** 设置发布记录 ID。 */
    public void setReleaseId(String releaseId) {
        this.releaseId = releaseId;
    }

    /** 返回发布业务编号。 */
    public String getReleaseNo() {
        return releaseNo;
    }

    /** 设置发布业务编号。 */
    public void setReleaseNo(String releaseNo) {
        this.releaseNo = releaseNo;
    }

    /** 返回发布所在环境。 */
    public String getEnvironmentCode() {
        return environmentCode;
    }

    /** 设置发布所在环境。 */
    public void setEnvironmentCode(String environmentCode) {
        this.environmentCode = environmentCode;
    }

    /** 返回发布生命周期状态。 */
    public String getStatus() {
        return status;
    }

    /** 设置发布生命周期状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 返回规则所属决策点编码。 */
    public String getPointCode() {
        return pointCode;
    }

    /** 设置规则所属决策点编码。 */
    public void setPointCode(String pointCode) {
        this.pointCode = pointCode;
    }

    /** 返回发布引用的决策点版本 ID。 */
    public String getPointVersionId() {
        return pointVersionId;
    }

    /** 设置发布引用的决策点版本 ID。 */
    public void setPointVersionId(String pointVersionId) {
        this.pointVersionId = pointVersionId;
    }

    /** 返回决策点业务版本号。 */
    public int getPointVersionNo() {
        return pointVersionNo;
    }

    /** 设置决策点业务版本号。 */
    public void setPointVersionNo(int pointVersionNo) {
        this.pointVersionNo = pointVersionNo;
    }

    /** 返回绑定的精确规则集版本 ID。 */
    public String getRuleSetVersionId() {
        return ruleSetVersionId;
    }

    /** 设置绑定的精确规则集版本 ID。 */
    public void setRuleSetVersionId(String ruleSetVersionId) {
        this.ruleSetVersionId = ruleSetVersionId;
    }

    /** 返回规则集业务版本号。 */
    public int getVersionNo() {
        return versionNo;
    }

    /** 设置规则集业务版本号。 */
    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    /** 返回激活时间。 */
    public String getActivatedTime() {
        return activatedTime;
    }

    /** 设置激活时间。 */
    public void setActivatedTime(String activatedTime) {
        this.activatedTime = activatedTime;
    }

    /** 返回被替代时间。 */
    public String getSupersededTime() {
        return supersededTime;
    }

    /** 设置被替代时间。 */
    public void setSupersededTime(String supersededTime) {
        this.supersededTime = supersededTime;
    }
}
