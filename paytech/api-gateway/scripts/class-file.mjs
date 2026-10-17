/** Read class access flags without treating ordinary anonymous/nested classes as synthetic. */
export function classAccessFlags(bytes) {
    let offset = 0;
    const take = size => {
        if (offset + size > bytes.length) throw new Error('Truncated JVM class file');
        const start = offset;
        offset += size;
        return start;
    };
    if (bytes.readUInt32BE(take(4)) !== 0xCAFEBABE) throw new Error('Invalid JVM class magic');
    take(4); // Minor and major versions do not change the constant-pool layout.
    const count = bytes.readUInt16BE(take(2));
    if (count === 0) throw new Error('Invalid JVM constant-pool count');
    for (let index = 1; index < count; index++) {
        const tag = bytes.readUInt8(take(1));
        switch (tag) {
            case 1: take(bytes.readUInt16BE(take(2))); break;
            case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18: take(4); break;
            case 5: case 6:
                take(8);
                if (++index >= count) throw new Error('Invalid two-slot JVM constant');
                break;
            case 7: case 8: case 16: case 19: case 20: take(2); break;
            case 15: take(3); break;
            default: throw new Error(`Unknown JVM constant-pool tag: ${tag}`);
        }
    }
    return bytes.readUInt16BE(take(2));
}

/** Mirrors JaCoCo Analyzer: synthetic classes and module descriptors have no class report. */
export function requiresCoverageReport(bytes) {
    return (classAccessFlags(bytes) & (0x1000 | 0x8000)) === 0;
}
