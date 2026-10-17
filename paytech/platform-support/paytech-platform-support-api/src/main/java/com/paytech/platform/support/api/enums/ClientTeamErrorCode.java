package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** Client 团队与邀请错误；提示不含邮箱原值、工作区名称或链接 Secret。 */
public enum ClientTeamErrorCode implements CodeMessageEnum {
    /** 链接不存在、Secret 不正确或尝试次数用尽，不区分原因。 */
    CLIENT_INVITATION_INVALID("CLIENT_INVITATION_INVALID", "邀请链接无效"),

    /** 邀请已过期、已撤销或已被其他账号接受。 */
    CLIENT_INVITATION_CLOSED("CLIENT_INVITATION_CLOSED", "邀请已失效，请联系工作区管理员重新邀请"),

    CLIENT_INVITATION_EMAIL_MISMATCH("CLIENT_INVITATION_EMAIL_MISMATCH", "当前登录账号不是受邀邮箱，请用受邀邮箱登录"),

    CLIENT_INVITATION_DUPLICATE("CLIENT_INVITATION_DUPLICATE", "该邮箱已有待接受的邀请，可以重发"),

    CLIENT_INVITATION_LIMIT_REACHED("CLIENT_INVITATION_LIMIT_REACHED", "待接受的邀请已达上限"),

    CLIENT_MEMBER_EXISTS("CLIENT_MEMBER_EXISTS", "该邮箱已是工作区成员"),

    /** 修改或移除所有者，或修改、移除自己。 */
    CLIENT_MEMBER_PROTECTED("CLIENT_MEMBER_PROTECTED", "不能修改或移除该成员");

    private final String code;
    private final String message;

    /** 绑定稳定错误码和安全提示。 */
    ClientTeamErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 返回稳定错误码。 */
    @Override public String getCode() { return code; }

    /** 返回不包含敏感信息的提示。 */
    @Override public String getMessage() { return message; }
}
