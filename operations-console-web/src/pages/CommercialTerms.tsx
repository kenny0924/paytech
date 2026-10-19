import { useState } from 'react';
import { Button, Form, Input } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { CreatePlanModal } from '../commercial/AgreementModals';
import { commercialButtons, commercialRoutes, hasButton, idPattern, isId } from '../commercial/commercial';
import type { TermPlan } from '../commercial/commercial';
import { CommercialNote } from '../commercial/CommercialParts';
import { CreateVersionModal } from '../commercial/TermModals';
import { TermVersionEditor } from '../commercial/TermVersionEditor';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/** 价目：/commercial/terms 按版本 ID 打开与新建方案，/commercial/terms/versions/:versionId 版本编辑器。 */
export function CommercialTerms(props: Props) {
  const location = useLocation();
  const parts = location.pathname.replace(/\/$/, '').split('/').slice(3);
  if (parts.length === 0) return <TermsHome {...props} />;
  if (parts.length === 2 && parts[0] === 'versions' && isId(parts[1])) {
    return <TermVersionEditor key={parts[1]} versionId={parts[1]} {...props} />;
  }
  return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
}

function TermsHome({ resources, onAuthorizationChanged }: Props) {
  const navigate = useNavigate();
  const [step, setStep] = useState<{ kind: 'plan' } | { kind: 'version'; plan: TermPlan } | null>(null);
  const canManage = hasButton(resources, commercialButtons.termManage);
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>价目</h1>{canManage && <Button type="primary" onClick={() => setStep({ kind: 'plan' })}>新建条款方案</Button>}</div>
    <Form name="commercial-version-open" layout="vertical" onFinish={(values: { versionId: string }) => void navigate(commercialRoutes.version(values.versionId.trim()))}>
      <div className="commercial-open">
        <Form.Item name="versionId" label="条款版本 ID" rules={[{ required: true, whitespace: true, message: '请输入版本 ID' }, { pattern: idPattern, message: '版本 ID 是正十进制整数' }]}>
          <Input allowClear maxLength={19} placeholder="例如 7319000000000000601" /></Form.Item>
        <Button type="primary" htmlType="submit">打开</Button>
      </div>
    </Form>
    <CommercialNote>commercial 本期没有方案与版本列表接口：从合同详情的「条款版本」进入当前 / 已排期版本，或按版本 ID 打开。新建方案后会立即新建第一个草稿版本并进入编辑器。</CommercialNote>
    {step?.kind === 'plan' && <CreatePlanModal onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={plan => setStep({ kind: 'version', plan })} />}
    {step?.kind === 'version' && <CreateVersionModal planId={step.plan.planId} termType={step.plan.termType} expectedChangeVersion={step.plan.changeVersion}
      agreementId={step.plan.agreementId} onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={version => { setStep(null); void navigate(commercialRoutes.version(version.versionId)); }} />}
  </div>;
}
