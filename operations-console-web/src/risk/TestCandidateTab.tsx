import { useEffect, useLayoutEffect, useRef } from 'react';
import { Button, Form, Select, Table, Tag } from 'antd';
import { usePaging } from '../hooks/usePaging';
import type { TestCandidateListItem } from './riskApiModels';
import type { CandidateFilters } from './riskDecisionApi';
import { decodeCandidatePage } from './riskResponseValidation';
import { ContentHash, formatRiskTime } from './RuleSetDetails';
import { candidateStatusLabel, candidateStatuses } from './TestCandidateDetails';
import { useT } from '../i18n/react';

export function TestCandidateTab({ revision, disabled, onSelect, onReadFailure }: { revision: number; disabled: boolean; onSelect: (id: string) => void; onReadFailure: () => void }) {
  const { data, loading, error, refresh, applyFilters, pagination } = usePaging<TestCandidateListItem>('/decision-releases/v1/candidates', decodeCandidatePage, { pointCode: 'CHECKOUT_PREPARE' });
  const t = useT('risk');
  const previousRevision = useRef(revision);
  const readFailure = useRef(onReadFailure);
  useLayoutEffect(() => { readFailure.current = onReadFailure; }, [onReadFailure]);
  useEffect(() => { if (error) readFailure.current(); }, [error]);
  useEffect(() => {
    if (previousRevision.current !== revision) { previousRevision.current = revision; refresh(); }
  }, [revision, refresh]);
  return <section className="table-panel">
    <Form layout="inline" className="risk-filters" initialValues={{ pointCode: 'CHECKOUT_PREPARE' }}
      onFinish={(values: CandidateFilters) => applyFilters({ pointCode: values.pointCode || 'CHECKOUT_PREPARE', ...(values.status ? { status: values.status } : {}) })}>
      <Form.Item label={t('candidateList.point')} name="pointCode"><Select options={[{ value: 'CHECKOUT_PREPARE', label: t('point.CHECKOUT_PREPARE') }]} /></Form.Item>
      <Form.Item label={t('candidateList.status')} name="status"><Select allowClear placeholder={t('candidateList.allStatuses')} options={candidateStatuses.map(value => ({ value, label: candidateStatusLabel(value) }))} /></Form.Item>
      <Button htmlType="submit">{t('candidateList.search')}</Button>
    </Form>
    <div className="table-toolbar"><span className="table-caption">{t('candidateList.count', { count: data.total })}</span><Button onClick={refresh} loading={loading}>{t('candidateList.refresh')}</Button></div>
    <Table<TestCandidateListItem> rowKey="releaseId" dataSource={error ? [] : data.items} loading={loading} pagination={pagination}
      scroll={{ x: 1100 }} locale={{ emptyText: error ? t('candidateList.loadFailed') : t('candidateList.empty') }} columns={[
        { title: 'Release No', dataIndex: 'releaseNo' }, { title: t('candidateList.column.ruleSetVersionId'), dataIndex: 'ruleSetVersionId' },
        { title: t('candidateList.status'), render: (_, row) => <Tag>{candidateStatusLabel(row.status)}</Tag> }, { title: t('candidate.column.version'), dataIndex: 'version' },
        { title: t('candidate.field.contentHash'), render: (_, row) => <ContentHash hash={row.contentHash} /> },
        { title: t('candidateList.column.staticChecks'), render: (_, row) => row.allStaticChecksPassed === null ? t('candidateList.checks.incomplete') : row.allStaticChecksPassed ? t('candidateList.checks.allPassed') : t('candidateList.checks.failed') },
        { title: t('candidate.field.created'), render: (_, row) => <>{row.createdBy}<div className="cell-meta">{formatRiskTime(row.createTime)}</div></> },
        { title: t('candidateList.column.actions'), render: (_, row) => <Button type="link" disabled={disabled} onClick={() => onSelect(row.releaseId)}>{t('candidateList.viewDetails')}</Button> },
      ]} />
  </section>;
}
