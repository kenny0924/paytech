package com.paytech.operations.console.bff.integration;

import java.util.Collection;
import java.util.List;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.CountryListResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.CountrySubdivisionListResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.IndustryListResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.MccResponseBody;

/**
 * Platform 参考数据（国家、行政区、行业、MCC）的本地端口；数据与状态归 Platform，查询不携带操作人
 * <li>每个方法只调用一次 Platform（按编码批量查询除外），不重试；返回值已核对编码与状态。</li>
 *
 * @author Claude
 */
public interface PlatformReferenceDataClient {

    /**
     * 国家与地区
     *
     * @param status 可选状态；省略时返回全部
     * @return 按国家代码升序的国家
     */
    CountryListResponseBody countries(String status);

    /**
     * 一级行政区
     *
     * @param countryCode 可选国家代码；省略时返回全部国家的行政区
     * @param status 可选状态；省略时返回全部
     * @return 按行政区代码升序的行政区
     */
    CountrySubdivisionListResponseBody subdivisions(String countryCode, String status);

    /**
     * 两级行业
     *
     * @param status 可选状态；省略时返回全部
     * @return 每个大类之后紧跟其细类
     */
    IndustryListResponseBody industries(String status);

    /**
     * 分页搜索 MCC
     *
     * @param keyword 可选关键字：编码前缀或英文名
     * @param status 可选状态
     * @param page 已校验的页码
     * @param size 已校验的每页条数
     * @return 按编码升序的分页结果
     */
    PagingResponseBody<MccResponseBody> searchMccs(String keyword, String status, int page, int size);

    /**
     * 按编码读取 MCC（任意状态）；超过单次上限时分批查询
     *
     * @param codes 四位数字编码，可为空集合
     * @return 存在的 MCC，按编码升序；不存在的编码不出现
     */
    List<MccResponseBody> mccsByCodes(Collection<String> codes);
}
