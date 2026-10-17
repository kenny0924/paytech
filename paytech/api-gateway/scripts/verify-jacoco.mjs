import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import {fileURLToPath} from 'node:url';
import {requiresCoverageReport} from './class-file.mjs';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const reportDirectory = path.join(repositoryRoot, 'paytech-api-gateway-test/target/site/jacoco-aggregate');
const reportXml = path.join(reportDirectory, 'jacoco.xml');
const reportHtml = path.join(reportDirectory, 'index.html');
const executionData = path.join(repositoryRoot, 'paytech-api-gateway-test/target/jacoco.exec');
const webClassesDirectory = path.join(repositoryRoot, 'paytech-api-gateway-app/target/classes');
const webClassesRoot = path.join(repositoryRoot, 'paytech-api-gateway-app/target/classes/com/paytech/gateway');
const testClassesRoot = path.join(repositoryRoot, 'paytech-api-gateway-test/target/test-classes');
const surefireRoot = path.join(repositoryRoot, 'paytech-api-gateway-test/target/surefire-reports');
const minimumRatio = 0.90;
const excludedClasses = new Set(['com/paytech/gateway/ApiGatewayApplication']);
const failures = [];

function requireNonemptyFile(file, description) {
    if (!fs.existsSync(file)) {
        failures.push(`${description} is missing: ${path.relative(repositoryRoot, file)}`);
        return false;
    }
    if (!fs.statSync(file).isFile() || fs.statSync(file).size === 0) {
        failures.push(`${description} is empty: ${path.relative(repositoryRoot, file)}`);
        return false;
    }
    return true;
}

function filesUnder(root, predicate, description) {
    if (!fs.existsSync(root)) {
        failures.push(`${description} directory is missing: ${path.relative(repositoryRoot, root)}`);
        return [];
    }
    const result = [];
    const visit = current => {
        for (const entry of fs.readdirSync(current, {withFileTypes: true})) {
            const resolved = path.join(current, entry.name);
            if (entry.isDirectory()) {
                visit(resolved);
            } else if (entry.isFile() && predicate(entry.name)) {
                result.push(resolved);
            }
        }
    };
    visit(root);
    if (result.length === 0) {
        failures.push(`${description} contains no evidence files: ${path.relative(repositoryRoot, root)}`);
    }
    return result.sort();
}

const hasReportXml = requireNonemptyFile(reportXml, 'JaCoCo aggregate XML');
requireNonemptyFile(reportHtml, 'JaCoCo aggregate HTML index');
const hasExecutionData = requireNonemptyFile(executionData, 'JaCoCo execution data');
const webClassFiles = filesUnder(webClassesRoot, name => name.endsWith('.class'), 'Gateway web classes');
const testClassFiles = filesUnder(testClassesRoot, name => name.endsWith('.class'), 'Gateway test classes');
const surefireFiles = filesUnder(surefireRoot,
    name => name.startsWith('TEST-') && name.endsWith('.xml'), 'Surefire XML reports');

if (hasReportXml) {
    const reportTimestamp = fs.statSync(reportXml).mtimeMs;
    const evidenceFiles = [
        ...(hasExecutionData ? [executionData] : []),
        ...webClassFiles,
        ...testClassFiles,
        ...surefireFiles
    ];
    const newerEvidence = evidenceFiles
        .filter(file => fs.statSync(file).mtimeMs > reportTimestamp)
        .sort((left, right) => fs.statSync(right).mtimeMs - fs.statSync(left).mtimeMs);
    if (newerEvidence.length > 0) {
        failures.push(`JaCoCo aggregate XML is stale; newer evidence exists: `
            + path.relative(repositoryRoot, newerEvidence[0]));
    }
}

function attributes(source) {
    const result = new Map();
    const attributePattern = /([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*(["'])(.*?)\2/g;
    for (const match of source.matchAll(attributePattern)) {
        result.set(match[1], match[3]);
    }
    return result;
}

function parseCounter(attributesMap, className) {
    const type = attributesMap.get('type');
    const missedAttribute = attributesMap.get('missed') ?? '';
    const coveredAttribute = attributesMap.get('covered') ?? '';
    const nonNegativeInteger = /^(?:0|[1-9]\d*)$/;
    const missed = Number(missedAttribute);
    const covered = Number(coveredAttribute);
    if (type === undefined || !nonNegativeInteger.test(missedAttribute)
        || !nonNegativeInteger.test(coveredAttribute)
        || !Number.isSafeInteger(missed) || !Number.isSafeInteger(covered)) {
        failures.push(`Malformed direct class counter for ${className}`);
        return null;
    }
    return {type, missed, covered};
}

function parseClassCounters(xml) {
    const classes = new Map();
    const stack = [];
    let currentClass = null;
    const tagPattern = /<\s*(\/?)\s*([A-Za-z][A-Za-z0-9:_-]*)([^<>]*?)(\/?)\s*>/g;
    for (const match of xml.matchAll(tagPattern)) {
        const closing = match[1] === '/';
        const name = match[2];
        const selfClosing = match[4] === '/';
        if (closing) {
            if (name === 'class' && currentClass !== null) {
                if (classes.has(currentClass.name)) {
                    failures.push(`Duplicate JaCoCo class entry: ${currentClass.name}`);
                } else {
                    classes.set(currentClass.name, currentClass.counters);
                }
                currentClass = null;
            }
            const opened = stack.pop();
            if (opened !== name) {
                failures.push(`Malformed JaCoCo XML near closing <${name}>`);
                return classes;
            }
            continue;
        }

        const tagAttributes = attributes(match[3]);
        if (name === 'class') {
            const className = tagAttributes.get('name');
            if (className === undefined) {
                failures.push('JaCoCo class entry has no name');
            } else {
                currentClass = {name: className, counters: new Map()};
                if (selfClosing) {
                    if (classes.has(currentClass.name)) {
                        failures.push(`Duplicate JaCoCo class entry: ${currentClass.name}`);
                    } else {
                        classes.set(currentClass.name, currentClass.counters);
                    }
                    currentClass = null;
                }
            }
        } else if (name === 'counter' && currentClass !== null && stack.at(-1) === 'class') {
            const counter = parseCounter(tagAttributes, currentClass.name);
            if (counter !== null) {
                if (currentClass.counters.has(counter.type)) {
                    failures.push(`Duplicate direct ${counter.type} counter for ${currentClass.name}`);
                } else {
                    currentClass.counters.set(counter.type, counter);
                }
            }
        }

        if (!selfClosing) {
            stack.push(name);
        }
    }
    if (stack.length > 0) {
        failures.push('Malformed JaCoCo XML: unclosed elements remain');
    }
    return classes;
}

let result = null;
if (hasReportXml) {
    const allClasses = parseClassCounters(fs.readFileSync(reportXml, 'utf8'));
    const gatewayClasses = [...allClasses.entries()]
        .filter(([className]) => className.startsWith('com/paytech/gateway/'));
    if (gatewayClasses.length === 0) {
        failures.push('JaCoCo aggregate XML contains no paytech-api-gateway-app classes');
    }
    const compiledGatewayClasses = new Set(webClassFiles.map(file => path.relative(webClassesDirectory, file)
        .split(path.sep).join('/').replace(/\.class$/, '')));
    const reportedGatewayClasses = new Set(gatewayClasses.map(([className]) => className));
    const requiredGatewayClasses = webClassFiles.filter(file => {
        try {
            return requiresCoverageReport(fs.readFileSync(file));
        } catch (error) {
            failures.push(`Cannot inspect compiled class ${path.relative(repositoryRoot, file)}: ${error.message}`);
            return true;
        }
    }).map(file => path.relative(webClassesDirectory, file).split(path.sep).join('/').replace(/\.class$/, ''));
    const missingClasses = requiredGatewayClasses
        .filter(className => !reportedGatewayClasses.has(className)).sort();
    const unexpectedClasses = [...reportedGatewayClasses]
        .filter(className => !compiledGatewayClasses.has(className)).sort();
    if (missingClasses.length > 0) {
        failures.push(`JaCoCo aggregate XML omits compiled Gateway class: ${missingClasses[0]}`);
    }
    if (unexpectedClasses.length > 0) {
        failures.push(`JaCoCo aggregate XML contains non-compiled Gateway class: ${unexpectedClasses[0]}`);
    }
    const measuredClasses = gatewayClasses
        .filter(([className]) => !excludedClasses.has(className));
    if (measuredClasses.length === 0) {
        failures.push('JaCoCo aggregate XML contains no measured Gateway classes after exclusions');
    }

    const totals = {
        INSTRUCTION: {missed: 0, covered: 0},
        BRANCH: {missed: 0, covered: 0}
    };
    for (const [className, counters] of measuredClasses) {
        if (counters.size > 0 && !counters.has('INSTRUCTION')) {
            failures.push(`JaCoCo executable class ${className} has no direct INSTRUCTION counter`);
        }
        for (const type of Object.keys(totals)) {
            const counter = counters.get(type);
            if (counter === undefined) {
                continue;
            }
            totals[type].missed += counter.missed;
            totals[type].covered += counter.covered;
        }
    }

    const ratios = {};
    for (const [type, total] of Object.entries(totals)) {
        const denominator = total.missed + total.covered;
        if (denominator === 0) {
            failures.push(`${type.toLowerCase()} coverage total is zero`);
            ratios[type] = 0;
            continue;
        }
        ratios[type] = total.covered / denominator;
        if (ratios[type] < minimumRatio) {
            failures.push(`${type.toLowerCase()} coverage ${(ratios[type] * 100).toFixed(6)}% `
                + `is below ${(minimumRatio * 100).toFixed(0)}% (${total.covered}/${denominator})`);
        }
    }
    result = {gatewayClassCount: gatewayClasses.length, measuredClassCount: measuredClasses.length, totals, ratios};
}

if (failures.length > 0) {
    console.error('JaCoCo verification failed:');
    for (const failure of failures) {
        console.error(`- ${failure}`);
    }
    process.exit(1);
}

console.log(`JaCoCo verified for ${result.measuredClassCount}/${result.gatewayClassCount} Gateway classes: `
    + `instruction ${(result.ratios.INSTRUCTION * 100).toFixed(6)}% `
    + `(${result.totals.INSTRUCTION.covered}/`
    + `${result.totals.INSTRUCTION.covered + result.totals.INSTRUCTION.missed}), `
    + `branch ${(result.ratios.BRANCH * 100).toFixed(6)}% `
    + `(${result.totals.BRANCH.covered}/`
    + `${result.totals.BRANCH.covered + result.totals.BRANCH.missed}).`);
