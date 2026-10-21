import { useMemo, useRef, useState } from 'react';
import type { FormInstance } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import type { PageDecoder } from '../api/paging';
import { changePaging } from '../api/paging';
import { usePaging } from '../hooks/usePaging';
import { readListQuery, writeListQuery } from './reconRoutes';
import type { ListQuery } from './reconRoutes';

/**
 * 列表分页 + 筛选 / 页码同步到 URL 查询串（AC-01）
 * <li>挂载时从 URL 读初值；之后的查询、翻页以 replace 写回 URL，从详情页返回或浏览器后退即恢复。</li>
 * <li>fixed 为页面固定附加的筛选（如银行账户），不写 URL；defaults 为缺省筛选（URL 中键不存在时使用）。</li>
 */
export function useReconListQuery<T>(path: string, decode: PageDecoder<T>, keys: readonly string[],
  options: { defaults?: Readonly<Record<string, string>>; fixed?: Readonly<Record<string, string>>; sizes?: readonly number[] } = {}) {
  const location = useLocation();
  const navigate = useNavigate();
  const defaults = options.defaults ?? {}; const fixed = options.fixed ?? {};
  const initial = useMemo(() => readListQuery(location.search, keys, defaults, options.sizes), []);
  const current = useRef<ListQuery>(initial);
  const [activeFilters, setActiveFilters] = useState<Record<string, string>>(initial.filters);
  const page = usePaging<T>(path, decode, { ...initial.filters, ...fixed }, { initialPaging: initial.paging });
  const sync = (next: ListQuery) => {
    current.current = next;
    setActiveFilters(next.filters);
    const search = writeListQuery(window.location.search, keys, next, defaults);
    void navigate({ pathname: location.pathname, search }, { replace: true, state: location.state });
  };
  return {
    ...page,
    /** 挂载时 URL 中的筛选，供表单 initialValues。 */
    filters: initial.filters,
    /** 当前生效的筛选（每次查询后更新），用于空状态文案等。 */
    activeFilters,
    applyFilters: (filters: Record<string, string>) => {
      page.applyFilters({ ...filters, ...fixed });
      sync({ filters, paging: { ...current.current.paging, page: 1 } });
    },
    pagination: { ...page.pagination, ...(options.sizes ? { pageSizeOptions: [...options.sizes] } : {}), onChange: (pageNo: number, size: number) => {
      page.pagination.onChange(pageNo, size);
      sync({ filters: current.current.filters, paging: changePaging(current.current.paging, pageNo, size) });
    } },
  };
}

/** 「重置」：表单回到给定值（不是 URL 初值），其余字段清空。 */
export function resetForm(form: FormInstance, values: Readonly<Record<string, string>> = {}) {
  form.setFieldsValue(Object.fromEntries(Object.keys(form.getFieldsValue(true) as object).map(key => [key, values[key]])));
}
