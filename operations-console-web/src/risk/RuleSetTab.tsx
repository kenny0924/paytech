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
import { formatRiskTime, ruleSetStatusLabel, ruleSetStatuses } from './RuleSetDetails';
import { useT } from '../i18n/react';
import { useRiskPoints } from './riskPoints';

export function RuleSetTab({ revision, disabled, navigation, resources, canEdit, onNavigate, onSelect, onEdit, onDelete, onHistory, onReferences, onFreeze, onReadFailure }: {
  revision: number; disabled: boolean; navigation: RiskNavigation; resources: readonly ConsoleResource[]; canEdit: boolean;
  onNavigate: (next: RiskNavigation, replace?: boolean) => void; onSelect: (row: RuleSetListItem) => void;
  onHistory: (row: RuleSetListItem) => void; onReferences: (row: RuleSetListItem) => void; onFreeze: (row: RuleSetListItem) => void;
  onEdit: (row: RuleSetListItem) => void; onDelete: (row: RuleSetListItem) => void; onReadFailure: () => void;
}) {
  const t = useT('risk');
  const [form] = Form.useForm();
  const points = useRiskPoints();
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
      onFinish={(values: RuleSetFilters) => applyFilters({ pointCode: values.pointCode || navigation.filters.pointCode || 'CHECKOUT_PREPARE',
        ...(values.ruleSetCode?.trim() ? { ruleSetCode: values.ruleSetCode.trim() } : {}), ...(values.status ? { status: values.status } : {}) })}>
      <Form.Item label={t('ruleSetList.point')} name="pointCode"><Select aria-label={t('ruleSetList.point')} style={{ width: 160 }} disabled={disabled} options={points.map(p => ({ value: p.pointCode, label: p.pointName }))}
        onChange={pointCode => applyFilters({ pointCode })} /></Form.Item>
      <Form.Item label={t('fieldError.ruleSetCode')} name="ruleSetCode" rules={[{ validator: (_, value: string | undefined) => {
        const message = ruleSetCodeError(value ?? ''); return message ? Promise.reject(new Error(message)) : Promise.resolve();
      } }]}><Input autoComplete="off" /></Form.Item>
      <Form.Item label={t('candidateList.status')} name="status"><Select allowClear placeholder={t('candidateList.allStatuses')} options={ruleSetStatuses.filter(value => value !== 'RETIRED').map(value => ({ value, label: ruleSetStatusLabel(value) }))} /></Form.Item>
      <Button htmlType="submit" disabled={disabled}>{t('candidateList.search')}</Button>
    </Form>
    <div className="table-toolbar"><span className="table-caption">{t('ruleSetList.count', { count: data.total })}</span><Button onClick={refresh} loading={loading} disabled={disabled || invalidQuery}>{t('candidateList.refresh')}</Button></div>
    <Table<RuleSetListItem> rowKey="ruleSetId" dataSource={error || invalidQuery ? [] : data.items} loading={loading} pagination={pagination}
      scroll={{ x: 1000 }} locale={{ emptyText: error ? t('ruleSetList.loadFailed') : t('ruleSetList.empty') }} columns={[
        { title: t('ruleSetPicker.column.ruleSet'), render: (_, row) => <><strong>{row.ruleSetName}</strong><div className="cell-meta">{row.ruleSetCode}</div></> },
        { title: t('candidateList.status'), render: (_, row) => <Tag>{ruleSetStatusLabel(row.status)}</Tag> },
        { title: t('candidate.field.created'), render: (_, row) => <>{row.createdBy}<div className="cell-meta">{formatRiskTime(row.createTime)}</div></> },
        { title: t('ruleSetDetails.frozen'), render: (_, row) => <>{row.frozenBy ?? '—'}<div className="cell-meta">{formatRiskTime(row.frozenTime)}</div></> },
        { title: t('candidateList.column.actions'), render: (_, row) => <Dropdown autoFocus trigger={['click', 'hover']} menu={{ items: [
          { key: 'detail', label: t('ruleSetList.action.detail'), onClick: () => onSelect(row) },
          ...(resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === 'OPERATIONS_RISK_RULE_SET_HISTORY') ? [{ key: 'history', label: t('ruleSetList.action.history'), onClick: () => onHistory(row) }] : []),
          ...(resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === 'OPERATIONS_RISK_RULE_SET_REFERENCES') ? [{ key: 'references', label: t('ruleSetList.action.references'), onClick: () => onReferences(row) }] : []),
          ...(hasRiskAction(resources, 'FREEZE') && row.status === 'DRAFT' ? [{ key: 'freeze', label: t('ruleSetList.action.freeze'), disabled: !canEdit, onClick: () => onFreeze(row) }] : []),
          ...(hasRiskAction(resources, 'SAVE') ? [{ key: 'edit', label: t('ruleSetList.action.edit'), disabled: !canEdit, onClick: () => onEdit(row) }] : []),
          ...(hasRiskAction(resources, 'DELETE') ? [{ key: 'delete', danger: true, disabled: row.inUse,
            label: row.inUse ? <Tooltip title={t('ruleSetList.inUse')}><span>{t('ruleSetList.action.delete')}</span></Tooltip> : t('ruleSetList.action.delete'), onClick: () => onDelete(row) }] : []),
        ] }} disabled={disabled}><Button type="link" aria-label={t('ruleSetList.actionsFor', { code: row.ruleSetCode })} aria-haspopup="menu" disabled={disabled}>{t('ruleSetList.actions')}</Button></Dropdown> },
      ]} />
  </section>;
}
