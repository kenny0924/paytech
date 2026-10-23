import { useRef, useState } from 'react';
import { Alert, Button, Checkbox, Form, Input, Select, Table, Tag } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import {
  codePattern, commercialPaths, componentSummary, currencyPattern, dimensionCodes, entityNoPattern, idPattern, joinList, localInput, partnerTermTypes,
  termTypes, utcInstant,
} from '../commercial/commercial';
import type { PriceComponent } from '../commercial/commercial';
import {
  cutoffTime, decodeSimulation, illustrationNotesText, simulationBody, simulationErrorMessage, simulationFieldName,
} from '../commercial/commercialPreview';
import type { ComponentIllustration, Simulation, SimulationFormValues } from '../commercial/commercialPreview';
import { CommercialNote } from '../commercial/CommercialParts';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const amountPattern = /^(0|[1-9][0-9]{0,14})(\.[0-9]{1,6})?$/;
const prefillKeys = ['termVersionId', 'agreementId', 'entityNo', 'clientId', 'currency', 'termType'] as const;

/**
 * 价格试算：输入条款版本、OE、币种、支付方式与维度事实，经 commercial 命中价格；填写示例金额时显示按命中价格计算的示例费用
 * <li>维度行留空表示事实未知；勾选「不适用」表示确认该维度不适用（提交 null）。命中失败显示 commercial 的错误码与原因。</li>
 * <li>支持 ?termVersionId=&agreementId=&entityNo=&clientId=&currency=&termType= 预填。</li>
 */
export function CommercialPriceSimulator({ onAuthorizationChanged }: Props) {
  const t = useT('commercial'), tNav = useT('nav');
  const [params] = useSearchParams();
  const [form] = Form.useForm<SimulationFormValues>();
  const notify = useErrorNotification();
  const [result, setResult] = useState<Simulation | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  const busy = useRef(false);
  const termType = Form.useWatch('termType', form) ?? 'CLIENT_PRICING';
  const partner = partnerTermTypes.includes(termType);
  const prefill = Object.fromEntries(prefillKeys.map(key => [key, params.get(key) ?? undefined]));
  const submit = async (values: SimulationFormValues) => {
    if (busy.current) return;
    busy.current = true; setRunning(true); setFailure(null);
    const body = simulationBody({ ...values, contextAsOfTime: utcInstant(values.contextAsOfTime) }, partner);
    try {
      const simulation = await session.request(commercialPaths.simulate, { method: 'POST', body: JSON.stringify(body),
        headers: { 'Content-Type': 'application/json' } }, decodeSimulation(body));
      setResult(simulation);
    } catch (error) {
      setResult(null);
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      if (error instanceof ApiError && error.fieldErrors.length) {
        form.setFields(error.fieldErrors.flatMap(item => {
          const name = simulationFieldName(item.field);
          return name ? [{ name, errors: [item.message] }] : [];
        }));
      }
      const message = simulationErrorMessage(error);
      if (message) setFailure(message); else notify(error, t('simulator.failed'));
    } finally { busy.current = false; setRunning(false); }
  };
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_COMMERCIAL_PRICE_SIMULATOR')}</h1></div>
    <Form form={form} name="commercial-price-simulation" layout="vertical" onFinish={values => void submit(values)}
      initialValues={{ termType: 'CLIENT_PRICING', currency: 'USD', paymentMethodCode: 'CARD', contextAsOfTime: localInput(new Date().toISOString()),
        dimensions: [], ...prefill, ownerId: prefill.clientId }}>
      <div className="commercial-sim-fields">
        <Form.Item name="termVersionId" label={t('simulator.field.termVersionId')} className="sim-id" rules={[{ required: true, pattern: idPattern, message: t('simulator.field.termVersionIdPattern') }]}><Input maxLength={19} /></Form.Item>
        <Form.Item name="agreementId" label={t('plan.field.agreementId')} className="sim-id" rules={[{ required: true, pattern: idPattern, message: t('simulator.field.agreementIdPattern') }]}><Input maxLength={19} /></Form.Item>
        <Form.Item name="termType" label={t('shared.column.termType')} className="sim-type"><Select options={termTypes.map(value => ({ value, label: `${enumLabel(t, 'enum.termType', value)} ${value}` }))} /></Form.Item>
        <Form.Item name="ownerId" label={partner ? 'Local Partner ID' : 'Client ID'} className="sim-id" rules={[{ required: true, pattern: idPattern, message: t('simulator.field.ownerIdPattern') }]}>
          <Input maxLength={19} /></Form.Item>
        <Form.Item name="entityNo" label="OE" className="sim-short" normalize={(v: string) => v?.toUpperCase()} rules={[{ required: true, pattern: entityNoPattern, message: t('list.entityNoPattern') }]}>
          <Input maxLength={5} placeholder="AE001" /></Form.Item>
        <Form.Item name="currency" label={t('matrix.column.currency')} className="sim-short" normalize={(v: string) => v?.toUpperCase()} rules={[{ required: true, pattern: currencyPattern, message: t('simulator.field.currencyPattern') }]}>
          <Input maxLength={3} /></Form.Item>
        <Form.Item name="contextAsOfTime" label={t('simulator.field.asOf')} className="sim-time" rules={[{ required: true, message: t('simulator.field.asOfRequired') }]}><Input type="datetime-local" step={1} /></Form.Item>
        <Form.Item name="paymentMethodCode" label={t('catalog.column.paymentMethod')} className="sim-code" normalize={(v: string) => v?.toUpperCase()} rules={[{ pattern: codePattern, message: t('simulator.field.paymentMethodPattern') }]}>
          <Input maxLength={64} placeholder={t('simulator.field.optional')} /></Form.Item>
        <Form.Item name="amount" label={t('simulator.field.amount')} className="sim-code" tooltip={t('simulator.field.amountTooltip')} rules={[{ pattern: amountPattern, message: t('simulator.field.amountPattern') }]}>
          <Input maxLength={22} placeholder={t('simulator.field.optional')} /></Form.Item>
      </div>
      <Form.List name="dimensions">{(fields, { add, remove }) => <div className="commercial-sim-dimensions">
        {fields.map(field => <div className="commercial-sim-dimension" key={field.key}>
          <Form.Item name={[field.name, 'dimensionCode']} rules={[{ required: true, message: t('simulator.dimension') }]}>
            <Select aria-label={t('simulator.dimension')} options={dimensionCodes.map(value => ({ value, label: value }))} /></Form.Item>
          <Form.Item name={[field.name, 'value']} normalize={(v: string) => v?.toUpperCase()}><Input placeholder={t('simulator.dimensionValuePlaceholder')} /></Form.Item>
          <Form.Item name={[field.name, 'notApplicable']} valuePropName="checked"><Checkbox>{t('simulator.notApplicable')}</Checkbox></Form.Item>
          <Button type="link" danger onClick={() => remove(field.name)}>{t('componentTable.band.remove')}</Button>
        </div>)}
        <div className="commercial-sim-actions">
          <Button type="dashed" onClick={() => add({ dimensionCode: 'CARD_BRAND' })}>{t('simulator.addDimension')}</Button>
          <Button type="primary" htmlType="submit" loading={running}>{t('simulator.run')}</Button>
        </div>
      </div>}</Form.List>
    </Form>
    {failure && <Alert type="warning" showIcon className="commercial-banner" style={{ marginTop: 16 }} title={failure} />}
    {result && <SimulationResult result={result} />}
    <CommercialNote>{t('simulator.note')}</CommercialNote>
  </div>;
}

interface ComponentRow { key: string; feeEventCode: string; component: PriceComponent; illustration: ComponentIllustration | null; total: string | null }

function SimulationResult({ result }: { result: Simulation }) {
  const t = useT('commercial');
  const { price, illustration } = result;
  const illustrated = new Map((illustration?.feeEvents ?? []).flatMap(event => event.components.map(c => [`${event.feeEventCode}|${c.componentNo}`, c] as const)));
  const totals = new Map((illustration?.feeEvents ?? []).map(event => [event.feeEventCode, event.totalAmount]));
  const rows: ComponentRow[] = price.components.toSorted((a, b) => a.triggerCode.localeCompare(b.triggerCode) || a.componentNo - b.componentNo)
    .map(component => ({ key: `${component.triggerCode}|${component.componentNo}`, feeEventCode: component.triggerCode, component,
      illustration: illustrated.get(`${component.triggerCode}|${component.componentNo}`) ?? null, total: totals.get(component.triggerCode) ?? null }));
  return <section className="commercial-block" style={{ marginTop: 16 }}>
    <div className="commercial-block-head"><h3>{t('simulator.result.matchedProfile')} <span className="mono">{price.pricingProfileCode}</span></h3>
      <Tag>{price.entityNo}</Tag><Tag>{price.currency}</Tag><span className="commercial-cell-meta">{t('matrix.priority', { priority: price.priorityNo })}</span>
      {price.paymentMethodCode && <span className="commercial-cell-meta">{t('simulator.result.paymentMethod', { code: price.paymentMethodCode })}</span>}
      <span className="commercial-cell-meta mono">{t('simulator.result.price', { id: price.priceId })}</span>
    </div>
    <div className="commercial-limits">{price.conditions.length ? price.conditions.map(c => <Tag key={`${c.dimensionCode}-${c.conditionValue}`}>{c.dimensionCode}={c.conditionValue}</Tag>)
      : <span className="commercial-cell-meta">{t('simulator.result.noConditions')}</span>}</div>
    <Table<ComponentRow> size="small" rowKey="key" pagination={false} dataSource={rows} scroll={{ x: 900 }} style={{ marginTop: 12 }} columns={[
      { title: t('componentTable.column.event'), width: 170, render: (_, row) => <span className="mono">{row.feeEventCode}</span> },
      { title: t('compare.column.component'), render: (_, row) => <div className="commercial-cell"><span>{row.component.componentNo}. {row.component.componentCode}</span>
        <span className="commercial-cell-meta">{t('simulator.result.componentMeta', { base: row.component.calculationBase, refund: row.component.refundBehavior })}</span></div> },
      { title: t('simulator.result.rateAmount'), render: (_, row) => <div className="commercial-cell"><span>{componentSummary(row.component)}</span>
        {row.component.bands.length > 0 && <span className="commercial-cell-meta">{joinList(row.component.bands.map(band =>
          `[${band.lowerBound}, ${band.upperBound ?? '∞'}) ${band.rate ?? band.fixedAmount ?? ''}`), 'clauses')}</span>}</div> },
      ...(illustration ? [
        { title: t('simulator.result.exampleFee', { amount: illustration.amount, currency: illustration.currency }), width: 220, render: (_: unknown, row: ComponentRow) => row.illustration?.computed
          ? <div className="commercial-cell"><span>{row.illustration.amount}</span>{row.illustration.notes.length > 0 && <span className="commercial-cell-meta">{illustrationNotesText(row.illustration.notes)}</span>}</div>
          : <span className="commercial-cell-meta">{row.illustration?.notes.length ? illustrationNotesText(row.illustration.notes) : t('simulator.result.notCalculated')}</span> },
        { title: t('simulator.result.eventTotal'), width: 120, render: (_: unknown, row: ComponentRow) => <span className="commercial-fee-total">{row.total ?? '—'}</span> },
      ] : []),
    ]} />
    {price.settlement && <div className="commercial-cell-meta" style={{ marginTop: 8 }}>{t('simulator.result.settlement', { days: price.settlement.settlementBusinessDays,
      calendar: price.settlement.businessCalendarCode, timezone: price.settlement.settlementTimezone, cutoff: cutoffTime(price.settlement.cutoffSecondOfDay) })}</div>}
  </section>;
}
