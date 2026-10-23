import { lazy, Suspense } from 'react';
import { Button, Form, Input, Select, Table, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { Loading, QueryFailure } from '../tenantAccess/TenantAccessView';
import {
  activeStatusLabels, activeStatuses, dataQueryButtons, dataQueryPaths, dataQueryRoutes, hasButton, isId, listFilters, localTime,
} from '../dataQuery/dataQueryCommon';
import { decodeTemplatePage, pagingModeLabels, parameterSummaryTags } from '../dataQuery/templateModel';
import type { TemplateSummary } from '../dataQuery/templateModel';
import { StatusTag, useDataSourceOptions } from '../dataQuery/DataQueryParts';
import '../dataQuery/dataQuery.css';

const TemplateEditor = lazy(() => import('../dataQuery/TemplateEditor').then(m => ({ default: m.TemplateEditor })));

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/** 查询模板：/data-query/templates 列表，/data-query/templates/new 新建，/data-query/templates/:templateId 编辑器（懒加载）。 */
export function QueryTemplates(props: Props) {
  const location = useLocation();
  const parts = location.pathname.replace(/\/$/, '').split('/').slice(3);
  if (parts.length > 1 || (parts.length === 1 && parts[0] !== 'new' && !isId(parts[0]))) return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  if (parts.length === 1) {
    return <div className="dq-page dq-editor-page"><Suspense fallback={<Loading />}>
      <TemplateEditor key={parts[0]} templateId={parts[0] === 'new' ? null : parts[0]} {...props} />
    </Suspense></div>;
  }
  return <TemplateList {...props} />;
}

interface FilterValues { dataSourceId?: string; status?: string; keyword?: string }

/** 模板列表（原型 03）：按数据源、状态、关键字筛选；参数摘要 * 必填、[] 集合、⟨片段⟩。 */
function TemplateList({ resources }: Props) {
  const navigate = useNavigate(); const location = useLocation();
  const initial = listFilters({ dataSourceId: new URLSearchParams(location.search).get('dataSourceId') ?? undefined });
  const [form] = Form.useForm<FilterValues>();
  const page = usePaging<TemplateSummary>(dataQueryPaths.templates, decodeTemplatePage, initial, { notifyOnError: false });
  const sources = useDataSourceOptions();
  const canManage = hasButton(resources, dataQueryButtons.templateManage);
  return <div className="configuration-page dq-page">
    <Form form={form} layout="vertical" name="template-filter" initialValues={initial}
      onFinish={(values: FilterValues) => page.applyFilters(listFilters({ ...values }))}>
      <div className="dq-filters">
        <Form.Item name="dataSourceId" label="数据源"><Select allowClear showSearch optionFilterProp="label" placeholder="全部" loading={sources.loading}
          options={sources.items.map(item => ({ value: item.dataSourceId, label: `${item.dataSourceCode} · ${item.dataSourceName}` }))} /></Form.Item>
        <Form.Item name="status" label="状态"><Select allowClear placeholder="全部" options={activeStatuses.map(value => ({ value, label: activeStatusLabels[value] }))} /></Form.Item>
        <Form.Item name="keyword" label="编码 / 名称"><Input allowClear maxLength={64} placeholder="关键字" /></Form.Item>
        <div className="dq-filter-actions"><Button type="primary" htmlType="submit">查询</Button>
          <Button onClick={() => { form.setFieldsValue({ dataSourceId: undefined, status: undefined, keyword: undefined }); page.applyFilters({}); }}>重置</Button></div>
      </div>
    </Form>
    <section className="dq-panel">
      <div className="dq-list-toolbar">
        <div><strong>共 {page.data.total} 个模板</strong>
          <span className="muted">用统一语法书写只读 SQL，保存即生成新版本并立即生效；调用方按模板编码执行。</span></div>
        {canManage && <Button type="primary" onClick={() => void navigate(dataQueryRoutes.newTemplate)}>＋ 新建模板</Button>}
      </div>
      {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
        : <Table<TemplateSummary> rowKey="templateId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
          tableLayout="fixed" scroll={{ x: 1080 }} locale={{ emptyText: '没有符合条件的模板' }} rowClassName={() => 'dq-clickable'}
          onRow={row => ({ onClick: () => void navigate(dataQueryRoutes.template(row.templateId)) })} columns={[
            { title: '编码 / 名称', width: 230, render: (_, row) => <div className="dq-cell"><span className="mono dq-link">{row.templateCode}</span><span className="muted">{row.templateName}</span></div> },
            { title: '数据源', width: 170, render: (_, row) => <span className="mono">{row.dataSourceCode}</span> },
            { title: '参数', width: 260, render: (_, row) => <div className="dq-param-tags">{parameterSummaryTags(row.parameters).map(tag =>
              <Tag key={tag.text} className={`dq-param-tag is-${tag.kind}${tag.required ? ' is-required' : ''}`}>{tag.text}</Tag>)}
              {row.parameters.length === 0 && <span className="muted">无参数</span>}</div> },
            { title: '分页', width: 80, render: (_, row) => pagingModeLabels[row.pagingMode] },
            { title: '当前版本', width: 90, render: (_, row) => `v${row.currentVersionNo}` },
            { title: '状态', width: 80, render: (_, row) => <StatusTag status={row.status} /> },
            { title: '最近修改', width: 170, render: (_, row) => `${row.updatedBy} · ${localTime(row.updateTime)}` },
          ]} />}
      <div className="dq-table-foot muted">参数带 * 为必填，[] 为集合，⟨片段⟩ 为片段参数</div>
    </section>
  </div>;
}
