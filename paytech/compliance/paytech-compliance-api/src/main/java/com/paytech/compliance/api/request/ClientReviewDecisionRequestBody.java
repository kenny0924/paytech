package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** O08/O09 最终决定；批准或拒绝由操作路径确定，不由请求体决定。 */
public class ClientReviewDecisionRequestBody {

    /** 决定原因代码。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String reasonCode;

    /** 内部审核理由，1—2000 字符；不会展示给客户。 */
    @NotBlank
    @Size(max = 2000)
    private String reason;

    /** 另行确认的客户可见说明，可空，最多 1000 字符。 */
    @Size(max = 1000)
    private String publicNote;

    /** 读取决定原因代码。 */
    public String getReasonCode() {
        return reasonCode;
    }

    /** 设置决定原因代码。 */
    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    /** 读取内部审核理由，1—2000 字符；不会展示给客户。 */
    public String getReason() {
        return reason;
    }

    /** 设置内部审核理由，1—2000 字符；不会展示给客户。 */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /** 读取另行确认的客户可见说明，可空，最多 1000 字符。 */
    public String getPublicNote() {
        return publicNote;
    }

    /** 设置另行确认的客户可见说明，可空，最多 1000 字符。 */
    public void setPublicNote(String publicNote) {
        this.publicNote = publicNote;
    }
}
