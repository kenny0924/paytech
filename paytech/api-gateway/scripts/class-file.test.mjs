import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {classAccessFlags, requiresCoverageReport} from './class-file.mjs';

test('real javac enum helper is synthetic, but an ordinary $1 anonymous class still requires coverage', () => {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'gateway-class-flags-'));
    try {
        fs.writeFileSync(path.join(directory, 'Example.java'), `
            class Example {
                int choose(java.time.DayOfWeek choice) { switch (choice) { case MONDAY: return 1; default: return 2; } }
            }
            class Anonymous { Runnable task = new Runnable() { public void run() {} }; }
        `);
        execFileSync(process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin/javac') : 'javac',
            ['-d', directory, path.join(directory, 'Example.java')]);
        assert.equal(requiresCoverageReport(fs.readFileSync(path.join(directory, 'Example$1.class'))), false);
        assert.equal(requiresCoverageReport(fs.readFileSync(path.join(directory, 'Anonymous$1.class'))), true);
        assert.equal(requiresCoverageReport(fs.readFileSync(path.join(directory, 'Example.class'))), true);
    } finally {
        fs.rmSync(directory, {recursive: true, force: true});
    }
});

function envelope(pool, count, flags = 0x21) {
    const header = Buffer.from('cafebabe000000450000', 'hex');
    header.writeUInt16BE(count, 8);
    const access = Buffer.alloc(2);
    access.writeUInt16BE(flags);
    return Buffer.concat([header, ...pool, access]);
}

test('walks all JVM constant kinds including two-slot constants and dynamic constants', () => {
    const pool = [Buffer.from([1, 0, 3, 65, 66, 67])];
    for (const tag of [3, 4, 9, 10, 11, 12, 17, 18]) pool.push(Buffer.from([tag, 0, 0, 0, 0]));
    for (const tag of [5, 6]) pool.push(Buffer.from([tag, 0, 0, 0, 0, 0, 0, 0, 0]));
    for (const tag of [7, 8, 16, 19, 20]) pool.push(Buffer.from([tag, 0, 0]));
    pool.push(Buffer.from([15, 0, 0, 0]));
    assert.equal(classAccessFlags(envelope(pool, pool.length + 3)), 0x21);
    assert.equal(requiresCoverageReport(envelope([], 1, 0x8000)), false);
});

test('unreadable class metadata fails closed instead of excluding a class', () => {
    for (const data of [Buffer.alloc(0), Buffer.alloc(12), envelope([], 0),
        envelope([Buffer.from([99])], 2), envelope([Buffer.from([5, 0, 0, 0, 0, 0, 0, 0, 0])], 2),
        envelope([Buffer.from([1, 0, 20])], 2), envelope([], 1).subarray(0, 11)]) {
        assert.throws(() => requiresCoverageReport(data));
    }
});
