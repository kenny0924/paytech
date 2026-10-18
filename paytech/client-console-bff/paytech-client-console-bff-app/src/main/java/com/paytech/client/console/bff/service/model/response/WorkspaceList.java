package com.paytech.client.console.bff.service.model.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 开始页“我的工作区”；成员与租户事实来自 Platform，名称来自 CCT。 */
@Getter
@AllArgsConstructor
public class WorkspaceList {

    /**
     * 区域状态：AVAILABLE 或 UNAVAILABLE
     * <li>Platform 或 CCT 不可用时为 UNAVAILABLE，且 items 为空。</li>
     */
    private final String status;

    /** 工作区，不含 PENDING 工作区 */
    private final List<WorkspaceItem> items;
}
