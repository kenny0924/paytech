package com.paytech.platform.support.api.clientinvitation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 邀请链接证明：邀请邮件链接 fragment 中的 challengeNo 与 secret。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientInvitationProofRequestBody {

    /** 挑战编号（UUID）。 */
    @NotBlank
    @Size(max = 64)
    private String challengeNo;

    /** 链接 Secret（Base64URL）；只校验，不保存、不记录。 */
    @NotBlank
    @Size(max = 128)
    private String secret;

    /** 屏蔽 Secret。 */
    @Override
    public String toString() {
        return "ClientInvitationProofRequestBody[challengeNo=" + challengeNo + "]";
    }
}
