import { Alert, Button, Descriptions, Table, Tag } from 'antd';
import type { ReleaseStateChange, ReleaseStaticCheck, TestCandidateDetail } from './riskApiModels';
import { classifyTestCandidate } from './testCandidateState';
import { ContentHash, formatRiskTime } from './RuleSetDetails';
import { enumLabel, tOr, translator } from '../i18n/index';
import { useT } from '../i18n/react';

/** Candidate 状态与检查项显示名是 `risk.enum.candidateStatus.<值>`、`risk.enum.candidateCheck.<值>`。 */
export const candidateStatuses = ['DRAFT', 'VALIDATING', 'REJECTED'] as const;
export function candidateStatusLabel(status: string): string { return enumLabel(translator('risk'), 'enum.candidateStatus', status); }
export function TestCandidateDetails({ detail, actorId, canValidate, disabled, onValidate }: {
  detail: TestCandidateDetail; actorId: string; canValidate: boolean; disabled: boolean; onValidate: () => void;
}) {
  const t = useT('risk');
  const presentation = classifyTestCandidate(detail, actorId);
  const composition = detail.frozenComposition;
  return <div className="risk-details">
    {presentation.kind === 'READY_TO_VALIDATE' && <Alert type="info" title={t('candidate.ready')}
      action={canValidate && <Button type="primary" disabled={disabled} onClick={onValidate}>{t('candidate.validate')}</Button>} />}
    {presentation.kind === 'RECOVERABLE_BY_CURRENT_ACTOR' && <Alert type="warning" title={t('candidate.recoverable')}
      action={canValidate && <Button type="primary" disabled={disabled} onClick={onValidate}>{t('candidate.recover')}</Button>} />}
    {presentation.kind === 'RECOVERY_OWNED_BY_OTHER_ACTOR' && <Alert type="warning" title={t('candidate.otherActor')}
      description={t('candidate.otherActorHint', { actorId: presentation.ownerActorId })} />}
    {presentation.kind === 'WAITING_FOR_SHADOW' && <Alert type="success" title={t('candidate.waitingForShadow')} description={t('candidate.noActivation')} />}
    {presentation.kind === 'REJECTED' && <Alert type="warning" title={t('candidate.rejected')} description={t('candidate.rejectedHint')} />}
    <Descriptions bordered size="small" column={1} items={[
      { key: 'release', label: 'Release No / ID', children: `${detail.releaseNo} / ${detail.releaseId}` },
      { key: 'status', label: t('candidate.field.statusVersion'), children: `${candidateStatusLabel(detail.status)} / ${detail.version}` },
      { key: 'environment', label: t('candidate.field.environment'), children: <Tag>TEST</Tag> },
      { key: 'hash', label: t('candidate.field.contentHash'), children: <ContentHash hash={detail.contentHash} /> },
      { key: 'created', label: t('candidate.field.created'), children: `${detail.createdBy} / ${formatRiskTime(detail.createTime)}` },
    ]} />
    <h3>{t('candidate.composition')}</h3>
    <Descriptions bordered size="small" column={1} items={[
      { key: 'point', label: t('candidate.field.point'), children: `${composition.pointCode} / ${composition.decisionPointId}` },
      { key: 'pointVersion', label: t('candidate.field.pointVersion'), children: `${composition.pointVersionNo} / ${composition.pointVersionId}` },
      { key: 'pointHash', label: t('candidate.field.pointHash'), children: <ContentHash hash={composition.pointVersionContentHash} /> },
      { key: 'ruleSet', label: t('candidate.field.ruleSetCode'), children: composition.ruleSetCode },
      { key: 'ruleVersion', label: t('candidate.field.ruleSetVersion'), children: `${composition.ruleSetVersionNo} / ${composition.ruleSetVersionId}` },
      { key: 'ruleHash', label: t('candidate.field.ruleSetHash'), children: <ContentHash hash={composition.ruleSetContentHash} /> },
    ]} />
    <h3>{t('candidate.stateChanges')}</h3>
    <Table<ReleaseStateChange> size="small" rowKey="auditEventId" pagination={false} dataSource={[...detail.stateChanges].sort((left, right) => left.aggregateVersion - right.aggregateVersion)} scroll={{ x: 700 }} columns={[
      { title: t('candidate.column.version'), dataIndex: 'aggregateVersion' },
      { title: t('candidate.column.change'), render: (_, row) => `${row.fromStatus ? candidateStatusLabel(row.fromStatus) : t('candidate.created')} → ${candidateStatusLabel(row.toStatus)}` },
      { title: t('candidate.column.reasonCode'), dataIndex: 'reasonCode' }, { title: t('candidate.column.actor'), dataIndex: 'actorId' },
      { title: t('candidate.column.time'), dataIndex: 'eventTime', render: formatRiskTime },
    ]} />
    <h3>{t('candidate.staticChecks')}</h3>
    {detail.staticChecks.length === 0 ? <p>{t('candidate.noChecks')}</p> : <Table<ReleaseStaticCheck> size="small" rowKey="checkCode" pagination={false}
      dataSource={detail.staticChecks} scroll={{ x: 600 }} columns={[
        { title: t('candidate.column.check'), dataIndex: 'checkCode', render: (value: string) => tOr(t, 'enum.candidateCheck.' + value, t('candidate.column.check')) },
        { title: t('candidate.column.result'), render: (_, row) => <Tag color={row.checkStatus === 'PASSED' ? 'green' : 'red'}>{row.checkStatus === 'PASSED' ? t('candidate.passed') : t('candidate.failed')}</Tag> },
        { title: t('candidate.column.severity'), dataIndex: 'severity', render: value => value === 'ERROR' ? t('candidate.severity.error') : t('candidate.severity.info') },
        { title: t('candidate.column.summary'), render: (_, row) => <>{row.errorSummary ?? '—'}{row.errorCode && <div className="cell-meta">{row.errorCode}</div>}</> },
      ]} />}
  </div>;
}
