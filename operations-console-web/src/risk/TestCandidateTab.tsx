import { useEffect, useLayoutEffect, useRef } from 'react';
import { Button, Form, Select, Table, Tag } from 'antd';
import { usePaging } from '../hooks/usePaging';
import type { TestCandidateListItem } from './riskApiModels';
import type { CandidateFilters } from './riskDecisionApi';
import { decodeCandidatePage } from './riskResponseValidation';
import { ContentHash, formatRiskTime } from './RuleSetDetails';
import { candidateStatusLabels } from './TestCandidateDetails';

export function TestCandidateTab({ revision, disabled, onSelect, onReadFailure }: { revision: number; disabled: boolean; onSelect: (id: string) => void; onReadFailure: () => void }) {
  const { data, loading, error, refresh, applyFilters, pagination } = usePaging<TestCandidateListItem>('/decision-releases/v1/candidates', decodeCandidatePage, { pointCode: 'CHECKOUT_PREPARE' });
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
      <Form.Item label="决策点" name="pointCode"><Select options={[{ value: 'CHECKOUT_PREPARE', label: '结账准备' }]} /></Form.Item>
      <Form.Item label="状态" name="status"><Select allowClear placeholder="全部状态" options={Object.entries(candidateStatusLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Button htmlType="submit">查询</Button>
    </Form>
    <div className="table-toolbar"><span className="table-caption">共 {data.total} 个 TEST Candidate</span><Button onClick={refresh} loading={loading}>刷新</Button></div>
    <Table<TestCandidateListItem> rowKey="releaseId" dataSource={error ? [] : data.items} loading={loading} pagination={pagination}
      scroll={{ x: 1100 }} locale={{ emptyText: error ? '暂时无法加载 Candidate，请重试。' : '暂无 TEST Candidate' }} columns={[
        { title: 'Release No', dataIndex: 'releaseNo' }, { title: '规则集版本 ID', dataIndex: 'ruleSetVersionId' },
        { title: '状态', render: (_, row) => <Tag>{candidateStatusLabels[row.status]}</Tag> }, { title: '版本', dataIndex: 'version' },
        { title: '内容摘要', render: (_, row) => <ContentHash hash={row.contentHash} /> },
        { title: '静态检查', render: (_, row) => row.allStaticChecksPassed === null ? '尚无完整结果' : row.allStaticChecksPassed ? '全部通过' : '未通过' },
        { title: '创建人 / 时间', render: (_, row) => <>{row.createdBy}<div className="cell-meta">{formatRiskTime(row.createTime)}</div></> },
        { title: '操作', render: (_, row) => <Button type="link" disabled={disabled} onClick={() => onSelect(row.releaseId)}>查看详情</Button> },
      ]} />
  </section>;
}
