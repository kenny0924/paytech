package com.paytech.compliance.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** O14 运营程序目录。 */
@Getter
@Setter
public class ClientReviewProgramListResponseBody {

    /** 程序版本，按辖区（全局程序在前）、程序编码、版本号倒序；最多 200 个。 */
    private List<ClientReviewProgramResponseBody> items;
}
