import { useEffect, useLayoutEffect, useRef } from 'react';
import { Button, Dropdown, Form, Input, Select, Table, Tag, Tooltip } from 'antd';
import { usePaging } from '../hooks/usePaging';
import type { ConsoleResource } from '../api/types';
import type { RuleSetListItem } from './riskApiModels';
import type { RuleSetFilters } from './riskDecisionApi';
import type { RiskNavigation } from './riskNavigation';
import { ruleSetCodeError } from './riskNavigation';
import { hasRiskAction } from './riskPageModel';
import { decodeRuleSetPage } from './riskResponseValidation';
import { formatRiskTime, ruleSetStatusLabels } from './RuleSetDetails';

export function RuleSetTab({ revision, disabled, navigation, resources, canEdit, onNavigate, onSelect, onEdit, onDelete, onHistory, onReferences, onFreeze, onReadFailure }: {
  revision: number; disabled: boolean; navigation: RiskNavigation; resources: readonly ConsoleResource[]; canEdit: boolean;
  onNavigate: (next: RiskNavigation, replace?: boolean) => void; onSelect: (row: RuleSetListItem) => void;
  onHistory: (row: RuleSetListItem) => void; onReferences: (row: RuleSetListItem) => void; onFreeze: (row: RuleSetListItem) => void;
  onEdit: (row: RuleSetListItem) => void; onDelete: (row: RuleSetListItem) => void; onReadFailure: () => void;
}) {
  const [form] = Form.useForm();
  const invalidQuery = !!ruleSetCodeError(navigation.filters.ruleSetCode ?? '');
  const { data, loading, error, refresh, applyFilters, pagination } = usePaging<RuleSetListItem>(
    '/decision-rule-sets/v1/rule-sets', decodeRuleSetPage, navigation.filters, {
      initialPaging: navigation.paging, enabled: !invalidQuery,
      onQueryChange: next => {
        if (JSON.stringify(next) === JSON.stringify({ paging: navigation.paging, filters: navigation.filters })) refresh();
        else onNavigate({ ...navigation, ...next });
      },
    });
  const previousRevision = useRef(revision);
  const readFailure = useRef(onReadFailure);
  useLayoutEffect(() => { readFailure.current = onReadFailure; }, [onReadFailure]);
  useEffect(() => { if (error) readFailure.current(); }, [error]);
  useEffect(() => { if (invalidQuery) void form.validateFields(['ruleSetCode']).catch(() => undefined); }, [invalidQuery, form]);
  useEffect(() => {
    if (previousRevision.current !== revision) { previousRevision.current = revision; refresh(); }
  }, [revision, refresh]);
  useEffect(() => {
    if (!loading && !error && !invalidQuery && data.page === navigation.paging.page && data.size === navigation.paging.size
      && data.items.length === 0 && navigation.paging.page > 1) {
      onNavigate({ ...navigation, paging: { ...navigation.paging, page: Math.max(1, Math.ceil(data.total / navigation.paging.size)) } }, true);
    }
  }, [data, loading, error, invalidQuery, navigation, onNavigate]);
  return <section className="table-panel">
    <Form form={form} layout="inline" className="risk-filters" initialValues={navigation.filters}
      onFinish={(values: RuleSetFilters) => applyFilters({ pointCode: values.pointCode || 'CHECKOUT_PREPARE',
        ...(values.ruleSetCode?.trim() ? { ruleSetCode: values.ruleSetCode.trim() } : {}), ...(values.status ? { status: values.status } : {}) })}>
      <Form.Item label="决策点" name="pointCode"><Select options={[{ value: 'CHECKOUT_PREPARE', label: '结账准备' }]} /></Form.Item>
      <Form.Item label="规则集编码" name="ruleSetCode" rules={[{ validator: (_, value: string | undefined) => {
        const message = ruleSetCodeError(value ?? ''); return message ? Promise.reject(new Error(message)) : Promise.resolve();
      } }]}><Input autoComplete="off" /></Form.Item>
      <Form.Item label="状态" name="status"><Select allowClear placeholder="全部状态" options={Object.entries(ruleSetStatusLabels).filter(([value]) => value !== 'RETIRED').map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Button htmlType="submit" disabled={disabled}>查询</Button>
    </Form>
    <div className="table-toolbar"><span className="table-caption">共 {data.total} 个规则集</span><Button onClick={refresh} loading={loading} disabled={disabled || invalidQuery}>刷新</Button></div>
    <Table<RuleSetListItem> rowKey="ruleSetId" dataSource={error || invalidQuery ? [] : data.items} loading={loading} pagination={pagination}
      scroll={{ x: 1000 }} locale={{ emptyText: error ? '暂时无法加载规则集，请重试。' : '暂无规则集' }} columns={[
        { title: '规则集', render: (_, row) => <><strong>{row.ruleSetName}</strong><div className="cell-meta">{row.ruleSetCode}</div></> },
        { title: '状态', render: (_, row) => <Tag>{ruleSetStatusLabels[row.status]}</Tag> },
        { title: '创建人 / 时间', render: (_, row) => <>{row.createdBy}<div className="cell-meta">{formatRiskTime(row.createTime)}</div></> },
        { title: '冻结人 / 时间', render: (_, row) => <>{row.frozenBy ?? '—'}<div className="cell-meta">{formatRiskTime(row.frozenTime)}</div></> },
        { title: '操作', render: (_, row) => <Dropdown autoFocus trigger={['click', 'hover']} menu={{ items: [
          { key: 'detail', label: '详情', onClick: () => onSelect(row) },
          ...(resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === 'OPERATIONS_RISK_RULE_SET_HISTORY') ? [{ key: 'history', label: '历史版本', onClick: () => onHistory(row) }] : []),
          ...(resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === 'OPERATIONS_RISK_RULE_SET_REFERENCES') ? [{ key: 'references', label: '引用关系', onClick: () => onReferences(row) }] : []),
          ...(hasRiskAction(resources, 'FREEZE') && row.status === 'DRAFT' ? [{ key: 'freeze', label: '冻结版本', disabled: !canEdit, onClick: () => onFreeze(row) }] : []),
          ...(hasRiskAction(resources, 'SAVE') ? [{ key: 'edit', label: '编辑', disabled: !canEdit, onClick: () => onEdit(row) }] : []),
          ...(hasRiskAction(resources, 'DELETE') ? [{ key: 'delete', danger: true, disabled: row.inUse,
            label: row.inUse ? <Tooltip title="规则集正在使用，不能删除"><span>删除</span></Tooltip> : '删除', onClick: () => onDelete(row) }] : []),
        ] }} disabled={disabled}><Button type="link" aria-label={`操作 ${row.ruleSetCode}`} aria-haspopup="menu" disabled={disabled}>操作 ▾</Button></Dropdown> },
      ]} />
  </section>;
}
