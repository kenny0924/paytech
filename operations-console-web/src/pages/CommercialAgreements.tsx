import { useState } from 'react';
import { Button, Form, Input } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { AgreementDetail } from '../commercial/AgreementDetail';
import { CreateAgreementModal } from '../commercial/AgreementModals';
import { commercialButtons, commercialRoutes, hasButton, idPattern, isId } from '../commercial/commercial';
import { CommercialNote } from '../commercial/CommercialParts';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/** 合同：/commercial/agreements 按 ID 打开与新建，/commercial/agreements/:agreementId 详情。 */
export function CommercialAgreements(props: Props) {
  const location = useLocation();
  const parts = location.pathname.replace(/\/$/, '').split('/').slice(3);
  if (parts.length > 1 || (parts.length === 1 && !isId(parts[0]))) return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  return parts.length === 1 ? <AgreementDetail key={parts[0]} agreementId={parts[0]} {...props} /> : <AgreementHome {...props} />;
}

function AgreementHome({ resources, onAuthorizationChanged }: Props) {
  const navigate = useNavigate();
  const [creating, setCreating] = useState(false);
  const canManage = hasButton(resources, commercialButtons.agreementManage);
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>合同</h1>{canManage && <Button type="primary" onClick={() => setCreating(true)}>新建合同</Button>}</div>
    <Form name="commercial-agreement-open" layout="vertical" onFinish={(values: { agreementId: string }) => void navigate(commercialRoutes.agreement(values.agreementId.trim()))}>
      <div className="commercial-open">
        <Form.Item name="agreementId" label="合同 ID" rules={[{ required: true, whitespace: true, message: '请输入合同 ID' }, { pattern: idPattern, message: '合同 ID 是正十进制整数' }]}>
          <Input allowClear maxLength={19} placeholder="例如 7319000000000000101" /></Form.Item>
        <Button type="primary" htmlType="submit">打开</Button>
      </div>
    </Form>
    <CommercialNote>commercial 本期没有合同列表与按编号查询接口：请按合同 ID 打开。新建合同成功后直接进入详情；详情内维护签署、订阅、开通项与条款方案。</CommercialNote>
    {creating && <CreateAgreementModal onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={agreementId => { setCreating(false); void navigate(commercialRoutes.agreement(agreementId)); }} />}
  </div>;
}
