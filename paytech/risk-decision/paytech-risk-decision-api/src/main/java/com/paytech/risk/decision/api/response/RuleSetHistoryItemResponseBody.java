package com.paytech.risk.api.decision.response;

/** 规则集历史分页中的精确版本摘要，名称与变更信息保留保存时的事实。 */
public class RuleSetHistoryItemResponseBody {

    /**
     * 整体规则根 ID。
     * <li>正 BIGINT 的十进制字符串。</li>
     */
    private String ruleSetId;

    /**
     * 精确规则版本 ID。
     * <li>正 BIGINT 的十进制字符串。</li>
     */
    private String ruleSetVersionId;

    /** 所属决策点编码。 */
    private String pointCode;

    /** 决策点内的规则集编码。 */
    private String ruleSetCode;

    /** 该版本保存时的规则集名称。 */
    private String ruleSetName;

    /** 规则集内递增的业务版本号。 */
    private int versionNo;

    /**
     * 规则版本的生命周期状态。
     * <li>取值为 DRAFT、FROZEN 或 RETIRED。</li>
     */
    private String status;

    /**
     * 是否等于规则根的当前配置指针。
     * <li>不表示正在使用，也不以本次浏览上界判断。</li>
     */
    private boolean isCurrent;

    /**
     * 创建该版本的 Account ID。
     * <li>正 BIGINT 的十进制字符串。</li>
     */
    private String createdBy;

    /** 保存该版本时填写的变更原因。 */
    private String changeReason;

    /**
     * 保存该版本的时间。
     * <li>UTC RFC 3339 字符串，固定六位小数。</li>
     */
    private String createTime;

    /** 读取整体规则根 ID。 */
    public String getRuleSetId() {
        return ruleSetId;
    }

    /** 设置整体规则根 ID。 */
    public void setRuleSetId(String ruleSetId) {
        this.ruleSetId = ruleSetId;
    }

    /** 读取精确规则版本 ID。 */
    public String getRuleSetVersionId() {
        return ruleSetVersionId;
    }

    /** 设置精确规则版本 ID。 */
    public void setRuleSetVersionId(String ruleSetVersionId) {
        this.ruleSetVersionId = ruleSetVersionId;
    }

    /** 读取所属决策点编码。 */
    public String getPointCode() {
        return pointCode;
    }

    /** 设置所属决策点编码。 */
    public void setPointCode(String pointCode) {
        this.pointCode = pointCode;
    }

    /** 读取决策点内的规则集编码。 */
    public String getRuleSetCode() {
        return ruleSetCode;
    }

    /** 设置决策点内的规则集编码。 */
    public void setRuleSetCode(String ruleSetCode) {
        this.ruleSetCode = ruleSetCode;
    }

    /** 读取该版本保存时的规则集名称。 */
    public String getRuleSetName() {
        return ruleSetName;
    }

    /** 设置该版本保存时的规则集名称。 */
    public void setRuleSetName(String ruleSetName) {
        this.ruleSetName = ruleSetName;
    }

    /** 读取规则集内递增的业务版本号。 */
    public int getVersionNo() {
        return versionNo;
    }

    /** 设置规则集内递增的业务版本号。 */
    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    /** 读取规则版本的生命周期状态。 */
    public String getStatus() {
        return status;
    }

    /** 设置规则版本的生命周期状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 读取是否等于规则根的当前配置指针。 */
    public boolean getIsCurrent() {
        return isCurrent;
    }

    /** 设置是否等于规则根的当前配置指针。 */
    public void setIsCurrent(boolean isCurrent) {
        this.isCurrent = isCurrent;
    }

    /** 读取创建该版本的 Account ID。 */
    public String getCreatedBy() {
        return createdBy;
    }

    /** 设置创建该版本的 Account ID。 */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /** 读取保存该版本时填写的变更原因。 */
    public String getChangeReason() {
        return changeReason;
    }

    /** 设置保存该版本时填写的变更原因。 */
    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    /** 读取保存该版本的时间。 */
    public String getCreateTime() {
        return createTime;
    }

    /** 设置保存该版本的时间。 */
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }
}
