package com.paytech.risk.control.domain.limittier;

import java.util.List;

/**
 * 档位列表的一页
 *
 * @param items 本页条目
 * @param total 同一条件下的总数
 */
public record ClientTierPage(List<ClientTierView> items, long total) { }
