#!/usr/bin/env python3
"""Country flag upload STUB (dev machine only; never run against an environment without a release card).

Steps (stage 6 release item, see README.md "Flag logos"):
  1. download   fetch lipis/flag-icons@{FLAG_ICONS_VERSION} 4x3 SVGs for the 249 country codes into a new empty dir.
  2. upload     NOT IMPLEMENTED: for each SVG, call the Platform internal file API of the target environment
                (same production chain as FileServiceReleaseBundleStore, but over HTTP):
                  a. POST {platform}/internal/files/v1/uploads
                     header X-Caller-Service: <source system with a PUBLIC write route>
                     body {"reasonCode": "<approved reason, e.g. REF_COUNTRY_FLAG>", "filename": "<cc>.svg",
                           "mimeType": "image/svg+xml", "sizeBytes": "<n>", "accessLevel": "PUBLIC"}
                     -> 201 {uploadId, authorization{method=PUT, url, headers}, expiresAt}
                  b. PUT authorization.url with exactly authorization.headers and the SVG bytes
                  c. POST {platform}/internal/files/v1/uploads/{uploadId}/completion
                     -> {fileId, fileAddress=/files/v1/{fileId}, accessLevel=PUBLIC}
                  d. GET  {platform}/internal/files/v1/files/{fileId} until the scan passes and publicUrl is set
                Write "<CC>,<fileId>" lines to a mapping CSV.
  3. emit-sql   turn the mapping CSV into UPDATE statements for t_ref_country.logo_file_address.

Usage:
  /usr/bin/python3 -I upload_flags.py download <empty-dir>
  /usr/bin/python3 -I upload_flags.py emit-sql <mapping.csv> > flags-update.sql
"""
import csv
import os
import re
import sys
import urllib.request

FLAG_ICONS_VERSION = "7.5.0"
FLAG_URL = "https://cdn.jsdelivr.net/npm/flag-icons@{v}/flags/4x3/{cc}.svg"
HERE = os.path.dirname(os.path.abspath(__file__))
COUNTRY_SEED = os.path.join(HERE, "sources", "existing-country-region-seed.sql")


def country_codes():
    with open(COUNTRY_SEED, encoding="utf-8") as handle:
        codes = re.findall(r"^\s*\(\d+, '([A-Z]{2})'", handle.read(), re.M)
    if len(codes) != 249:
        sys.exit("expected 249 country codes, got %d" % len(codes))
    return codes


def download(target):
    os.makedirs(target, exist_ok=True)
    if os.listdir(target):
        sys.exit("target directory must be empty: " + target)
    missing = []
    for cc in country_codes():
        url = FLAG_URL.format(v=FLAG_ICONS_VERSION, cc=cc.lower())
        try:
            with urllib.request.urlopen(url, timeout=30) as response:
                body = response.read()
        except Exception:  # noqa: BLE001 - report and continue
            missing.append(cc)
            continue
        if not body.lstrip().startswith(b"<svg"):
            missing.append(cc)
            continue
        with open(os.path.join(target, cc + ".svg"), "wb") as handle:
            handle.write(body)
    print("downloaded %d, missing %s" % (249 - len(missing), missing))


def upload(*_):
    raise NotImplementedError("Upload is a stage-6 release step; see the module docstring and README.md.")


def emit_sql(mapping):
    known = set(country_codes())
    with open(mapping, encoding="utf-8", newline="") as handle:
        for row in csv.reader(handle):
            if not row or row[0].startswith("#"):
                continue
            cc, file_id = row[0].strip(), row[1].strip()
            if cc not in known or not re.fullmatch(r"[0-9]{1,19}", file_id):
                sys.exit("bad mapping row: %r" % row)
            print("UPDATE t_ref_country SET logo_file_address = '/files/v1/{}', version = version + 1, "
                  "update_time = UTC_TIMESTAMP(6) WHERE country_code = '{}';".format(file_id, cc))


def main(argv):
    if len(argv) != 3 or argv[1] not in ("download", "upload", "emit-sql"):
        sys.exit(__doc__)
    {"download": download, "upload": upload, "emit-sql": emit_sql}[argv[1]](argv[2])


if __name__ == "__main__":
    main(sys.argv)
