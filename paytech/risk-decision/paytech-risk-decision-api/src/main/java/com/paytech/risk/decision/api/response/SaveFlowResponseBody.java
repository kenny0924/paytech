package com.paytech.risk.api.decision.response;

/** 本次保存成功的最小结果，不提供服务本地请求回放。 */
public class SaveFlowResponseBody {

    /**
     * 流程管理 ID。
     * <li>正 BIGINT 十进制字符串。</li>
     */
    private String flowId;

    /**
     * 本次保存的准确版本 ID。
     * <li>正 BIGINT 十进制字符串。</li>
     */
    private String flowVersionId;

    /**
     * 本次流程业务版本号。
     * <li>正整数，不是接口版本。</li>
     */
    private int versionNo;

    /**
     * 本次提交后的根修订号。
     * <li>正 BIGINT 十进制字符串。</li>
     */
    private String version;

    /**
     * 本次可信保存人的 Account ID。
     * <li>正 BIGINT 十进制字符串。</li>
     */
    private String savedBy;

    /**
     * 本次保存时间。
     * <li>UTC RFC 3339，固定六位小数。</li>
     */
    private String savedTime;

    /** 返回流程管理 ID。 */
    public String getFlowId() { return flowId; }

    /** 设置流程管理 ID。 */
    public void setFlowId(String flowId) { this.flowId = flowId; }

    /** 返回本次保存的准确版本 ID。 */
    public String getFlowVersionId() { return flowVersionId; }

    /** 设置本次保存的准确版本 ID。 */
    public void setFlowVersionId(String flowVersionId) { this.flowVersionId = flowVersionId; }

    /** 返回本次流程业务版本号。 */
    public int getVersionNo() { return versionNo; }

    /** 设置本次流程业务版本号。 */
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }

    /** 返回本次提交后的根修订号。 */
    public String getVersion() { return version; }

    /** 设置本次提交后的根修订号。 */
    public void setVersion(String version) { this.version = version; }

    /** 返回本次可信保存人的 Account ID。 */
    public String getSavedBy() { return savedBy; }

    /** 设置本次可信保存人的 Account ID。 */
    public void setSavedBy(String savedBy) { this.savedBy = savedBy; }

    /** 返回本次保存时间。 */
    public String getSavedTime() { return savedTime; }

    /** 设置本次保存时间。 */
    public void setSavedTime(String savedTime) { this.savedTime = savedTime; }
}
