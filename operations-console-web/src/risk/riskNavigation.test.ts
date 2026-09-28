import assert from 'node:assert/strict';
import test from 'node:test';
import { parseRiskNavigation, riskNavigationSearch, ruleSetCodeError, withRiskView } from './riskNavigation.ts';

test('view navigation preserves exact filters and paging while carrying only public coordinates', () => {
  const list = parseRiskNavigation('?pointCode=CHECKOUT_PREPARE&ruleSetCode=BASE&status=DRAFT&page=2&size=20');
  const detail = withRiskView(list, 'detail', 'CHECKOUT_BASE');
  const edit = withRiskView(detail, 'edit', 'CHECKOUT_BASE');
  assert.deepEqual(parseRiskNavigation(riskNavigationSearch(edit)), edit);
  assert.deepEqual(withRiskView(edit, 'list'), list);
  assert.equal(edit.filters.ruleSetCode, 'BASE');
  assert.equal(edit.selectedRuleSetCode, 'CHECKOUT_BASE');
  assert.deepEqual(edit.paging, { page: 2, size: 20 });
});

test('legacy Candidate and malformed detail addresses normalize to list without injecting a return URL', () => {
  for (const search of ['?tab=test-candidates&view=edit&selectedRuleSetCode=BASE', '?view=detail&selectedRuleSetCode=invalid', '?view=detail&selectedRuleSetCode=A&selectedRuleSetCode=B']) {
    assert.equal(parseRiskNavigation(search).view, 'list');
  }
  assert.equal(riskNavigationSearch(parseRiskNavigation('?returnUrl=https://example.com&payload=private')).includes('private'), false);
});

test('query code trims without uppercasing or silently removing invalid input', () => {
  assert.equal(ruleSetCodeError('  BASE_2  '), '');
  assert.equal(ruleSetCodeError(''), '');
  for (const code of ['base', '123', 'BASE-X', 'A'.repeat(65)]) assert.notEqual(ruleSetCodeError(code), '');
  const invalid = parseRiskNavigation('?ruleSetCode=base');
  assert.equal(invalid.filters.ruleSetCode, 'base');
});

test('page bounds are canonical and direct create restores only code', () => {
  for (const search of ['?page=0&size=11', '?page=100001&size=999', '?page=1&page=2']) {
    assert.deepEqual(parseRiskNavigation(search).paging, { page: 1, size: 10 });
  }
  const create = parseRiskNavigation('?view=create&selectedRuleSetCode=NEW_SET');
  assert.equal(create.view, 'create');
  assert.equal(create.selectedRuleSetCode, 'NEW_SET');
});

test('历史与精确版本导航保留固定上界和两个独立分页，不接受跨页面 returnUrl',()=>{
  const nav=parseRiskNavigation('?view=version&selectedRuleSetCode=RULE&rootId=12&versionId=14&maxVersionNo=9&historyPage=2&historySize=20&refPage=3&refSize=10&page=4&size=50');
  assert.equal(nav.view,'version'); assert.equal(nav.rootId,'12'); assert.equal(nav.historyPage,2);
  assert.deepEqual(parseRiskNavigation(riskNavigationSearch(nav)),nav);
  assert.equal(parseRiskNavigation('?view=version&selectedRuleSetCode=RULE&rootId=12&versionId=9223372036854775808').view,'list');
});
