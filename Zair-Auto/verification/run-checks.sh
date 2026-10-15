#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -d "$tmp_dir" \
  fermata/src/main/java/me/aap/fermata/util/ZairPlaybackGuard.java \
  verification/ZairPlaybackGuardTest.java \
  fermata/src/main/java/me/aap/fermata/util/ZairCoverGeometry.java \
  verification/ZairCoverGeometryTest.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$tmp_dir" ZairPlaybackGuardTest
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$tmp_dir" ZairCoverGeometryTest
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
for root in ('fermata/src', 'depends/utils/src', 'modules'):
    paths=[p for p in Path(root).rglob('*.xml') if 'build' not in p.parts]
    for path in paths: ET.parse(path)
    print(f'{root}: {len(paths)} XML files parsed')
en=ET.parse('fermata/src/main/res/values/zair_strings.xml').getroot()
es=ET.parse('fermata/src/main/res/values-es/zair_strings.xml').getroot()
assert {e.attrib['name'] for e in en} == {e.attrib['name'] for e in es}
print('English/Spanish string keys match')
PY
