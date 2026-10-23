package com.paytech.platform.support.localization.domain;

import com.paytech.platform.support.localization.core.enums.KeyStatus;

import lombok.Getter;
import lombok.Setter;

/** 一个命名空间某状态的 key 数；命名空间列表统计用。 */
@Getter
@Setter
public class NamespaceKeyCount {

    /** 命名空间 ID。 */
    private Long namespaceId;

    /** key 状态。 */
    private KeyStatus status;

    /** key 数。 */
    private Long keyCount;
}
