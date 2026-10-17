package com.paytech.risk.decision.api.response;

/** 主体标签当前事实，不声明适用主体限制，不改写历史配置。 */
public class SubjectTagResponseBody {

    /**
     * 永久标签编码。
     * <li>停用后不可复用。</li>
     */
    private String tagCode;

    /**
     * 当前名称。
     * <li>不代表历史名称。</li>
     */
    private String name;

    /**
     * 标签含义说明。
     * <li>响应键始终存在，允许 null。</li>
     */
    private String description;

    /**
     * 当前目录资格。
     * <li>ACTIVE 或 DISABLED。</li>
     */
    private String status;

    /**
     * 当前修订号。
     * <li>正 BIGINT 十进制字符串。</li>
     */
    private String version;

    /**
     * 创建人 Account ID。
     * <li>正 BIGINT 十进制字符串。</li>
     */
    private String createdBy;

    /**
     * 最后更新人 Account ID。
     * <li>正 BIGINT 十进制字符串。</li>
     */
    private String updatedBy;

    /**
     * 创建时间。
     * <li>UTC RFC 3339 字符串，固定六位小数。</li>
     */
    private String createTime;

    /**
     * 最后更新时间。
     * <li>UTC RFC 3339 字符串，固定六位小数。</li>
     */
    private String updateTime;

    /** 返回永久标签编码。 */
    public String getTagCode() {
        return tagCode;
    }

    /** 设置永久标签编码。 */
    public void setTagCode(String tagCode) {
        this.tagCode = tagCode;
    }

    /** 返回当前名称。 */
    public String getName() {
        return name;
    }

    /** 设置当前名称。 */
    public void setName(String name) {
        this.name = name;
    }

    /** 返回标签含义说明。 */
    public String getDescription() {
        return description;
    }

    /** 设置标签含义说明。 */
    public void setDescription(String description) {
        this.description = description;
    }

    /** 返回当前目录资格。 */
    public String getStatus() {
        return status;
    }

    /** 设置当前目录资格。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 返回当前修订号。 */
    public String getVersion() {
        return version;
    }

    /** 设置当前修订号。 */
    public void setVersion(String version) {
        this.version = version;
    }

    /** 返回创建人 Account ID。 */
    public String getCreatedBy() {
        return createdBy;
    }

    /** 设置创建人 Account ID。 */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /** 返回最后更新人 Account ID。 */
    public String getUpdatedBy() {
        return updatedBy;
    }

    /** 设置最后更新人 Account ID。 */
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    /** 返回创建时间。 */
    public String getCreateTime() {
        return createTime;
    }

    /** 设置创建时间。 */
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    /** 返回最后更新时间。 */
    public String getUpdateTime() {
        return updateTime;
    }

    /** 设置最后更新时间。 */
    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }
}

