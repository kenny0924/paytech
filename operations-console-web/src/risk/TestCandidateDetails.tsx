import { Alert, Button, Descriptions, Table, Tag } from 'antd';
import type { ReleaseStateChange, ReleaseStaticCheck, TestCandidateDetail } from './riskApiModels';
import { classifyTestCandidate } from './testCandidateState';
import { ContentHash, formatRiskTime } from './RuleSetDetails';

export const candidateStatusLabels = { DRAFT: '待校验', VALIDATING: '校验中 / 等待后续阶段', REJECTED: '已拒绝' } as const;
const checkLabels: Readonly<Record<string, string>> = {
  REFERENCE_INTEGRITY: '引用完整性', AST_SHAPE: '条件结构', AST_TYPES: '条件类型', FEATURE_DEPENDENCY: '特征依赖',
  HANDLER_HASH: '动作实现一致性', ACTION_PARAMETERS: '动作参数', TERMINAL_ACTION: '终态动作', SYSTEM_OBLIGATION: '系统义务',
};
export function TestCandidateDetails({ detail, actorId, canValidate, disabled, onValidate }: {
  detail: TestCandidateDetail; actorId: string; canValidate: boolean; disabled: boolean; onValidate: () => void;
}) {
  const presentation = classifyTestCandidate(detail, actorId);
  const composition = detail.frozenComposition;
  return <div className="risk-details">
    {presentation.kind === 'READY_TO_VALIDATE' && <Alert type="info" title="Candidate 已创建，可执行八项静态校验。"
      action={canValidate && <Button type="primary" disabled={disabled} onClick={onValidate}>执行静态校验</Button>} />}
    {presentation.kind === 'RECOVERABLE_BY_CURRENT_ACTOR' && <Alert type="warning" title="上次校验尚无完整结果，可使用原命令恢复。"
      action={canValidate && <Button type="primary" disabled={disabled} onClick={onValidate}>恢复校验</Button>} />}
    {presentation.kind === 'RECOVERY_OWNED_BY_OTHER_ACTOR' && <Alert type="warning" title="校验由其他操作员发起，当前账号不能接管。"
      description={`请联系原操作员 ${presentation.ownerActorId}，或从同一冻结规则集创建新的 Candidate。`} />}
    {presentation.kind === 'WAITING_FOR_SHADOW' && <Alert type="success" title="静态校验通过，等待未来 Shadow" description="本期没有激活或生产发布操作。" />}
    {presentation.kind === 'REJECTED' && <Alert type="warning" title="静态校验未通过，Candidate 已拒绝。" description="请查看失败项；修正规则并保存、冻结新版本后，创建新的 Candidate。" />}
    <Descriptions bordered size="small" column={1} items={[
      { key: 'release', label: 'Release No / ID', children: `${detail.releaseNo} / ${detail.releaseId}` },
      { key: 'status', label: '状态 / 版本', children: `${candidateStatusLabels[detail.status]} / ${detail.version}` },
      { key: 'environment', label: '环境', children: <Tag>TEST</Tag> },
      { key: 'hash', label: '内容摘要', children: <ContentHash hash={detail.contentHash} /> },
      { key: 'created', label: '创建人 / 时间', children: `${detail.createdBy} / ${formatRiskTime(detail.createTime)}` },
    ]} />
    <h3>冻结组成</h3>
    <Descriptions bordered size="small" column={1} items={[
      { key: 'point', label: '决策点 / ID', children: `${composition.pointCode} / ${composition.decisionPointId}` },
      { key: 'pointVersion', label: '决策点版本 / ID', children: `${composition.pointVersionNo} / ${composition.pointVersionId}` },
      { key: 'pointHash', label: '决策点内容摘要', children: <ContentHash hash={composition.pointVersionContentHash} /> },
      { key: 'ruleSet', label: '规则集编码', children: composition.ruleSetCode },
      { key: 'ruleVersion', label: '规则集版本 / ID', children: `${composition.ruleSetVersionNo} / ${composition.ruleSetVersionId}` },
      { key: 'ruleHash', label: '规则集内容摘要', children: <ContentHash hash={composition.ruleSetContentHash} /> },
    ]} />
    <h3>状态变化</h3>
    <Table<ReleaseStateChange> size="small" rowKey="auditEventId" pagination={false} dataSource={[...detail.stateChanges].sort((left, right) => left.aggregateVersion - right.aggregateVersion)} scroll={{ x: 700 }} columns={[
      { title: '版本', dataIndex: 'aggregateVersion' },
      { title: '变化', render: (_, row) => `${row.fromStatus ? candidateStatusLabels[row.fromStatus] : '创建'} → ${candidateStatusLabels[row.toStatus]}` },
      { title: '原因编码', dataIndex: 'reasonCode' }, { title: '操作员', dataIndex: 'actorId' },
      { title: '时间', dataIndex: 'eventTime', render: formatRiskTime },
    ]} />
    <h3>八项静态检查</h3>
    {detail.staticChecks.length === 0 ? <p>尚无检查结果。</p> : <Table<ReleaseStaticCheck> size="small" rowKey="checkCode" pagination={false}
      dataSource={detail.staticChecks} scroll={{ x: 600 }} columns={[
        { title: '检查项', dataIndex: 'checkCode', render: (value: string) => checkLabels[value] ?? '检查项' },
        { title: '结果', render: (_, row) => <Tag color={row.checkStatus === 'PASSED' ? 'green' : 'red'}>{row.checkStatus === 'PASSED' ? '通过' : '失败'}</Tag> },
        { title: '级别', dataIndex: 'severity', render: value => value === 'ERROR' ? '错误' : '信息' },
        { title: '安全摘要', render: (_, row) => <>{row.errorSummary ?? '—'}{row.errorCode && <div className="cell-meta">{row.errorCode}</div>}</> },
      ]} />}
  </div>;
}
