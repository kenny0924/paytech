import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { dirname, extname, join, normalize, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { parseSync } from 'vite';
import { parseRiskTab, riskTabSearch } from './riskPageModel.ts';

const riskDirectory = fileURLToPath(new URL('.', import.meta.url));
const pagePath = fileURLToPath(new URL('../pages/RiskRuleConfiguration.tsx', import.meta.url));
const sourceDirectory = resolve(riskDirectory, '..');
const sessionStorageOwner = resolve(sourceDirectory, 'api/session.ts');

function productionTypeScriptSources(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return productionTypeScriptSources(path);
    if (!entry.isFile() || !/\.tsx?$/.test(entry.name) || /\.test\.tsx?$/.test(entry.name)
      || /fixtures?\.tsx?$/i.test(entry.name)) return [];
    return [path];
  });
}

type AstNode = { type: string; [key: string]: unknown };

const externalImportAllowlist = new Set(['antd', 'react', 'react-router-dom', '@xyflow/react', '@xyflow/react/dist/style.css']);
const browserCapabilities = new Set(['console', 'indexedDB', 'localStorage', 'sessionStorage']);
const handlerFields = new Set(['handlerCode', 'implementationHash', 'implementationVersion']);
const executableEscapeHatches = new Set(['eval', 'Function', 'require']);
const sensitiveGlobalObjects = new Set(['globalThis', 'self', 'window']);

function isAstNode(value: unknown): value is AstNode {
  return typeof value === 'object' && value !== null && typeof (value as { type?: unknown }).type === 'string';
}

function childNodes(node: AstNode): AstNode[] {
  const children: AstNode[] = [];
  for (const [key, value] of Object.entries(node)) {
    if (key === 'type' || key === 'start' || key === 'end') continue;
    if (isAstNode(value)) children.push(value);
    else if (Array.isArray(value)) children.push(...value.filter(isAstNode));
  }
  return children;
}

function unwrap(node: AstNode | null): AstNode | null {
  let current = node;
  while (current && ['ChainExpression', 'ParenthesizedExpression', 'TSAsExpression', 'TSNonNullExpression',
    'TSSatisfiesExpression', 'TSTypeAssertion'].includes(current.type)) {
    current = isAstNode(current.expression) ? current.expression : null;
  }
  return current;
}

type ExpressionCandidate = { kind: 'EXPRESSION'; expression: AstNode };
type UnknownCandidate = { kind: 'UNKNOWN' };
type InitializerCandidate =
  | ExpressionCandidate
  | { kind: 'OBJECT_MEMBER'; object: InitializerCandidate; property: AstNode; computed: boolean }
  | { kind: 'ARRAY_ELEMENT'; array: InitializerCandidate; index: number }
  | UnknownCandidate;
type InitializerLeaf = ExpressionCandidate | UnknownCandidate;

interface LexicalBinding {
  initializers: InitializerCandidate[];
}

interface LexicalScope {
  parent: LexicalScope | null;
  kind: 'PROGRAM' | 'FUNCTION' | 'BLOCK';
  bindings: Map<string, LexicalBinding>;
}

interface LexicalModel {
  root: LexicalScope;
  scopes: WeakMap<AstNode, LexicalScope>;
}

const functionNodes = new Set(['ArrowFunctionExpression', 'FunctionDeclaration', 'FunctionExpression']);
const blockScopeNodes = new Set(['BlockStatement', 'ForInStatement', 'ForOfStatement', 'ForStatement',
  'StaticBlock', 'SwitchStatement', 'TSModuleBlock']);
const unknownInitializer: UnknownCandidate = { kind: 'UNKNOWN' };
const expressionInitializer = (expression: AstNode): ExpressionCandidate => ({ kind: 'EXPRESSION', expression });

function buildLexicalModel(program: AstNode): LexicalModel {
  const root: LexicalScope = { parent: null, kind: 'PROGRAM', bindings: new Map() };
  const scopes = new WeakMap<AstNode, LexicalScope>();
  const runtimePatternDeclarations = new WeakSet<AstNode>();
  const makeScope = (parent: LexicalScope, kind: LexicalScope['kind']): LexicalScope =>
    ({ parent, kind, bindings: new Map() });
  const varScope = (scope: LexicalScope) => {
    let current = scope;
    while (current.kind === 'BLOCK' && current.parent) current = current.parent;
    return current;
  };
  const bindPattern = (pattern: AstNode | null, scope: LexicalScope,
    initializers: readonly InitializerCandidate[] = [], merge = false) => {
    const value = unwrap(pattern);
    if (!value) return;
    if (value.type === 'Identifier' && typeof value.name === 'string') {
      const existing = merge ? scope.bindings.get(value.name) : undefined;
      if (existing) {
        existing.initializers.push(...initializers);
        return;
      }
      scope.bindings.set(value.name, { initializers: [...initializers] });
      return;
    }
    if (value.type === 'AssignmentPattern' && isAstNode(value.left)) {
      const candidates = isAstNode(value.right) ? [...initializers, expressionInitializer(value.right)] : initializers;
      bindPattern(value.left, scope, candidates, merge);
      return;
    }
    if (value.type === 'RestElement' && isAstNode(value.argument)) {
      bindPattern(value.argument, scope, [], merge);
      return;
    }
    if (value.type === 'ArrayPattern' && Array.isArray(value.elements)) {
      for (let index = 0; index < value.elements.length; index += 1) {
        const element = value.elements[index];
        if (!isAstNode(element)) continue;
        if (element.type === 'RestElement') bindPattern(element, scope, [], merge);
        else bindPattern(element, scope, initializers.map(array => array.kind === 'UNKNOWN' ? unknownInitializer
          : { kind: 'ARRAY_ELEMENT', array, index }), merge);
      }
      return;
    }
    if (value.type === 'ObjectPattern' && Array.isArray(value.properties)) {
      for (const property of value.properties) {
        if (!isAstNode(property)) continue;
        if (property.type === 'RestElement' && isAstNode(property.argument)) {
          bindPattern(property.argument, scope, [], merge);
        }
        else if (property.type === 'Property' && isAstNode(property.value) && isAstNode(property.key)) {
          bindPattern(property.value, scope, initializers.map(object => object.kind === 'UNKNOWN' ? unknownInitializer
            : { kind: 'OBJECT_MEMBER', object, property: property.key as AstNode,
              computed: property.computed === true }), merge);
        }
      }
    }
  };
  const visit = (node: AstNode, inheritedScope: LexicalScope) => {
    if (functionNodes.has(node.type)) {
      if (node.type === 'FunctionDeclaration' && isAstNode(node.id)) {
        bindPattern(node.id, inheritedScope, [], true);
      }
      const functionScope = makeScope(inheritedScope, 'FUNCTION');
      scopes.set(node, functionScope);
      if (node.type === 'FunctionExpression' && isAstNode(node.id)) bindPattern(node.id, functionScope);
      if (Array.isArray(node.params)) {
        for (const parameter of node.params) {
          if (isAstNode(parameter)) bindPattern(parameter, functionScope, [unknownInitializer]);
        }
      }
      for (const child of childNodes(node)) visit(child, functionScope);
      return;
    }
    let scope = inheritedScope;
    if (node !== program && (blockScopeNodes.has(node.type) || node.type === 'CatchClause')) {
      scope = makeScope(inheritedScope, 'BLOCK');
    }
    scopes.set(node, scope);
    if ((node.type === 'ForInStatement' || node.type === 'ForOfStatement') && isAstNode(node.left)
      && node.left.type === 'VariableDeclaration') {
      runtimePatternDeclarations.add(node.left);
    }
    if (node.type === 'VariableDeclaration' && Array.isArray(node.declarations)) {
      const target = node.kind === 'var' ? varScope(scope) : scope;
      for (const declaration of node.declarations) {
        if (!isAstNode(declaration) || !isAstNode(declaration.id)) continue;
        const initializers = runtimePatternDeclarations.has(node) ? [unknownInitializer]
          : isAstNode(declaration.init) ? [expressionInitializer(declaration.init)] : [];
        bindPattern(declaration.id, target, initializers, node.kind === 'var');
      }
    }
    if (node.type === 'ClassDeclaration' && isAstNode(node.id)) bindPattern(node.id, scope);
    if (node.type === 'CatchClause' && isAstNode(node.param)) {
      bindPattern(node.param, scope, [unknownInitializer]);
    }
    if (node.type === 'ImportDeclaration' && Array.isArray(node.specifiers)) {
      for (const specifier of node.specifiers) {
        if (isAstNode(specifier) && isAstNode(specifier.local)) bindPattern(specifier.local, scope);
      }
    }
    for (const child of childNodes(node)) visit(child, scope);
  };
  visit(program, root);
  return { root, scopes };
}

function resolveBinding(identifier: AstNode, lexical: LexicalModel): LexicalBinding | null {
  if (identifier.type !== 'Identifier' || typeof identifier.name !== 'string') return null;
  let scope: LexicalScope | null = lexical.scopes.get(identifier) ?? lexical.root;
  while (scope) {
    const binding = scope.bindings.get(identifier.name);
    if (binding) return binding;
    scope = scope.parent;
  }
  return null;
}

function projectedPropertyName(property: AstNode, computed: boolean, lexical: LexicalModel,
  seen: ReadonlySet<LexicalBinding>): string | null {
  if (computed) return staticString(property, lexical, new Set(seen));
  if (property.type === 'Identifier' && typeof property.name === 'string') return property.name;
  return staticString(property, lexical, new Set(seen));
}

function candidateLeaves(candidate: InitializerCandidate, lexical: LexicalModel,
  seen: ReadonlySet<LexicalBinding>): InitializerLeaf[] {
  if (candidate.kind === 'UNKNOWN') return [unknownInitializer];
  if (candidate.kind === 'EXPRESSION') {
    const value = unwrap(candidate.expression);
    if (!value) return [unknownInitializer];
    if (value.type !== 'Identifier' || typeof value.name !== 'string') return [expressionInitializer(value)];
    const binding = resolveBinding(value, lexical);
    if (!binding) return [expressionInitializer(value)];
    if (seen.has(binding) || binding.initializers.length === 0) return [unknownInitializer];
    const nextSeen = new Set(seen); nextSeen.add(binding);
    return binding.initializers.flatMap(initializer => candidateLeaves(initializer, lexical, nextSeen));
  }
  if (candidate.kind === 'ARRAY_ELEMENT') {
    return candidateLeaves(candidate.array, lexical, seen).flatMap(array => {
      if (array.kind === 'UNKNOWN') return [unknownInitializer];
      const value = unwrap(array.expression);
      if (value?.type !== 'ArrayExpression' || !Array.isArray(value.elements)
        || value.elements.some(element => isAstNode(element) && element.type === 'SpreadElement')) {
        return [unknownInitializer];
      }
      const element = value.elements[candidate.index];
      return isAstNode(element) ? candidateLeaves(expressionInitializer(element), lexical, seen)
        : [unknownInitializer];
    });
  }
  const expectedName = projectedPropertyName(candidate.property, candidate.computed, lexical, seen);
  if (expectedName === null) return [unknownInitializer];
  return candidateLeaves(candidate.object, lexical, seen).flatMap(object => {
    if (object.kind === 'UNKNOWN') return [unknownInitializer];
    const value = unwrap(object.expression);
    if (value?.type !== 'ObjectExpression' || !Array.isArray(value.properties)) return [unknownInitializer];
    let result: AstNode | null = null;
    for (const property of value.properties) {
      if (!isAstNode(property) || property.type !== 'Property' || property.kind !== 'init'
        || property.method === true || !isAstNode(property.key) || !isAstNode(property.value)) {
        return [unknownInitializer];
      }
      const actualName = projectedPropertyName(property.key, property.computed === true, lexical, seen);
      if (actualName === null) return [unknownInitializer];
      if (actualName === expectedName) result = property.value;
    }
    return result ? candidateLeaves(expressionInitializer(result), lexical, seen) : [unknownInitializer];
  });
}

function staticStringCandidate(candidate: InitializerCandidate, lexical: LexicalModel,
  seen: ReadonlySet<LexicalBinding>): string | null {
  const values = candidateLeaves(candidate, lexical, seen).map(item => item.kind === 'EXPRESSION'
    ? staticString(item.expression, lexical, new Set(seen)) : null);
  if (values.length === 0 || values.some(value => value === null)) return null;
  return values.every(value => value === values[0]) ? values[0]! : null;
}

function staticStringArrayCandidate(candidate: InitializerCandidate, lexical: LexicalModel,
  seen: ReadonlySet<LexicalBinding>): string[] | null {
  const values = candidateLeaves(candidate, lexical, seen).map(item => item.kind === 'EXPRESSION'
    ? staticStringArray(item.expression, lexical, new Set(seen)) : null);
  if (values.length === 0 || values.some(value => value === null)) return null;
  const first = values[0]!;
  return values.every(value => value!.length === first.length
    && value!.every((part, index) => part === first[index])) ? first : null;
}

function staticStringArray(node: AstNode | null, lexical: LexicalModel,
  seen: ReadonlySet<LexicalBinding>): string[] | null {
  const value = unwrap(node);
  if (!value) return null;
  if (value.type === 'Identifier' && typeof value.name === 'string') {
    const binding = resolveBinding(value, lexical);
    if (!binding || binding.initializers.length === 0 || seen.has(binding)) return null;
    const nextSeen = new Set(seen); nextSeen.add(binding);
    const values = binding.initializers.map(initializer => staticStringArrayCandidate(initializer, lexical, nextSeen));
    if (values.some(item => item === null)) return null;
    const first = values[0]!;
    return values.every(item => item!.length === first.length && item!.every((part, index) => part === first[index]))
      ? first : null;
  }
  if (value.type !== 'ArrayExpression' || !Array.isArray(value.elements)) return null;
  const result: string[] = [];
  for (const element of value.elements) {
    if (!isAstNode(element)) return null;
    const item = staticString(element, lexical, new Set(seen));
    if (item === null) return null;
    result.push(item);
  }
  return result;
}

function staticString(node: AstNode | null, lexical: LexicalModel,
  seen = new Set<LexicalBinding>()): string | null {
  const value = unwrap(node);
  if (!value) return null;
  if (value.type === 'Literal' && typeof value.value === 'string') return value.value;
  if (value.type === 'Identifier' && typeof value.name === 'string') {
    const binding = resolveBinding(value, lexical);
    if (!binding || binding.initializers.length === 0 || seen.has(binding)) return null;
    const nextSeen = new Set(seen); nextSeen.add(binding);
    const values = binding.initializers.map(initializer => staticStringCandidate(initializer, lexical, nextSeen));
    if (values.some(item => item === null)) return null;
    return values.every(item => item === values[0]) ? values[0]! : null;
  }
  if (value.type === 'BinaryExpression' && value.operator === '+' && isAstNode(value.left) && isAstNode(value.right)) {
    const left = staticString(value.left, lexical, seen); const right = staticString(value.right, lexical, seen);
    return left === null || right === null ? null : left + right;
  }
  if (value.type === 'CallExpression' && isAstNode(value.callee)) {
    const callee = unwrap(value.callee);
    if (callee?.type !== 'MemberExpression' || callee.computed === true || !isAstNode(callee.object)
      || !isAstNode(callee.property) || callee.property.type !== 'Identifier' || callee.property.name !== 'join'
      || !Array.isArray(value.arguments) || value.arguments.length > 1) return null;
    const items = staticStringArray(callee.object, lexical, seen);
    if (!items) return null;
    if (value.arguments.length === 0) return items.join(',');
    const separatorNode = value.arguments[0];
    const separator = isAstNode(separatorNode) ? staticString(separatorNode, lexical, seen) : null;
    return separator === null ? null : items.join(separator);
  }
  if (value.type === 'TemplateLiteral' && Array.isArray(value.quasis) && Array.isArray(value.expressions)) {
    let result = '';
    for (let index = 0; index < value.quasis.length; index += 1) {
      const quasi = value.quasis[index];
      if (!isAstNode(quasi) || typeof quasi.value !== 'object' || quasi.value === null
        || typeof (quasi.value as { cooked?: unknown }).cooked !== 'string') return null;
      result += (quasi.value as { cooked: string }).cooked;
      if (index < value.expressions.length) {
        const expression = value.expressions[index];
        if (!isAstNode(expression)) return null;
        const expressionValue = staticString(expression, lexical, seen);
        if (expressionValue === null) return null;
        result += expressionValue;
      }
    }
    return result;
  }
  return null;
}

function sensitiveGlobalCandidate(candidate: InitializerCandidate, lexical: LexicalModel,
  seen: ReadonlySet<LexicalBinding>): string | null {
  if (candidate.kind === 'UNKNOWN') return null;
  if (candidate.kind === 'EXPRESSION') {
    const value = unwrap(candidate.expression);
    if (!value) return null;
    if (value.type === 'Identifier' && typeof value.name === 'string') {
      const binding = resolveBinding(value, lexical);
      if (!binding) return sensitiveGlobalObjects.has(value.name) ? value.name : null;
      if (seen.has(binding) || binding.initializers.length === 0) return null;
      const nextSeen = new Set(seen); nextSeen.add(binding);
      for (const initializer of binding.initializers) {
        const globalObject = sensitiveGlobalCandidate(initializer, lexical, nextSeen);
        if (globalObject) return globalObject;
      }
      return null;
    }
    return sensitiveGlobalObject(value, lexical, new Set(seen));
  }
  if (candidate.kind === 'OBJECT_MEMBER') {
    const owner = sensitiveGlobalCandidate(candidate.object, lexical, seen);
    const property = projectedPropertyName(candidate.property, candidate.computed, lexical, seen);
    if (owner && property === null) return owner;
    if (owner && property && sensitiveGlobalObjects.has(property)) return property;
  }
  for (const leaf of candidateLeaves(candidate, lexical, seen)) {
    if (leaf.kind !== 'EXPRESSION') continue;
    const globalObject = sensitiveGlobalObject(leaf.expression, lexical, new Set(seen));
    if (globalObject) return globalObject;
  }
  return null;
}

function sensitiveGlobalObject(node: AstNode | null, lexical: LexicalModel,
  seen = new Set<LexicalBinding>()): string | null {
  const value = unwrap(node);
  if (!value) return null;
  if (value.type === 'Identifier' && typeof value.name === 'string') {
    const binding = resolveBinding(value, lexical);
    if (!binding && sensitiveGlobalObjects.has(value.name)) return value.name;
    if (!binding || binding.initializers.length === 0 || seen.has(binding)) return null;
    const nextSeen = new Set(seen); nextSeen.add(binding);
    for (const initializer of binding.initializers) {
      const globalObject = sensitiveGlobalCandidate(initializer, lexical, nextSeen);
      if (globalObject) return globalObject;
    }
    return null;
  }
  if (value.type === 'MemberExpression' && isAstNode(value.object)) {
    const owner = sensitiveGlobalObject(value.object, lexical, seen);
    if (!owner) return null;
    const property = memberName(value, lexical);
    if (property === null) return owner;
    return sensitiveGlobalObjects.has(property) ? property : null;
  }
  return null;
}

function memberName(node: AstNode, lexical: LexicalModel): string | null {
  if (node.type !== 'MemberExpression' || !isAstNode(node.property)) return null;
  if (node.computed === true) return staticString(node.property, lexical);
  return node.property.type === 'Identifier' && typeof node.property.name === 'string' ? node.property.name : null;
}

function moduleSpecifier(node: AstNode): string | null {
  return isAstNode(node.source) && node.source.type === 'Literal' && typeof node.source.value === 'string'
    ? node.source.value : null;
}

function resolveLocalModule(importer: string, specifier: string, sources: ReadonlyMap<string, string>): string | null {
  const base = normalize(resolve(dirname(importer), specifier));
  for (const candidate of [base, `${base}.ts`, `${base}.tsx`, join(base, 'index.ts'), join(base, 'index.tsx')]) {
    if (sources.has(candidate)) return candidate;
  }
  return null;
}

function auditSourceGraph(sources: ReadonlyMap<string, string>, roots: readonly string[],
  approvedStorageOwners: ReadonlySet<string> = new Set()): string[] {
  const normalizedSources = new Map([...sources].map(([path, source]) => [normalize(path), source]));
  const queue = roots.map(normalize); const visited = new Set<string>(); const violations = new Set<string>();
  const addViolation = (path: string, reason: string) => violations.add(`${path}: ${reason}`);
  while (queue.length > 0) {
    const path = queue.shift()!;
    if (visited.has(path)) continue;
    visited.add(path);
    const source = normalizedSources.get(path);
    if (source === undefined) { addViolation(path, 'missing local module in security graph'); continue; }
    const parsed = parseSync(path, source);
    for (const error of parsed.errors) addViolation(path, `TypeScript parse error: ${error.message}`);
    const program = parsed.program as unknown as AstNode;
    const lexical = buildLexicalModel(program);
    const visit = (node: AstNode) => {
      if (node.type === 'ImportExpression') addViolation(path, 'dynamic import is forbidden');
      if (node.type === 'CallExpression' || node.type === 'NewExpression') {
        const callee = isAstNode(node.callee) ? unwrap(node.callee) : null;
        if (callee?.type === 'Import') addViolation(path, 'dynamic import is forbidden');
        if (callee?.type === 'Identifier' && typeof callee.name === 'string'
          && executableEscapeHatches.has(callee.name)) addViolation(path, `${callee.name} execution is forbidden`);
      }
      if (node.type === 'Identifier' && typeof node.name === 'string') {
        if (handlerFields.has(node.name)) addViolation(path, `Handler field ${node.name} is forbidden`);
        if (browserCapabilities.has(node.name) && resolveBinding(node, lexical) === null
          && !(approvedStorageOwners.has(path) && node.name === 'sessionStorage')) {
          addViolation(path, `browser capability ${node.name} is forbidden`);
        }
      }
      const propertyName = memberName(node, lexical);
      if (propertyName && handlerFields.has(propertyName)) addViolation(path, `Handler field ${propertyName} is forbidden`);
      if (propertyName && browserCapabilities.has(propertyName)
        && !(approvedStorageOwners.has(path) && propertyName === 'sessionStorage')) {
        addViolation(path, `browser capability ${propertyName} is forbidden`);
      }
      if (propertyName && executableEscapeHatches.has(propertyName)) {
        addViolation(path, `${propertyName} member is forbidden`);
      }
      if (node.type === 'MemberExpression' && node.computed === true && propertyName === null && isAstNode(node.object)) {
        const globalObject = sensitiveGlobalObject(node.object, lexical);
        if (globalObject) addViolation(path, `unknown computed access on ${globalObject} is forbidden`);
      }
      if (['Property', 'PropertyDefinition', 'TSPropertySignature'].includes(node.type) && isAstNode(node.key)) {
        const propertyKey = node.computed === true ? staticString(node.key, lexical)
          : node.key.type === 'Identifier' && typeof node.key.name === 'string' ? node.key.name
            : staticString(node.key, lexical);
        if (propertyKey && handlerFields.has(propertyKey)) addViolation(path, `Handler field ${propertyKey} is forbidden`);
        if (propertyKey && browserCapabilities.has(propertyKey)
          && !(approvedStorageOwners.has(path) && propertyKey === 'sessionStorage')) {
          addViolation(path, `browser capability ${propertyKey} is forbidden`);
        }
        if (propertyKey && executableEscapeHatches.has(propertyKey)) {
          addViolation(path, `${propertyKey} property is forbidden`);
        }
      }
      if (['ImportDeclaration', 'ExportAllDeclaration', 'ExportNamedDeclaration'].includes(node.type)) {
        const specifier = moduleSpecifier(node);
        if (specifier?.startsWith('.')) {
          const dependency = resolveLocalModule(path, specifier, normalizedSources);
          if (dependency) queue.push(dependency);
          else if (!['.css', '.less', '.scss'].includes(extname(specifier))) {
            addViolation(path, `unresolved local import ${specifier}`);
          }
        } else if (specifier && !externalImportAllowlist.has(specifier)) {
          addViolation(path, `external import ${specifier} is not allowlisted`);
        }
      }
      for (const child of childNodes(node)) visit(child);
    };
    visit(program);
  }
  return [...violations].sort();
}

function assertCompileValid(source: string) {
  assert.deepEqual(parseSync('mutation.ts', source).errors, []);
}

test('Risk production sources cannot persist, log or expose Handler implementation fields', () => {
  const roots = [...productionTypeScriptSources(riskDirectory), pagePath];
  const sources = new Map(productionTypeScriptSources(sourceDirectory).map(path => [path, readFileSync(path, 'utf8')]));
  assert.deepEqual(auditSourceGraph(sources, roots, new Set([sessionStorageOwner])), []);
});

const mutationCases = [
  ['optional console access', `console?.log('rule'); export {};`],
  ['aliased console source', `const output = console; output.log('rule'); export {};`],
  ['computed browser storage', `const suffix = 'Storage'; window['local' + suffix].setItem('rule', 'secret'); export {};`],
  ['computed Handler field', `const suffix = 'Code'; const key = 'handler' + suffix; ({})[key]; export {};`],
] as const;
for (const [name, source] of mutationCases) {
  test(`security mutation rejects ${name}`, () => {
    assertCompileValid(source);
    const path = `/virtual/src/risk/${name.replaceAll(' ', '-')}.ts`;
    assert.ok(auditSourceGraph(new Map([[path, source]]), [path]).length > 0, `${name} escaped the security gate`);
  });
}

test('security mutation follows a local import outside the Risk directory', () => {
  const root = `/virtual/src/risk/root.ts`; const bridge = `/virtual/src/risk/bridge.ts`;
  const helper = `/virtual/src/shared/riskHelper.ts`;
  const sources = new Map([[root, `import './bridge'; export const safe = true;`],
    [bridge, `export * from '../shared/riskHelper';`],
    [helper, `const output = console; output?.log('rule'); export {};`]]);
  for (const source of sources.values()) assertCompileValid(source);
  assert.ok(auditSourceGraph(sources, [root]).some(violation => violation.includes('riskHelper.ts')),
    'a forbidden outside helper escaped the root import closure');
});

for (const [name, source] of [
  ['dynamic import', `void import('./escape');`],
  ['require', `declare const require: (path: string) => unknown; require('./escape');`],
  ['eval', `eval('1 + 1');`],
  ['Function constructor', `Function('return 1')();`],
] as const) {
  test(`security mutation rejects the ${name} escape hatch`, () => {
    assertCompileValid(source);
    const path = `/virtual/src/risk/${name.replaceAll(' ', '-')}.ts`;
    assert.ok(auditSourceGraph(new Map([[path, source]]), [path]).length > 0, `${name} escaped the security gate`);
  });
}

test('security mutation rejects an external module outside the explicit UI allowlist', () => {
  const source = `import 'unapproved-security-escape'; export {};`;
  assertCompileValid(source);
  const path = `/virtual/src/risk/external-import.ts`;
  assert.ok(auditSourceGraph(new Map([[path, source]]), [path])
    .some(violation => violation.includes('not allowlisted')));
});

for (const [name, source] of [
  ['computed global eval', `globalThis['ev' + 'al']('1 + 1'); export {};`],
  ['computed global require', `globalThis['re' + 'quire']('./escape'); export {};`],
  ['computed global Function constructor', `new globalThis['Fun' + 'ction']('return 1')(); export {};`],
  ['computed executable property', `const obj = { ['ev' + 'al']: () => 1 }; void obj; export {};`],
  ['array-joined Handler field', `const obj: Record<string, unknown> = {}; void obj[['handler', 'Code'].join('')]; export {};`],
  ['bound array-joined storage', `const parts = ['local', 'Storage']; void globalThis[parts.join('')]; export {};`],
  ['unknown computed global access', `declare const runtimeKey: string; void globalThis[runtimeKey]; export {};`],
] as const) {
  test(`security mutation rejects ${name}`, () => {
    assertCompileValid(source);
    const path = `/virtual/src/risk/${name.replaceAll(' ', '-')}.ts`;
    assert.ok(auditSourceGraph(new Map([[path, source]]), [path]).length > 0, `${name} escaped the security gate`);
  });
}

for (const [name, source] of [
  ['outer global alias survives an unused inner shadow', `
    const root = globalThis;
    function unused() { const root = {}; void root; }
    declare const runtimeKey: string;
    void root[runtimeKey];
    export {};
  `],
  ['globalThis window chain alias', `
    const browserRoot = globalThis.window;
    declare const runtimeKey: string;
    void browserRoot[runtimeKey];
    export {};
  `],
  ['inner block global alias', `
    const root = {};
    declare const runtimeKey: string;
    { const root = globalThis; void root[runtimeKey]; }
    export {};
  `],
  ['outer sensitive property key survives an inner shadow', `
    const field = 'handlerCode';
    function unused() { const field = 'safe'; void field; }
    void ({})[field];
    export {};
  `],
] as const) {
  test(`security lexical-scope mutation rejects ${name}`, () => {
    assertCompileValid(source);
    const path = `/virtual/src/risk/lexical-${name.replaceAll(' ', '-')}.ts`;
    assert.ok(auditSourceGraph(new Map([[path, source]]), [path]).length > 0, `${name} escaped the security gate`);
  });
}

for (const [name, source] of [
  ['ordinary outer object ignores an unused inner global alias', `
    const root = {};
    function unused() { const root = globalThis; void root; }
    declare const runtimeKey: string;
    void root[runtimeKey];
    export {};
  `],
  ['function parameter shadows an outer global alias', `
    const root = globalThis;
    function inspect(root: Record<string, unknown>, runtimeKey: string) { void root[runtimeKey]; }
    void inspect;
    export {};
  `],
  ['inner block object shadows an outer global alias', `
    const root = globalThis;
    declare const runtimeKey: string;
    { const root = {}; void root[runtimeKey]; }
    export {};
  `],
  ['cyclic aliases terminate without classifying an ordinary computed access', `
    declare const runtimeKey: string;
    const first = second;
    const second = first;
    void first[runtimeKey];
    export {};
  `],
  ['ordinary computed access remains allowed', `
    declare const runtimeKey: string;
    const ordinary = {};
    void ordinary[runtimeKey];
    export {};
  `],
  ['outer ordinary property key ignores an inner sensitive shadow', `
    const field = 'safe';
    function unused() { const field = 'handlerCode'; void field; }
    void ({})[field];
    export {};
  `],
  ['function parameter shadows the sensitive global name', `
    function inspect(globalThis: Record<string, unknown>, runtimeKey: string) { void globalThis[runtimeKey]; }
    void inspect;
    export {};
  `],
] as const) {
  test(`security lexical-scope mutation allows ${name}`, () => {
    assertCompileValid(source);
    const path = `/virtual/src/risk/lexical-${name.replaceAll(' ', '-')}.ts`;
    assert.deepEqual(auditSourceGraph(new Map([[path, source]]), [path]), []);
  });
}

for (const [name, source] of [
  ['object destructuring from globalThis', `
    const { window: root } = globalThis;
    declare const runtimeKey: string;
    void root[runtimeKey];
    export {};
  `],
  ['defaulted function parameter from globalThis', `
    function inspect(root = globalThis, runtimeKey: string) { void root[runtimeKey]; }
    void inspect;
    export {};
  `],
  ['computed object destructuring from globalThis', `
    const { ['win' + 'dow']: root } = globalThis;
    declare const runtimeKey: string;
    void root[runtimeKey];
    export {};
  `],
  ['nested object destructuring from globalThis', `
    const { window: { self: root } } = globalThis;
    declare const runtimeKey: string;
    void root[runtimeKey];
    export {};
  `],
  ['direct static array destructuring from globalThis', `
    const [, root] = [{}, globalThis];
    declare const runtimeKey: string;
    void root[runtimeKey];
    export {};
  `],
  ['aliased static array destructuring from globalThis', `
    const sources = [{}, globalThis];
    const [, root] = sources;
    declare const runtimeKey: string;
    void root[runtimeKey];
    export {};
  `],
  ['destructured AssignmentPattern with a global default', `
    const { missing: root = globalThis } = {};
    declare const runtimeKey: string;
    void root[runtimeKey];
    export {};
  `],
  ['consistent destructured Handler field candidates', `
    const { field = 'handlerCode' } = { field: 'handlerCode' };
    void ({})[field];
    export {};
  `],
  ['static array destructured Handler field', `
    const fields = ['handlerCode'];
    const [field] = fields;
    void ({})[field];
    export {};
  `],
] as const) {
  test(`security destructuring mutation rejects ${name}`, () => {
    assertCompileValid(source);
    const path = `/virtual/src/risk/destructuring-${name.replaceAll(' ', '-')}.ts`;
    assert.ok(auditSourceGraph(new Map([[path, source]]), [path]).length > 0, `${name} escaped the security gate`);
  });
}

for (const [name, source] of [
  ['ordinary object destructuring', `
    const { safe: root } = { safe: {} };
    declare const runtimeKey: string;
    void root[runtimeKey];
    export {};
  `],
  ['computed ordinary object destructuring', `
    const { ['safe']: root } = { safe: {} };
    declare const runtimeKey: string;
    void root[runtimeKey];
    export {};
  `],
  ['nested ordinary object destructuring', `
    const { outer: { inner: root } } = { outer: { inner: {} } };
    declare const runtimeKey: string;
    void root[runtimeKey];
    export {};
  `],
  ['object rest from globalThis', `
    const { ...root } = globalThis;
    declare const runtimeKey: string;
    void root[runtimeKey];
    export {};
  `],
  ['ordinary static array destructuring', `
    const [root] = [{}];
    declare const runtimeKey: string;
    void root[runtimeKey];
    export {};
  `],
  ['array rest from a globalThis element', `
    const [...root] = [globalThis];
    declare const runtimeKey: string;
    void root[runtimeKey];
    export {};
  `],
  ['ordinary defaulted function parameter', `
    function inspect(root = {}, runtimeKey: string) { void root[runtimeKey]; }
    void inspect;
    export {};
  `],
  ['plain function parameter without a default', `
    function inspect(root: Record<string, unknown>, runtimeKey: string) { void root[runtimeKey]; }
    void inspect;
    export {};
  `],
  ['conflicting static string candidates stay unknown', `
    const { field = 'handlerCode' } = { field: 'safe' };
    void ({})[field];
    export {};
  `],
] as const) {
  test(`security destructuring mutation allows ${name}`, () => {
    assertCompileValid(source);
    const path = `/virtual/src/risk/destructuring-${name.replaceAll(' ', '-')}.ts`;
    assert.deepEqual(auditSourceGraph(new Map([[path, source]]), [path]), []);
  });
}

for (const [name, source] of [
  ['object projection through three ordinary aliases', `
    const { window: root } = globalThis;
    const first = root;
    const second = first;
    const third = second;
    declare const runtimeKey: string;
    void third[runtimeKey];
    export {};
  `],
  ['destructured assignment default through an alias', `
    const { missing: root = globalThis } = {};
    const alias = root;
    declare const runtimeKey: string;
    void alias[runtimeKey];
    export {};
  `],
  ['function parameter default through an alias', `
    function inspect(root = globalThis, runtimeKey: string) {
      const alias = root;
      void alias[runtimeKey];
    }
    void inspect;
    export {};
  `],
  ['consistent Handler string candidates through an alias', `
    const { field = 'handlerCode' } = { field: 'handlerCode' };
    const alias = field;
    void ({})[alias];
    export {};
  `],
  ['array projection of a Handler string through two aliases', `
    const fields = ['handlerCode'];
    const [field] = fields;
    const first = field;
    const second = first;
    void ({})[second];
    export {};
  `],
] as const) {
  test(`security second-alias mutation rejects ${name}`, () => {
    assertCompileValid(source);
    const path = `/virtual/src/risk/second-alias-${name.replaceAll(' ', '-')}.ts`;
    assert.ok(auditSourceGraph(new Map([[path, source]]), [path]).length > 0, `${name} escaped the security gate`);
  });
}

for (const [name, source] of [
  ['ordinary object projection through an alias', `
    const { safe: root } = { safe: {} };
    const alias = root;
    declare const runtimeKey: string;
    void alias[runtimeKey];
    export {};
  `],
  ['unknown plain parameter through an alias', `
    function inspect(root: Record<string, unknown>, runtimeKey: string) {
      const alias = root;
      void alias[runtimeKey];
    }
    void inspect;
    export {};
  `],
  ['dynamic array projection through an alias', `
    declare const sources: unknown[];
    const [, root] = sources;
    const alias = root;
    declare const runtimeKey: string;
    void alias[runtimeKey];
    export {};
  `],
  ['out-of-bounds static array projection through an alias', `
    const [, root] = [{}];
    const alias = root;
    declare const runtimeKey: string;
    void alias[runtimeKey];
    export {};
  `],
  ['getter projection through an alias without executing it', `
    const source = { get root() { return globalThis; } };
    const { root } = source;
    const alias = root;
    declare const runtimeKey: string;
    void alias[runtimeKey];
    export {};
  `],
  ['spread projection through an alias stays unknown', `
    const inherited = { root: globalThis };
    const source = { ...inherited };
    const { root } = source;
    const alias = root;
    declare const runtimeKey: string;
    void alias[runtimeKey];
    export {};
  `],
  ['plain parameter shadow through an alias', `
    const { window: root } = globalThis;
    function inspect(root: Record<string, unknown>, runtimeKey: string) {
      const alias = root;
      void alias[runtimeKey];
    }
    void inspect;
    export {};
  `],
  ['cyclic second aliases terminate', `
    declare const runtimeKey: string;
    const first = second;
    const second = first;
    const alias = first;
    void alias[runtimeKey];
    export {};
  `],
  ['conflicting Handler string candidates through an alias stay unknown', `
    const { field = 'handlerCode' } = { field: 'safe' };
    const alias = field;
    void ({})[alias];
    export {};
  `],
] as const) {
  test(`security second-alias mutation allows ${name}`, () => {
    assertCompileValid(source);
    const path = `/virtual/src/risk/second-alias-${name.replaceAll(' ', '-')}.ts`;
    assert.deepEqual(auditSourceGraph(new Map([[path, source]]), [path]), []);
  });
}

for (const [name, source] of [
  ['catch-clause destructuring keeps its unknown runtime candidate', `
    try { throw {}; } catch ({ field = 'safe' }) {
      void globalThis[field];
    }
    export {};
  `],
  ['for-of destructuring keeps its unknown runtime candidate', `
    declare const rows: Array<{ field?: string }>;
    for (const { field = 'safe' } of rows) {
      void globalThis[field];
    }
    export {};
  `],
  ['for-in destructuring keeps its unknown runtime candidate', `
    declare const rows: Record<string, unknown>;
    for (const { field = 'safe' } in rows) {
      void globalThis[field];
    }
    export {};
  `],
] as const) {
  test(`security runtime-pattern mutation rejects ${name}`, () => {
    assertCompileValid(source);
    const path = `/virtual/src/risk/runtime-pattern-${name.replaceAll(' ', '-')}.ts`;
    assert.ok(auditSourceGraph(new Map([[path, source]]), [path]).length > 0, `${name} escaped the security gate`);
  });
}

for (const [name, source] of [
  ['catch-clause unknown key on an ordinary object', `
    const ordinary: Record<string, unknown> = {};
    try { throw {}; } catch ({ field = 'safe' }) {
      void ordinary[field];
    }
    export {};
  `],
  ['for-of pattern shadows an outer static field on an ordinary object', `
    const field = 'handlerCode';
    const ordinary: Record<string, unknown> = {};
    declare const rows: Array<{ field?: string }>;
    for (const { field = 'safe' } of rows) {
      void ordinary[field];
    }
    export {};
  `],
  ['for-in plain binding shadows an outer static field on an ordinary object', `
    const field = 'handlerCode';
    const ordinary: Record<string, unknown> = {};
    declare const rows: Record<string, unknown>;
    for (const field in rows) {
      void ordinary[field];
    }
    export {};
  `],
] as const) {
  test(`security runtime-pattern mutation allows ${name}`, () => {
    assertCompileValid(source);
    const path = `/virtual/src/risk/runtime-pattern-${name.replaceAll(' ', '-')}.ts`;
    assert.deepEqual(auditSourceGraph(new Map([[path, source]]), [path]), []);
  });
}

for (const [name, source] of [
  ['later uninitialized var preserves an earlier sensitive initializer', `
    declare const runtimeKey: string;
    var root = globalThis;
    void (root as typeof globalThis)[runtimeKey];
    var root;
    export {};
  `],
  ['earlier uninitialized var joins a later sensitive initializer', `
    declare const runtimeKey: string;
    var root;
    var root = globalThis;
    void (root as typeof globalThis)[runtimeKey];
    export {};
  `],
  ['later ordinary var initializer cannot erase an earlier sensitive initializer', `
    declare const runtimeKey: string;
    var root = globalThis;
    void (root as typeof globalThis)[runtimeKey];
    var root = {};
    export {};
  `],
  ['earlier ordinary var initializer joins a later sensitive initializer', `
    declare const runtimeKey: string;
    var root = {};
    var root = globalThis;
    void (root as typeof globalThis)[runtimeKey];
    export {};
  `],
  ['later for-of var runtime value cannot erase an earlier sensitive initializer', `
    declare const runtimeKey: string;
    declare const rows: Array<typeof globalThis>;
    var root = globalThis;
    void (root as typeof globalThis)[runtimeKey];
    for (var root of rows) { void root; }
    export {};
  `],
  ['earlier for-of var runtime value joins a later sensitive initializer', `
    declare const runtimeKey: string;
    declare const rows: Array<typeof globalThis>;
    for (var root of rows) { void root; }
    var root = globalThis;
    void (root as typeof globalThis)[runtimeKey];
    export {};
  `],
  ['later for-in var runtime value cannot erase an earlier sensitive initializer', `
    declare const runtimeKey: string;
    declare const rows: Record<string, unknown>;
    var root = globalThis;
    void (root as typeof globalThis)[runtimeKey];
    for (var root in rows) { void root; }
    export {};
  `],
  ['earlier for-in var runtime value joins a later sensitive initializer', `
    declare const runtimeKey: string;
    declare const rows: Record<string, unknown>;
    for (var root in rows) { void root; }
    var root = globalThis;
    void (root as typeof globalThis)[runtimeKey];
    export {};
  `],
  ['later var declaration preserves a sensitive parameter default', `
    function inspect(root = globalThis, runtimeKey: string) {
      void (root as typeof globalThis)[runtimeKey];
      var root;
    }
    void inspect;
    export {};
  `],
  ['parameter and sensitive var initializer share one function binding', `
    function inspect(root: Record<string, unknown>, runtimeKey: string) {
      var root = globalThis;
      void (root as typeof globalThis)[runtimeKey];
    }
    void inspect;
    export {};
  `],
] as const) {
  test(`security var-binding mutation rejects ${name}`, () => {
    assertCompileValid(source);
    const path = `/virtual/src/risk/var-binding-${name.replaceAll(' ', '-')}.ts`;
    assert.ok(auditSourceGraph(new Map([[path, source]]), [path]).length > 0, `${name} escaped the security gate`);
  });
}

for (const [name, source] of [
  ['pure unknown loop binding on an ordinary object', `
    declare const rows: unknown[];
    declare const runtimeKey: string;
    const ordinary: Record<string, unknown> = {};
    for (var root of rows) { void ordinary[root as string]; }
    void ordinary[runtimeKey];
    export {};
  `],
  ['uninitialized var preserves an ordinary object initializer', `
    declare const runtimeKey: string;
    var root = {};
    var root;
    void root[runtimeKey];
    export {};
  `],
  ['block let remains an independent ordinary shadow', `
    declare const runtimeKey: string;
    var root = globalThis;
    { let root = {}; void root[runtimeKey]; }
    export {};
  `],
  ['repeated var keeps an agreed safe static string', `
    var field = 'safe';
    var field = 'safe';
    void globalThis[field];
    export {};
  `],
  ['cyclic var aliases terminate on an ordinary object', `
    declare const runtimeKey: string;
    var first = second;
    var second = first;
    var first;
    const ordinary: Record<string, unknown> = {};
    void ordinary[first as unknown as string];
    void ordinary[runtimeKey];
    export {};
  `],
  ['function declaration and var share a local window binding', `
    declare const runtimeKey: string;
    function window() { return undefined; }
    var window;
    void window[runtimeKey];
    export {};
  `],
] as const) {
  test(`security var-binding mutation allows ${name}`, () => {
    assertCompileValid(source);
    const path = `/virtual/src/risk/var-binding-${name.replaceAll(' ', '-')}.ts`;
    assert.deepEqual(auditSourceGraph(new Map([[path, source]]), [path]), []);
  });
}

test('Risk tab parsing defaults safely and rewrites every input to the sole canonical tab key', () => {
  const cases = [
    ['', 'rule-sets'],
    ['?tab=rule-sets&secret=discard&command=discard', 'rule-sets'],
    ['?secret=discard&tab=test-candidates&payload=discard', 'test-candidates'],
    ['?tab=test-candidates&tab=rule-sets&secret=discard', 'rule-sets'],
    ['?tab=unknown&secret=discard', 'rule-sets'],
  ] as const;
  for (const [input, expected] of cases) {
    const tab = parseRiskTab(input);
    assert.equal(tab, expected);
    const rewritten = riskTabSearch(tab);
    const parameters = new URLSearchParams(rewritten.slice(1));
    assert.deepEqual([...parameters.keys()], ['tab']);
    assert.equal(parameters.get('tab'), expected);
    assert.equal(rewritten, `?tab=${expected}`);
  }
});
