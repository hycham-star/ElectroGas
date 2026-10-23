#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TOOLS="${ELECTROGAS_TOOLS:-/workspace/android-tools}"
BT="$TOOLS/build-tools/android-15"
ANDROID="$TOOLS/platform/android-35/android.jar"
ADB="${ELECTROGAS_ADB:-$TOOLS/runtime/platform-tools/adb}"
KEY="${ELECTROGAS_KEYSTORE:-$TOOLS/electrogas-development.jks}"
mkdir -p build/instrumentation-classes build/instrumentation-dex
java -jar "$TOOLS/ecj.jar" -8 -encoding UTF-8 -proc:none -classpath "$ANDROID:build/classes" -d build/instrumentation-classes tests/InvoiceTests.java
"$BT/aapt2" link -o build/instrumentation-base.apk --manifest tests/AndroidManifest.xml -I "$ANDROID"
mapfile -t TEST_CLASSES < <(find build/instrumentation-classes -name '*.class' -print | LC_ALL=C sort)
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --lib "$ANDROID" --classpath build/classes --min-api 23 --output build/instrumentation-dex "${TEST_CLASSES[@]}"
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/instrumentation-base.apk','a',zipfile.ZIP_DEFLATED) as z:
    z.write('build/instrumentation-dex/classes.dex','classes.dex')
PY
"$BT/zipalign" -f 4 build/instrumentation-base.apk build/instrumentation-aligned.apk
java -jar "$BT/lib/apksigner.jar" sign --ks "$KEY" --ks-key-alias electrogas --ks-pass pass:android --key-pass pass:android --out build/instrumentation.apk build/instrumentation-aligned.apk
"$ADB" install --no-streaming -r dist/ElectroGas.apk
"$ADB" install --no-streaming -r build/instrumentation.apk
"$ADB" shell am instrument -w dz.electrogas.tests/dz.electrogas.tests.InvoiceTests > build/invoice-tests.log
cat build/invoice-tests.log
python3 - <<'PY'
from pathlib import Path
s=Path('build/invoice-tests.log').read_text()
assert 'result=PASS:' in s and 'INSTRUMENTATION_CODE: -1' in s,s
PY
