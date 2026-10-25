import { useMemo, useState } from 'react';
import { Alert, Button, Empty, Form, Input, Select, Space, Table, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { Loading, QueryFailure, date, useDetail } from '../tenantAccess/TenantAccessView';
import {
  decodeQualificationPage, decodeSubject, hasButton, isRoleId, qualificationActions, pageSizes, qualificationApi, qualificationManageButton,
  qualificationRoute, qualificationStatuses, qualificationTypes, readListQuery, roleTypes, subjectQualificationSlots,
} from '../qualification/qualification';
import type { Qualification, QualificationRow, RoleType, Subject } from '../qualification/qualification';
import { HistoryDrawer, ManualDecisionModal, RevokeModal } from '../qualification/QualificationDialogs';
import { EffectiveStatusTag } from '../qualification/QualificationParts';
import { enumLabel } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import '../configuration/configuration.css';
import '../qualification/qualification.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Dialog =
  | { kind: 'decision'; subject: { roleType: RoleType; roleId: string; displayName: string | null }; existing: Qualification | null; items: Qualification[] }
  | { kind: 'revoke' | 'history'; qualification: Qualification; subjectName: string | null };
const options = (t: Translate, prefix: string, codes: readonly string[]) => codes.map(value => ({ value, label: enumLabel(t, prefix, value) }));
const subjectStatusColor = (status: string) => status === 'ACTIVE' ? 'green' : status === 'TERMINATED' || status === 'CLOSED' ? 'red' : 'orange';

/**
 * 主体资格（RS-01—03）：/compliance/qualifications。顶部按角色与编号查找主体并显示其各资格类型的实时状态，
 * 下方为资格列表（角色 / 资格类型 / 存储状态筛选）；行操作查看历史、登记、撤销。选中主体与筛选都写入 URL，
 * 经营主体与 Local Partner 详情的“在主体资格中查看”以 ?roleType=&roleId= 预填主体。
 */
export function Qualifications(props: Props) {
  const location = useLocation();
  if (location.pathname.replace(/\/$/, '') !== qualificationRoute) return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  return <QualificationWorkspace key={location.search} {...props} />;
}

function QualificationWorkspace({ resources, onAuthorizationChanged }: Props) {
  const t = useT('qualification'), tCommon = useT('common'), tNav = useT('nav');
  const location = useLocation(); const navigate = useNavigate(); const [finder] = Form.useForm(); const [form] = Form.useForm();
  const params = new URLSearchParams(location.search);
  const query = readListQuery(location.search);
  const size = pageSizes.includes(Number(params.get('size'))) ? Number(params.get('size')) : 20;
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const [revision, setRevision] = useState(0);
  const canManage = hasButton(resources, qualificationManageButton);
  const go = (next: Record<string, string>, pageNo = 1, pageSize = size) => {
    void navigate(`?${new URLSearchParams({ ...next, page: String(pageNo), size: String(pageSize) })}`);
  };
  const page = usePaging<QualificationRow>(`${qualificationApi}/qualifications`, decodeQualificationPage, query, {
    initialPaging: { page: Math.max(1, Number(params.get('page')) || 1), size }, notifyOnError: false,
    onQueryChange: next => go(next.filters as Record<string, string>, next.paging.page, next.paging.size) });
  const subject = query.roleType && query.roleId ? { roleType: query.roleType as RoleType, roleId: query.roleId } : null;
  const done = () => { setDialog(null); setRevision(v => v + 1); page.refresh(); };
  const reload = () => { setDialog(null); setRevision(v => v + 1); page.refresh(); };
  const finderRole: string | undefined = Form.useWatch('roleType', finder);
  return <div className="configuration-page qualification-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_QUALIFICATIONS')}</h1>
      <Button onClick={() => { page.refresh(); setRevision(v => v + 1); }} loading={page.loading}>{tCommon('action.refresh')}</Button></div>
    <section className="qualification-section"><h2>{t('finder.title')}</h2>
      <Form form={finder} layout="vertical" initialValues={{ roleType: query.roleType ?? 'OPERATING_ENTITY', subjectRef: query.roleId }}
        onFinish={values => go({ ...omitSubject(query), roleType: values.roleType, roleId: values.subjectRef.trim().toUpperCase() })}>
        <div className="qualification-finder">
          <Form.Item name="roleType" label={t('field.roleType')} rules={[{ required: true }]}><Select options={options(t, 'enum.roleType', roleTypes)}
            onChange={() => finder.setFields([{ name: 'subjectRef', errors: [] }])} /></Form.Item>
          <Form.Item name="subjectRef" label={t('finder.subjectRef')} normalize={(v?: string) => finderRole === 'OPERATING_ENTITY' ? v?.toUpperCase() : v}
            rules={[{ required: true, message: t('finder.subjectRefRequired') }, ({ getFieldValue }) => ({ validator: (_, value?: string) =>
              !value || isRoleId(getFieldValue('roleType'), value.trim().toUpperCase()) ? Promise.resolve()
                : Promise.reject(new Error(getFieldValue('roleType') === 'OPERATING_ENTITY' ? t('finder.entityNoInvalid') : t('finder.idInvalid'))) })]}>
            <Input allowClear maxLength={19} placeholder={finderRole === 'OPERATING_ENTITY' ? 'AE001' : '9001'} /></Form.Item>
          <Space><Button type="primary" htmlType="submit">{t('finder.search')}</Button>
            {subject && <Button onClick={() => go(omitSubject(query))}>{t('finder.clear')}</Button>}</Space>
        </div>
      </Form>
      {subject ? <SubjectCard key={`${subject.roleType}:${subject.roleId}:${revision}`} roleType={subject.roleType} roleId={subject.roleId} canManage={canManage}
        onDialog={setDialog} /> : <div className="muted">{t('finder.hint')}</div>}
    </section>
    <Form form={form} layout="vertical" initialValues={query} onFinish={values => go({ ...pick(query, subject ? ['roleType', 'roleId'] : []),
      ...compact({ roleType: subject ? undefined : values.roleType, qualificationType: values.qualificationType, status: values.status }) })}>
      <div className="qualification-search">
        <Form.Item name="roleType" label={t('field.roleType')}><Select allowClear disabled={subject !== null} placeholder={t('list.allRoles')} options={options(t, 'enum.roleType', roleTypes)} /></Form.Item>
        <Form.Item name="qualificationType" label={t('field.qualificationType')}><Select allowClear placeholder={t('list.allTypes')} options={options(t, 'enum.qualificationType', qualificationTypes)} /></Form.Item>
        <Form.Item name="status" label={t('field.status')}><Select allowClear placeholder={t('list.allStatuses')} options={options(t, 'enum.status', qualificationStatuses)} /></Form.Item>
        <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('list.search')}</Button>
          <Button onClick={() => go(pick(query, subject ? ['roleType', 'roleId'] : []))}>{t('list.reset')}</Button></div>
      </div>
    </Form>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} /> : <section className="table-panel">
      <Table<QualificationRow> rowKey={row => row.qualification.qualificationId} tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1180 }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.keys(query).length ? t('list.noMatch') : t('list.empty')} /> }}
        pagination={{ ...page.pagination, pageSizeOptions: pageSizes, showTotal: total => t('list.total', { total }) }} columns={[
          { title: t('list.column.subject'), width: 220, render: (_, row) => <div><div>{row.subjectName ?? <span className="muted">{t('list.nameUnavailable')}</span>}</div>
            <div className="cell-meta">{enumLabel(t, 'enum.roleType', row.qualification.roleType)} · <span className="qualification-mono">{row.qualification.roleId}</span></div></div> },
          { title: t('field.qualificationType'), width: 190, render: (_, row) => enumLabel(t, 'enum.qualificationType', row.qualification.qualificationType) },
          { title: t('field.effectiveStatus'), width: 140, render: (_, row) => <EffectiveStatusTag status={row.qualification.effectiveStatus} /> },
          { title: t('field.status'), width: 110, render: (_, row) => enumLabel(t, 'enum.status', row.qualification.status) },
          { title: t('field.validity'), width: 250, render: (_, row) => t('field.validityRange', { from: date(row.qualification.validFrom), to: date(row.qualification.validTo) }) },
          { title: t('field.decisionSource'), width: 120, render: (_, row) => enumLabel(t, 'enum.decisionSource', row.qualification.decisionSource) },
          { title: t('field.updatedAt'), width: 170, render: (_, row) => date(row.qualification.updatedAt) },
          { title: t('list.column.actions'), width: 220, fixed: 'right', render: (_, row) => { const actions = qualificationActions(row.qualification, canManage); return <Space size={0} wrap>
            <Button type="link" size="small" onClick={() => setDialog({ kind: 'history', qualification: row.qualification, subjectName: row.subjectName })}>{t('action.history')}</Button>
            {actions.register && <Button type="link" size="small" onClick={() => setDialog({ kind: 'decision', existing: row.qualification, items: [row.qualification],
              subject: { roleType: row.qualification.roleType, roleId: row.qualification.roleId, displayName: row.subjectName } })}>{t('action.register')}</Button>}
            {actions.revoke && <Button type="link" size="small" danger
              onClick={() => setDialog({ kind: 'revoke', qualification: row.qualification, subjectName: row.subjectName })}>{t('action.revoke')}</Button>}
          </Space>; } },
        ]} /></section>}
    {dialog?.kind === 'decision' && <ManualDecisionModal subject={dialog.subject} existing={dialog.existing} items={dialog.items} onClose={() => setDialog(null)}
      onDone={done} onReload={reload} onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog?.kind === 'revoke' && <RevokeModal qualification={dialog.qualification} subjectName={dialog.subjectName} onClose={() => setDialog(null)}
      onDone={done} onReload={reload} onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog?.kind === 'history' && <HistoryDrawer qualification={dialog.qualification} subjectName={dialog.subjectName} onClose={() => setDialog(null)} />}
  </div>;
}

/** 主体卡（B7 + 该主体的 B8）：名称、状态，以及每个允许资格类型的实时状态与登记 / 撤销 / 历史操作。 */
function SubjectCard({ roleType, roleId, canManage, onDialog }: { roleType: RoleType; roleId: string; canManage: boolean; onDialog: (dialog: Dialog) => void }) {
  const t = useT('qualification');
  const decodeFound = useMemo(() => (value: unknown) => decodeSubject(value, roleType, roleId), [roleType, roleId]);
  const found = useDetail<Subject>(`${qualificationApi}/subjects?${new URLSearchParams({ roleType, subjectRef: roleId })}`, decodeFound);
  const items = useDetail(`${qualificationApi}/qualifications?${new URLSearchParams({ roleType, roleId, page: '1', size: '10' })}`, decodeQualificationPage);
  if ((found.loading && !found.data) || (items.loading && !items.data)) return <Loading />;
  if (found.failure instanceof ApiError && found.failure.status === 404) {
    return <Alert showIcon type="warning" title={t('subject.notFound', { role: enumLabel(t, 'enum.roleType', roleType), roleId })} />;
  }
  if (found.failure || !found.data) return <QueryFailure failure={found.failure} retry={found.refresh} />;
  const subject = found.data;
  const rows = items.data?.items.map(row => row.qualification) ?? [];
  const terminated = subject.status === 'TERMINATED' || subject.status === 'CLOSED';
  const name = subject.displayName ?? subject.roleId;
  return <div>
    <div className="qualification-subject-head"><strong>{name}</strong>
      <span className="cell-meta">{enumLabel(t, 'enum.roleType', subject.roleType)} · <span className="qualification-mono">{subject.roleId}</span></span>
      <Tag color={subjectStatusColor(subject.status)}>{enumLabel(t, 'enum.subjectStatus', subject.status)}</Tag></div>
    {terminated && <Alert showIcon type="info" style={{ marginBottom: 8 }} title={t('subject.terminated')} />}
    {items.failure ? <QueryFailure failure={items.failure} retry={items.refresh} />
      : subjectQualificationSlots(subject.roleType, rows).map(slot => { const actions = qualificationActions(slot.qualification, canManage, terminated); return <div key={slot.qualificationType} className="qualification-slot">
        <span>{enumLabel(t, 'enum.qualificationType', slot.qualificationType)}</span>
        <EffectiveStatusTag status={slot.qualification?.effectiveStatus ?? 'NOT_REGISTERED'} />
        <span className="cell-meta">{slot.qualification ? t('summary.meta', { from: date(slot.qualification.validFrom), to: date(slot.qualification.validTo),
          source: enumLabel(t, 'enum.decisionSource', slot.qualification.decisionSource) }) : t('subject.notRegistered')}</span>
        <Space size={0} wrap>
          {actions.history && <Button type="link" size="small" onClick={() => onDialog({ kind: 'history', qualification: slot.qualification!, subjectName: subject.displayName })}>{t('action.history')}</Button>}
          {actions.register && <Button type="link" size="small" onClick={() => onDialog({ kind: 'decision', existing: slot.qualification, items: rows,
            subject: { roleType: subject.roleType, roleId: subject.roleId, displayName: subject.displayName } })}>{t('action.register')}</Button>}
          {actions.revoke && <Button type="link" size="small" danger
            onClick={() => onDialog({ kind: 'revoke', qualification: slot.qualification!, subjectName: subject.displayName })}>{t('action.revoke')}</Button>}
        </Space>
      </div>; })}
  </div>;
}

function omitSubject(query: Record<string, string>): Record<string, string> {
  return Object.fromEntries(Object.entries(query).filter(([key]) => key !== 'roleType' && key !== 'roleId'));
}
function pick(query: Record<string, string>, keys: string[]): Record<string, string> {
  return Object.fromEntries(Object.entries(query).filter(([key]) => keys.includes(key)));
}
function compact(values: Record<string, string | undefined>): Record<string, string> {
  return Object.fromEntries(Object.entries(values).filter(([, v]) => v?.trim()).map(([k, v]) => [k, v!.trim()]));
}
