/**
 * 仅测试用的浏览器协议模拟器（loopback）：Client 门户入驻 v2 页面预览。
 * - 采集 Schema 由仓库内 client-master 初始化 DML（AE / SA / EG 国家包）与 compliance 程序 DML 现场解析得到，不手写国家规则。
 * - 无数据库、无真实凭据、不连接任何 Owner；文件直传不可用（上传返回 503，用于展示失败态）。
 * - 用法：先 `npm --prefix client-console-web run build`，再 `node <本文件> [--port=18399]`；登录 demo@example.test / Preview-only-2026!
 */
import http from 'node:http';
import {randomBytes} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const repo = fileURLToPath(new URL('../../../../', import.meta.url));
const dist = path.join(repo, 'client-console-web/dist/');
const now = () => new Date().toISOString();

/** 解析 `INSERT INTO t (cols) VALUES (...),(...);` 为对象行；@变量与函数按原文保留。 */
function parseInserts(sql) {
  const tables = {};
  const re = /INSERT INTO `?(\w+)`?\s*\(([^)]*)\)\s*VALUES\s*/g;
  let match;
  while ((match = re.exec(sql))) {
    const table = match[1], cols = match[2].split(',').map((c) => c.trim().replace(/`/g, ''));
    let i = re.lastIndex;
    const rows = tables[table] ??= [];
    while (sql[i] === '(' || /\s|,/.test(sql[i])) {
      if (sql[i] !== '(') { i++; continue; }
      i++;
      const values = []; let token = '', quoted = false, depth = 0, wasQuoted = false;
      for (; i < sql.length; i++) {
        const c = sql[i];
        if (quoted) { if (c === "'" && sql[i + 1] === "'") { token += "'"; i++; } else if (c === "'") quoted = false; else token += c; continue; }
        if (c === "'") { quoted = true; wasQuoted = true; token = ''; continue; }
        if (c === '(') { depth++; token += c; continue; }
        if (c === ')' && depth > 0) { depth--; token += c; continue; }
        if (c === ',' || c === ')') {
          const raw = token.trim();
          values.push(wasQuoted ? token : raw === 'NULL' ? null : /^-?\d+$/.test(raw) ? (raw.length > 15 ? raw : Number(raw)) : raw);
          token = ''; wasQuoted = false;
          if (c === ')') { i++; break; }
          continue;
        }
        token += c;
      }
      rows.push(Object.fromEntries(cols.map((col, index) => [col, values[index]])));
      while (i < sql.length && /\s/.test(sql[i])) i++;
      if (sql[i] === ';') break;
    }
    re.lastIndex = i;
  }
  return tables;
}
const cm = parseInserts(readFileSync(path.join(repo, 'paytech/client-master/docs/database/sql/client-master-initial-data.sql'), 'utf8'));
const cp = parseInserts(readFileSync(path.join(repo, 'paytech/compliance/docs/database/sql/compliance-initial-data.sql'), 'utf8'));
const rule = (scope, code, ruleCode) => cm.t_rule_setting.find((r) => r.scope_type === scope && r.scope_code === code && r.rule_code === ruleCode)?.rule_value;

function programMaterials(jurisdiction, partyType, roleType, subjectRole) {
  const program = cp.t_dd_program.find((p) => p.jurisdiction_code === jurisdiction && p.party_type === partyType && p.role_type === roleType);
  if (!program) return [];
  const version = cp.t_dd_program_version.find((v) => String(v.program_id) === String(program.program_id));
  return cp.t_dd_program_requirement.filter((r) => String(r.program_version_id) === String(version.program_version_id)).map((r) => ({
    requirementCode: r.requirement_code, subjectRole, required: Number(r.required_flag) === 1, maxAgeDays: r.max_age_days ?? null,
    documentTypes: cp.t_dd_program_requirement_doc_type.filter((d) => String(d.program_requirement_id) === String(r.program_requirement_id)).map((d) => d.document_type),
  }));
}

function formSchema(jurisdiction, partyType, organizationType) {
  const j = cm.t_jurisdiction.find((row) => row.jurisdiction_code === jurisdiction);
  if (!j) return null;
  const schema = cm.t_onboarding_schema.find((s) => s.jurisdiction_code === jurisdiction && s.party_type === partyType && s.organization_type_code === organizationType)
    ?? cm.t_onboarding_schema.find((s) => s.jurisdiction_code === jurisdiction && s.party_type === partyType && s.organization_type_code === '*');
  const fields = cm.t_onboarding_schema_field.filter((f) => String(f.schema_id) === String(schema.schema_id)).sort((a, b) => a.display_order - b.display_order);
  const sections = [];
  for (const f of fields) {
    let section = sections.find((s) => s.section === f.section);
    if (!section) sections.push(section = { section: f.section, labelKey: 'onboardingSchema.section.' + f.section, groups: [] });
    let group = section.groups.find((g) => g.group === f.display_group);
    if (!group) section.groups.push(group = { group: f.display_group, labelKey: 'onboardingSchema.group.' + f.display_group, fields: [] });
    group.fields.push({ fieldKey: f.field_key, widget: f.widget_code, catalogCode: f.catalog_code, requirementLevel: f.requirement_level,
      requirementGroup: f.requirement_group, appliesTo: f.applies_to_subject, visibleWhen: f.visible_when, labelKey: f.label_key, helpKey: f.help_key,
      order: f.display_order, validation: { pattern: f.validation_pattern, minLength: f.min_length, maxLength: f.max_length } });
  }
  const catalogs = {};
  for (const item of cm.t_catalog_item.filter((c) => !c.jurisdiction_code || c.jurisdiction_code === jurisdiction).sort((a, b) => a.display_order - b.display_order))
    (catalogs[item.catalog_code] ??= []).push({ itemCode: item.item_code, labelKey: item.label_key });
  const individualTypes = new Set(cm.t_rule_setting.filter((r) => r.rule_code === 'PARTY_TYPE' && r.rule_value === 'INDIVIDUAL').map((r) => r.scope_code));
  const scope = jurisdiction + ':' + organizationType;
  return {
    schemaId: String(schema.schema_id), versionNo: schema.version_no, sections, catalogs,
    organizationTypes: cm.t_organization_type.filter((o) => o.jurisdiction_code === jurisdiction && (partyType === 'INDIVIDUAL') === individualTypes.has(jurisdiction + ':' + o.type_code))
      .map((o) => ({ code: o.type_code, labelKey: o.label_key })),
    identifierTypes: cm.t_identifier_type.filter((t) => !t.jurisdiction_code || t.jurisdiction_code === jurisdiction).map((t) => ({
      code: t.identifier_type_code, partyType: t.party_type, labelKey: t.label_key, pattern: t.format_regex, checksumRule: t.checksum_rule,
      issuerScopeMode: t.issuer_scope_mode, expiryRequired: rule('IDENTIFIER_TYPE', t.identifier_type_code, 'EXPIRY_REQUIRED') === '1' })),
    jurisdictionCode: jurisdiction,
    addressFormat: { code: j.address_format_code, components: cm.t_address_format_component.filter((c) => c.address_format_code === j.address_format_code)
      .map((c) => ({ component: c.component, required: Number(c.required_flag) === 1, labelKey: c.label_key, order: c.display_order, catalogCode: c.catalog_code })) },
    fallbackAddressFormat: { code: 'GENERIC', components: cm.t_address_format_component.filter((c) => c.address_format_code === 'GENERIC')
      .map((c) => ({ component: c.component, required: Number(c.required_flag) === 1, labelKey: c.label_key, order: c.display_order, catalogCode: c.catalog_code })) },
    relationRules: { uboThresholdPct: Number(rule('JURISDICTION', jurisdiction, 'UBO_THRESHOLD_PCT') ?? 25),
      minSignatories: Number(rule('ORGANIZATION_TYPE', scope, 'MIN_SIGNATORIES') ?? 0) || null,
      requiresDirectors: rule('ORGANIZATION_TYPE', scope, 'REQUIRES_DIRECTORS') === '1', uboCollectionMode: rule('ORGANIZATION_TYPE', scope, 'UBO_COLLECTION_MODE') ?? null },
    relationTypes: cm.t_relation_type.map((r) => ({ code: r.relation_type_code, labelKey: r.label_key, uboFlag: Number(r.ubo_flag) === 1, ownershipPctRequired: Number(r.ownership_pct_required) === 1 })),
    materials: partyType === 'ORGANIZATION'
      ? [...programMaterials(jurisdiction, partyType, 'CLIENT', 'PRIMARY'), ...programMaterials(jurisdiction, 'INDIVIDUAL', 'RELATED_PERSON', 'RELATED_PERSON')]
      : programMaterials(jurisdiction, partyType, 'CLIENT', 'PRIMARY'),
  };
}

// ---- 内存状态 ----
const context = { accountId: '1', sessionId: '2', emailMasked: 'f***@alnoor.ae', emailVerified: true, contextType: 'ACCOUNT', tenantId: null, memberId: null, allowedActions: ['ONBOARDING', 'LOGOUT'] };
const sessions = new Map();
let next = 500;
const applications = new Map();
function create(type, jurisdiction, organizationType, extra = {}) {
  const id = String(next++);
  const app = { applicationId: id, applicationNo: 'ONB-2026-' + id, clientId: String(next++), subjectPartyId: String(next++), clientType: type, status: 'DRAFT',
    savedAt: now(), editable: true, jurisdictionCode: jurisdiction, organizationTypeCode: organizationType, responsiblePartnerName: 'Gulf Pay',
    values: {}, people: [], relations: [], documents: [], declarations: [], submittedAt: null, withdrawnAt: null, review: null, ...extra };
  applications.set(id, app);
  return app;
}
const fileFor = (name, size, type = 'application/pdf') => { const fileId = String(next++); return { fileId, fileAddress: '/files/v1/' + fileId,
  displayName: name, sizeBytes: String(size), mimeType: type, scanStatus: 'AVAILABLE', objectStatus: 'ACTIVE', allowedActions: ['DOWNLOAD'] }; };
function attach(app, subjectPartyId, requirementCode, documentType, file) {
  app.documents.push({ documentId: String(next++), subjectPartyId, requirementCode, documentType, fileId: file.fileId, fileAddress: file.fileAddress,
    subjectInScope: true, fileStatus: 'AVAILABLE', file });
}
// 主场景：阿联酋自贸区 LLC，部分填写
const main = create('ORGANIZATION', 'AE', 'FZ_LLC', { values: {
  organization: { legalName: 'Al Noor Trading FZ-LLC', organizationType: 'FZ_LLC', incorporationCountry: 'AE', incorporationDate: '2021-03-02', licenseExpiryDate: '2027-05-09', registrationAuthorityCode: 'AE_DMCC' },
  identifier: { identifierType: 'AE_TRADE_LICENSE', identifierMasked: 'DMCC-123456' },
  address: { addressFormatCode: 'AE_POBOX', addressLine1: 'Unit 2504, Cluster X, JLT', city: 'Dubai', poBox: '340512', countryCode: 'AE' },
  contact: { email: 'ops@alnoor.ae', phone: '+97145550210' } } });
main.people.push({ partyId: '901', editable: true, values: { individual: { givenName: 'Fatima', familyName: 'Al Mansoori', nationalityCountry: 'AE', residenceCountry: 'AE' } } },
  { partyId: '902', editable: true, values: { individual: { givenName: 'Rahul', familyName: 'Mehta', nationalityCountry: 'IN', residenceCountry: 'AE' } } });
main.relations.push({ relationId: '911', relatedPartyId: '901', relationType: 'UBO', ownershipPercentage: '55', status: 'DRAFT', editable: true },
  { relationId: '912', relatedPartyId: '901', relationType: 'DIRECTOR', ownershipPercentage: null, status: 'DRAFT', editable: true },
  { relationId: '913', relatedPartyId: '901', relationType: 'SIGNATORY', ownershipPercentage: null, status: 'DRAFT', editable: true },
  { relationId: '914', relatedPartyId: '902', relationType: 'UBO', ownershipPercentage: '45', status: 'DRAFT', editable: true });
attach(main, main.subjectPartyId, 'REGISTRATION', 'TRADE_LICENSE', fileFor('trade-license-dmcc.pdf', 2516582));
attach(main, '901', 'IDENTITY', 'PASSPORT', fileFor('passport-fatima.jpg', 1843200, 'image/jpeg'));
// 其他状态的申请（结果页）
const returned = create('ORGANIZATION', 'SA', 'LLC', { status: 'RETURNED', submittedAt: '2026-10-24T16:38:00Z',
  values: { organization: { legalName: 'Noor Trading Co. LLC', organizationType: 'LLC', incorporationCountry: 'SA', incorporationDate: '2021-03-02' },
    identifier: { identifierType: 'SA_CR_NUMBER', identifierMasked: '7012345678' }, address: { addressFormatCode: 'SA_NATIONAL_ADDRESS', buildingNumber: '2929',
      addressLine1: 'Rayhanah Bint Zaid', district: 'Al Arid', city: 'RIYADH', postalCode: '13337', additionalNumber: '8118', countryCode: 'SA' } } });
returned.review = { caseStatus: 'RETURNED', outcome: 'RETURNED', publicNote: null, decidedAt: '2026-10-27T10:12:00Z', dueTime: '2026-11-10T23:59:00Z', errors: [
  { requirementKey: 'document.BANK', subjectPartyId: returned.subjectPartyId, code: 'DOCUMENT_TOO_OLD', remediation: 'UPLOAD_DOCUMENT', reason: 'The bank letter is dated 2026-03-02. Upload one issued within the last 6 months.' },
  { requirementKey: 'organization.legalName', subjectPartyId: returned.subjectPartyId, code: 'FIELD_FORMAT_INVALID', remediation: 'PROVIDE_FIELD', reason: 'The legal name must match the commercial registration exactly: Noor Trading Company LLC.' }] };
for (const [status, review] of [['UNDER_REVIEW', null], ['APPROVED', { caseStatus: 'APPROVED', outcome: 'APPROVED', decidedAt: '2026-10-29T14:05:00Z', errors: [] }],
  ['REJECTED', { caseStatus: 'REJECTED', outcome: 'REJECTED', publicNote: 'The business activity is outside the supported scope.', decidedAt: '2026-10-29T10:12:00Z', errors: [] }],
  ['WITHDRAWN', { caseStatus: 'EXPIRED', outcome: 'EXPIRED', dueTime: '2026-11-10T23:59:00Z', decidedAt: '2026-11-11T00:00:00Z', errors: [{ requirementKey: 'document.BANK', code: 'DOCUMENT_TOO_OLD', subjectPartyId: null }] }],
  ['WITHDRAWN', null]]) {
  const app = create('ORGANIZATION', 'EG', 'LLC', { status, editable: false, submittedAt: '2026-10-27T09:42:00Z', withdrawnAt: status === 'WITHDRAWN' ? '2026-10-28T08:30:00Z' : null, review,
    values: { organization: { legalName: 'Nile Commerce LLC' } } });
  void app;
}

function visible(field, flat) {
  if (!field.visibleWhen) return true;
  return field.visibleWhen.split(',').every((clause) => { const [k, v] = clause.split('='); return v.split('|').includes(flat[k] ?? ''); });
}
function requirements(app) {
  if (!['DRAFT', 'RETURNED'].includes(app.status)) return [];
  const schema = formSchema(app.jurisdictionCode, app.clientType, app.organizationTypeCode), items = [];
  const flat = Object.fromEntries(Object.entries(app.values).flatMap(([g, f]) => Object.entries(f).map(([k, v]) => [g + '.' + k, v])));
  const item = (kind, key, code, remediation, subject = app.subjectPartyId) => items.push({ requirementKind: kind, requirementKey: key, subjectPartyId: subject,
    status: 'CURRENTLY_DUE', awaitingActionFrom: 'CLIENT', errorCode: code, remediationCode: remediation, dueTime: null });
  for (const section of schema.sections) for (const group of section.groups) for (const field of group.fields) {
    if (field.appliesTo === 'RELATED_PERSON' || field.requirementLevel !== 'REQUIRED' || !visible(field, flat)) continue;
    if (field.widget === 'ADDRESS') { for (const c of schema.addressFormat.components) if (c.required && !flat['address.' + c.component]) item('FIELD', 'address.' + c.component, 'FIELD_MISSING', 'PROVIDE_FIELD'); }
    else if (field.widget === 'IDENTIFIER') { if (!flat['identifier.identifierMasked'] && !flat['identifier.identifierValue']) item('IDENTIFIER', field.fieldKey, 'FIELD_MISSING', 'PROVIDE_FIELD'); }
    else if (field.widget === 'PERSON_LIST') { if (!app.people.length) item('PERSON', 'people', 'RELATED_PERSON_INCOMPLETE', 'ADD_PERSON'); }
    else if (field.widget === 'DOCUMENT_SLOT') continue;
    else if (field.widget === 'DECLARATION') { const type = field.fieldKey.split('.')[1]; const last = app.declarations.filter((d) => d.declarationType === type).pop();
      if (last?.declaredValue !== 'TRUE') item('DECLARATION', field.fieldKey, 'DECLARATION_MISSING', 'SIGN_DECLARATION'); }
    else if (!flat[field.fieldKey]) item('FIELD', field.fieldKey, 'FIELD_MISSING', 'PROVIDE_FIELD');
  }
  for (const slot of schema.materials.filter((s) => s.required)) {
    const subjects = slot.subjectRole === 'PRIMARY' ? [app.subjectPartyId] : app.people.map((p) => p.partyId);
    for (const subject of subjects) if (!app.documents.some((d) => d.subjectPartyId === subject && d.requirementCode === slot.requirementCode))
      item('DOCUMENT', 'document.' + slot.requirementCode, 'DOCUMENT_MISSING', 'UPLOAD_DOCUMENT', subject);
  }
  return items;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1'), route = url.pathname;
  let bytes = ''; for await (const chunk of req) bytes += chunk;
  const reply = (body, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(status === 204 ? '' : JSON.stringify(body)); };
  const problem = (status, code, extra = {}) => reply({ type: 'about:blank', title: 'Error', detail: 'Preview fixture: ' + code, code, requestId: 'preview', timestamp: now(), ...extra }, status);
  const json = bytes ? JSON.parse(bytes) : null;
  const cookie = (req.headers.cookie ?? '').split(';').map((v) => v.trim()).find((v) => v.startsWith('preview-session='))?.slice(16);
  const bearer = req.headers.authorization?.slice(7);
  const grant = (s) => ({ context: { ...context, sessionId: s.id }, accessToken: s.access, expiresAt: new Date(Date.now() + 3600_000).toISOString() });
  if (route === '/api/client/auth/v1/login') {
    if (json?.email !== 'demo@example.test' || json?.password !== 'Preview-only-2026!') return problem(401, 'CLIENT_AUTHENTICATION_FAILED');
    const s = { id: String(next++), refresh: randomBytes(16).toString('hex'), access: randomBytes(16).toString('hex') }; sessions.set(s.refresh, s);
    res.setHeader('Set-Cookie', 'preview-session=' + s.refresh + '; HttpOnly; SameSite=Strict; Path=/api/client/auth/v1'); return reply(grant(s));
  }
  if (route === '/api/client/auth/v1/refresh') { const s = sessions.get(cookie); return s ? reply(grant(s)) : problem(401, 'CLIENT_SESSION_INVALID'); }
  if (route === '/api/client/auth/v1/context') return [...sessions.values()].some((s) => s.access === bearer) ? reply(context) : problem(401, 'CLIENT_SESSION_INVALID');
  if (route === '/api/client/auth/v1/logout') { sessions.delete(cookie); return reply(null, 204); }
  if (route.startsWith('/api/client/auth/v1/')) return reply({ resendAfterSeconds: 60 });
  if (route === '/api/client/i18n/v1/manifest') return problem(503, 'SERVICE_UNAVAILABLE');
  if (route.startsWith('/api/client/onboarding') && ![...sessions.values()].some((s) => s.access === bearer)) return problem(401, 'CLIENT_SESSION_INVALID');
  if (route === '/api/client/onboarding/v1/start') return reply({ workspaces: { status: 'AVAILABLE', items: [] }, hasMore: false, activeApplicationId: main.applicationId,
    applications: [...applications.values()].map((a) => ({ applicationId: a.applicationId, applicationNo: a.applicationNo, clientType: a.clientType, status: a.status, active: a.editable })) });
  if (route === '/api/client/onboarding/v1/jurisdictions') return reply(cm.t_jurisdiction.map((j) => ({ jurisdictionCode: j.jurisdiction_code, countryCode: j.country_code,
    labelKey: j.label_key, acceptancePolicy: rule('JURISDICTION', j.jurisdiction_code, 'ACCEPTANCE_POLICY') ?? 'OPEN' })));
  if (route === '/api/client/onboarding/v1/form-schema') {
    const value = formSchema(url.searchParams.get('jurisdiction'), url.searchParams.get('partyType'), url.searchParams.get('organizationType'));
    return value ? reply(value) : problem(422, 'JURISDICTION_NOT_ACCEPTED');
  }
  if (route === '/api/client/onboarding/v1/applications' && req.method === 'POST') {
    if (json.onboardingCode && json.onboardingCode !== 'GP7K-2Q9Z') return problem(422, 'ONBOARDING_CODE_INVALID');
    const app = create(json.clientType, json.jurisdictionCode, json.organizationTypeCode, { responsiblePartnerName: json.onboardingCode ? 'Gulf Pay' : null });
    if (json.organizationTypeCode) app.values.organization = { organizationType: json.organizationTypeCode };
    return reply({ applicationId: app.applicationId, applicationNo: app.applicationNo }, 201);
  }
  if (route.startsWith('/api/client/onboarding-files/')) return problem(503, 'SERVICE_UNAVAILABLE');
  const parts = route.split('/'), app = applications.get(parts[6]);
  if (route.startsWith('/api/client/onboarding/v1/applications/') && app) {
    if (req.method === 'GET' && parts.length === 7) return reply({ ...app, requirementsStatus: 'AVAILABLE', requirements: requirements(app), reviewStatus: app.review ? 'AVAILABLE' : 'NONE' });
    if (parts[7] === 'requirements') return reply({ items: requirements(app) });
    if (parts[7] === 'submission-check') { const errors = requirements(app).map((i) => ({ requirementKey: i.requirementKey, subjectPartyId: i.subjectPartyId, code: i.errorCode, remediation: i.remediationCode, message: 'x' }));
      return reply({ ready: errors.length === 0, errors }); }
    if (parts[7] === 'submissions') { app.status = 'UNDER_REVIEW'; app.editable = false; app.submittedAt = now(); return reply({ applicationId: app.applicationId, submissionId: '1', status: app.status }, 202); }
    if (parts[7] === 'withdrawal') { app.status = 'WITHDRAWN'; app.editable = false; app.withdrawnAt = now(); return reply({ applicationId: app.applicationId }); }
    if (parts[7] === 'draft') { for (const [group, values] of Object.entries(json.values)) for (const [k, v] of Object.entries(values)) {
      if (group === 'identifier' && k === 'identifierValue') { (app.values.identifier ??= {}).identifierMasked = v ? '•'.repeat(Math.max(0, v.length - 4)) + v.slice(-4) : null; continue; }
      (app.values[group] ??= {})[k] = v; }
      app.savedAt = now(); return reply({ applicationId: app.applicationId }); }
    if (parts[7] === 'declarations') { app.declarations.push({ ...json, declaredAt: now() }); return reply({ applicationId: app.applicationId }, 201); }
    if (parts[7] === 'people') {
      if (req.method === 'POST') { const partyId = String(next++); app.people.push({ partyId, editable: true, values: json.person.values });
        app.relations.push({ relationId: String(next++), relatedPartyId: partyId, ...json.relation, status: 'DRAFT', editable: true }); return reply({ partyId }, 201); }
      const person = app.people.find((p) => p.partyId === parts[8]); for (const [g, v] of Object.entries(json.values)) Object.assign(person.values[g] ??= {}, v); return reply({ partyId: person.partyId });
    }
    if (parts[7] === 'relations') {
      if (req.method === 'POST') { app.relations.push({ relationId: String(next++), ...json, status: 'DRAFT', editable: true }); return reply({}, 201); }
      const relation = app.relations.find((r) => r.relationId === parts[8]);
      if (req.method === 'DELETE') { app.relations = app.relations.filter((r) => r !== relation); return reply(null, 204); }
      Object.assign(relation, json.values); return reply({});
    }
    if (parts[7] === 'documents' && req.method === 'DELETE') { app.documents = app.documents.filter((d) => d.documentId !== parts[8]); return reply(null, 204); }
  }
  if (route.startsWith('/api/')) return problem(503, 'SERVICE_UNAVAILABLE');
  try {
    const target = path.join(dist, route.startsWith('/assets/') ? route : 'index.html');
    if (!target.startsWith(dist)) throw Error();
    const data = await readFile(target);
    const type = target.endsWith('.js') ? 'text/javascript' : target.endsWith('.css') ? 'text/css' : target.endsWith('.woff2') ? 'font/woff2' : target.endsWith('.woff') ? 'font/woff' : 'text/html';
    res.writeHead(200, { 'Content-Type': type, 'Cache-Control': 'no-store' }); res.end(data);
  } catch { res.writeHead(404).end(); }
});
const port = Number((process.argv.find((a) => a.startsWith('--port=')) ?? '--port=0').slice(7));
server.listen(port, '127.0.0.1', () => console.log(JSON.stringify({ url: 'http://127.0.0.1:' + server.address().port + '/login', fixtureOnly: true,
  applications: [...applications.values()].map((a) => a.applicationId + ':' + a.status) })));
