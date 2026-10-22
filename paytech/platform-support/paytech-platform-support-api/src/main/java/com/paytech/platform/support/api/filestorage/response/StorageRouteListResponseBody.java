package com.paytech.platform.support.api.filestorage.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 全部写入路由，按来源系统排序；数量有限，不分页。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StorageRouteListResponseBody {

    /** 写入路由。 */
    private List<StorageRouteResponseBody> items;
}
