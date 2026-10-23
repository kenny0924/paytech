import { useMemo, useState } from 'react';
import { Button, Form, Input, Select, Table } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import { commercialButtons, decodePlan, entityNoPattern, hasButton, isId } from '../commercial/commercial';
import type { TermPlan } from '../commercial/commercial';
import { catalogTermTypeLabel } from '../commercial/catalogTree';
import { useT } from '../i18n/react';
import { CommandModal, CommercialNote, StatusTag } from '../commercial/CommercialParts';
import { CreateVersionModal } from '../commercial/TermModals';
import { TemplateVersionWorkspace } from '../commercial/TemplateVersionWorkspace';
import { decodeTemplateList, templateBody, templatePaths, templateRoutes, templateTermTypes } from '../commercial/templates';
import type { TemplateFormValues, TemplatePlan } from '../commercial/templates';
import { useCommercialCommand } from '../commercial/useCommercialCommand';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface FilterValues { entityNo?: string; termType?: string; status?: string }

/** 标准价目：/commercial/templates 模板方案列表，?versionId= 为模板版本工作区。 */
export function CommercialTemplates(props: Props) {
  const [params] = useSearchParams();
  const versionId = params.get('versionId') ?? undefined;
  if (versionId !== undefined) {
    return isId(versionId) ? <TemplateVersionWorkspace key={versionId} versionId={versionId} {...props} />
      : <QueryFailure failure={undefined} />;
  }
  return <TemplateList {...props} />;
}

/** 列表查询路径：只带 entityNo / termType / status（BFF 拒绝其他与空 Query），不分页。 */
function listPath(filters: FilterValues): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) if (typeof value === 'string' && value.trim()) query.set(key, value.trim().toUpperCase());
  const text = query.toString();
  return text ? `${templatePaths.templates}?${text}` : templatePaths.templates;
}

/** 模板方案列表：按 OE / 条款类型 / 状态筛选；名称、编号、条款类型、生效版本、最新版本状态。 */
function TemplateList({ resources, onAuthorizationChanged }: Props) {
  const t = useT('commercial'), tCommon = useT('common'), tNav = useT('nav');
  const navigate = useNavigate();
  const [form] = Form.useForm<FilterValues>();
  const [filters, setFilters] = useState<FilterValues>({});
  const path = useMemo(() => listPath(filters), [filters]);
  const list = useDetail(path, decodeTemplateList);
  const [step, setStep] = useState<{ kind: 'template' } | { kind: 'version'; plan: TermPlan } | null>(null);
  const canManage = hasButton(resources, commercialButtons.templateManage);
  const open = (versionId: string | null) => { if (versionId) void navigate(templateRoutes.version(versionId)); };
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_COMMERCIAL_TEMPLATES')}</h1>
      <div className="commercial-actions">
        <Button onClick={list.refresh} loading={list.loading}>{tCommon('action.refresh')}</Button>
        {canManage && <Button type="primary" onClick={() => setStep({ kind: 'template' })}>{t('templates.create')}</Button>}
      </div>
    </div>
    <Form form={form} name="commercial-template-filter" layout="vertical" onFinish={(values: FilterValues) => setFilters({ ...values })}>
      <div className="commercial-filters">
        <Form.Item name="entityNo" label="OE" normalize={(v: string) => v?.toUpperCase()} rules={[{ pattern: entityNoPattern, message: t('list.entityNoPattern') }]}>
          <Input allowClear maxLength={5} /></Form.Item>
        <Form.Item name="termType" label={t('shared.column.termType')}><Select allowClear options={templateTermTypes.map(value => ({ value,
          label: catalogTermTypeLabel(value) }))} /></Form.Item>
        <Form.Item name="status" label={t('templates.planStatus')}><Select allowClear options={['DRAFT', 'ACTIVE', 'RETIRED'].map(value => ({ value, label: value }))} /></Form.Item>
        <div className="commercial-filter-actions"><Button type="primary" htmlType="submit">{t('list.search')}</Button>
          <Button onClick={() => { form.resetFields(); setFilters({}); }}>{t('list.reset')}</Button></div>
      </div>
    </Form>
    {list.failure ? <QueryFailure failure={list.failure} retry={list.refresh} />
      : <Table<TemplatePlan> rowKey="planId" size="middle" loading={list.loading} dataSource={list.data ?? []} pagination={false} scroll={{ x: 1100 }}
        locale={{ emptyText: t('templates.empty') }} onRow={row => ({ className: 'clickable-row', onClick: () => open(row.latestVersionId ?? row.activeVersionId) })}
        columns={[
          { title: t('catalogDrawer.name'), render: (_, row) => <div className="commercial-cell"><span>{row.templateName}</span>
            <span className="commercial-cell-meta mono">{row.planCode}</span></div> },
          { title: 'OE', dataIndex: 'entityNo', width: 90 },
          { title: t('shared.column.termType'), width: 140, render: (_, row) => catalogTermTypeLabel(row.termType) },
          { title: t('templates.planStatus'), width: 100, render: (_, row) => <StatusTag status={row.status} /> },
          { title: t('templates.activeVersion'), width: 130, render: (_, row) => row.activeVersionId
            ? <Button type="link" size="small" onClick={event => { event.stopPropagation(); open(row.activeVersionId); }}>v{row.activeVersionNo}</Button> : '—' },
          { title: t('templates.latestVersion'), width: 200, render: (_, row) => row.latestVersionId ? <span className="commercial-actions">
            <Button type="link" size="small" onClick={event => { event.stopPropagation(); open(row.latestVersionId); }}>v{row.latestVersionNo}</Button>
            {row.latestVersionStatus && <StatusTag status={row.latestVersionStatus} kind="versionStatus" />}</span> : '—' },
        ]} />}
    <CommercialNote>{t('templates.note')}</CommercialNote>
    {step?.kind === 'template' && <CreateTemplateModal onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={plan => { list.refresh(); setStep({ kind: 'version', plan }); }} />}
    {step?.kind === 'version' && <CreateVersionModal template planId={step.plan.planId} termType={step.plan.termType}
      expectedChangeVersion={step.plan.changeVersion} onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={version => { setStep(null); void navigate(templateRoutes.version(version.versionId)); }} />}
  </div>;
}

/** 新建标准价目方案：编号（同条款类型内唯一）、名称、条款类型（Client 类）与 OE；创建后接着新建首个版本。 */
function CreateTemplateModal({ onClose, onCreated, onAuthorizationChanged }: {
  onClose: () => void; onCreated: (plan: TermPlan) => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('commercial');
  const [form] = Form.useForm<TemplateFormValues>();
  const command = useCommercialCommand(onAuthorizationChanged, 'version', form);
  const submit = async () => {
    const values = await form.validateFields();
    const plan = await command.run(templatePaths.templates, 'POST', templateBody(values), decodePlan, t('templates.createFailed'));
    if (plan) onCreated(plan);
  };
  return <CommandModal title={t('templates.create')} command={command} okText={t('shared.create')} onOk={() => void submit()} onClose={onClose} onReload={onClose}>
    <Form form={form} name="commercial-template-create" layout="vertical" initialValues={{ termType: 'CLIENT_PRICING' }}>
      <div className="commercial-form-grid">
        <Form.Item name="templateName" label={t('catalogDrawer.name')} rules={[{ required: true, whitespace: true, message: t('catalogDrawer.nameRequired') }]}>
          <Input maxLength={128} placeholder={t('templates.namePlaceholder')} /></Form.Item>
        <Form.Item name="planCode" label={t('templates.code')} extra={t('plan.create.planCodeHint')} rules={[{ required: true, whitespace: true, message: t('templates.codeRequired') },
          { pattern: /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/, message: t('form.codePattern') }]}><Input maxLength={64} placeholder="TPL-AE001-STD" /></Form.Item>
        <Form.Item name="termType" label={t('shared.column.termType')} rules={[{ required: true, message: t('form.termTypeRequired') }]}>
          <Select options={templateTermTypes.map(value => ({ value, label: catalogTermTypeLabel(value) }))} /></Form.Item>
        <Form.Item name="entityNo" label="OE" normalize={(v: string) => v?.toUpperCase()} rules={[{ required: true, whitespace: true, message: t('agreement.create.entityNoRequired') },
          { pattern: entityNoPattern, message: t('form.entityNoPattern') }]}><Input maxLength={5} placeholder="AE001" /></Form.Item>
      </div>
      <CommercialNote>{t('templates.createNote')}</CommercialNote>
    </Form>
  </CommandModal>;
}
