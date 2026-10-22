package com.paytech.platform.support.api.filestorage.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 全部存储实例，按实例代码排序；数量有限，不分页。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StorageInstanceListResponseBody {

    /** 存储实例。 */
    private List<StorageInstanceResponseBody> items;
}
