"""Statically verify migration shape and deterministic generation; never execute SQL."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import generate_migrations as generator


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify():
    here = Path(__file__).resolve().parent
    artifacts = [here / name for name in (
        "client-contract-empty-closure.sql", "risk-compliance-empty-closure.sql",
        "platform-support-additions.sql", "migration-manifest.json")]
    before = {p.name: digest(p) for p in artifacts}
    outputs = generator.generate(write=False)
    assert all(path.read_text() == outputs[path.name] for path in artifacts), 'Generated migration drift'
    assert before == {p.name: digest(p) for p in artifacts}, "Generated migration drift"
    manifest = json.loads((here / "migration-manifest.json").read_text())
    checked = {}
    for owner, repo, stem in [("CCT", "client-contract-service", "client-contract"),
                              ("Risk", "risk-compliance-service", "risk-compliance")]:
        old = json.loads(subprocess.check_output(["git", "-C", str(generator.ROOT / repo),
            "show", f"HEAD:docs/database/{stem}.pdma.json"]))
        source = {e["defKey"]: e for e in old["entities"]}
        target = generator.create_tables((generator.ROOT / repo / f"docs/database/sql/{stem}-schema.sql").read_text())
        ddl = (here / f"{stem}-empty-closure.sql").read_text()
        altered = dict(re.findall(r"ALTER TABLE `(\w+)`\n([\s\S]*?);(?:\n|$)", ddl))
        created = generator.create_tables(ddl)
        expected = manifest["systems"][owner]["tables"]
        assert set(altered) | set(created) == set(expected)
        assert not set(altered) & set(created)
        for name in expected:
            if name in created:
                assert created[name][0] == target[name][0], name
                continue
            columns = [f["defKey"] for f in source[name]["fields"]]
            primary = [f["defKey"] for f in source[name]["fields"] if f["primaryKey"]]
            indexes = {i["defKey"]: None for i in source[name]["indexes"]}
            definitions = {}
            comment = None
            for raw in altered[name].splitlines():
                clause = raw.strip().removesuffix(",")
                if clause.startswith("DROP INDEX"):
                    index = re.fullmatch(r"DROP INDEX `(\w+)`", clause)[1]
                    assert index in indexes, (name, index)
                    del indexes[index]
                elif clause.startswith("DROP COLUMN"):
                    column = re.fullmatch(r"DROP COLUMN `(\w+)`", clause)[1]
                    assert column not in primary, (name, column)
                    columns.remove(column)
                elif clause.startswith(("MODIFY COLUMN", "ADD COLUMN", "CHANGE COLUMN")):
                    match = re.fullmatch(r"(MODIFY|ADD|CHANGE) COLUMN `(\w+)`(?: `(\w+)`)? (.+) (FIRST|AFTER `(\w+)`)", clause)
                    assert match, (name, clause)
                    action, column, renamed, definition, position, predecessor = match.groups()
                    if action == "ADD":
                        assert column not in columns, (name, column)
                    else:
                        columns.remove(column)
                    if renamed:
                        primary = [renamed if k == column else k for k in primary]
                        column = renamed
                        assert column not in columns
                    offset = 0 if position == "FIRST" else columns.index(predecessor) + 1
                    columns.insert(offset, column)
                    definitions[column] = definition
                elif clause.startswith("ADD "):
                    definition = clause[4:]
                    index = re.match(r"(?:UNIQUE KEY|KEY) `(\w+)`", definition)[1]
                    assert index not in indexes, (name, index)
                    indexes[index] = definition
                elif clause.startswith("COMMENT = "):
                    comment = clause[len("COMMENT = "):]
                else:
                    raise AssertionError((name, clause))
            expected_columns = re.findall(r"^  `(\w+)` (.*?)(?:,)?$", target[name][2], re.M)
            expected_indexes = re.findall(r"^  ((?:UNIQUE KEY|KEY) `(\w+)` [^\n]*?)(?:,)?$", target[name][2], re.M)
            expected_primary = re.search(r"PRIMARY KEY \(([^)]+)\)", target[name][2])[1]
            assert columns == [c for c, _ in expected_columns], name
            assert [(c, definitions[c]) for c in columns] == expected_columns, name
            assert indexes == {key: definition for definition, key in expected_indexes}, name
            assert primary == re.findall(r"`(\w+)`", expected_primary), name
            assert comment == re.search(r"COMMENT=('(?:[^']|'')*')$", target[name][3])[1], name
        checked[owner] = {"alterTables": len(altered), "createTables": len(created)}
    platform = generator.create_tables((generator.ROOT / "platform-support-service/docs/database/sql/platform-support-schema.sql").read_text())
    additions = generator.create_tables((here / "platform-support-additions.sql").read_text())
    assert len(additions) == 7
    for name, statement in additions.items():
        assert statement[0] == platform[name][0], name
    checked["Platform"] = {"createTables": len(additions)}
    return {"scope": "static SQL shape and repeatability only; no database execution", "owners": checked, "sha256": before}


if __name__ == "__main__":
    print(json.dumps(verify(), indent=2))
