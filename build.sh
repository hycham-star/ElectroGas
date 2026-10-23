#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
TOOLS="${ELECTROGAS_TOOLS:-/workspace/android-tools}"
BT="$TOOLS/build-tools/android-15"
ANDROID="$TOOLS/platform/android-35/android.jar"
if [[ ! -f "$ANDROID" || ! -f "$TOOLS/ecj.jar" ]]; then
    python3 tools/bootstrap.py
fi
mkdir -p build/classes build/test-classes build/dex dist
java -jar "$TOOLS/ecj.jar" -17 -encoding UTF-8 -proc:none -d build/test-classes app/src/main/java/dz/electrogas/Calculator.java tests/CalculatorTest.java
java -cp build/test-classes dz.electrogas.CalculatorTest
java -jar "$TOOLS/ecj.jar" -8 -encoding UTF-8 -proc:none -classpath "$ANDROID" -d build/classes app/src/main/java/dz/electrogas/*.java
chmod +x "$BT/aapt2" "$BT/zipalign"
"$BT/aapt2" compile --dir app/src/main/res -o build/resources.zip
"$BT/aapt2" link -o build/base.apk --manifest app/src/main/AndroidManifest.xml -I "$ANDROID" build/resources.zip
find build/classes -name '*.class' -print | LC_ALL=C sort > build/classes.list
mapfile -t CLASSES < build/classes.list
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --lib "$ANDROID" --min-api 23 --output build/dex "${CLASSES[@]}"
python3 - <<'PY'
import zipfile
from pathlib import Path
with zipfile.ZipFile('build/base.apk','a',zipfile.ZIP_DEFLATED) as z:
    for p in Path('build/dex').glob('*.dex'):
        z.write(p,p.name)
PY
"$BT/zipalign" -f -p 4 build/base.apk build/aligned.apk
# Local development signing only; this key is neither embedded in source nor a production identity.
KEY="${ELECTROGAS_KEYSTORE:-$TOOLS/electrogas-development.jks}"
if [[ ! -f "$KEY" ]]; then
    keytool -genkeypair -keystore "$KEY" -storepass android -keypass android -alias electrogas -dname 'CN=ElectroGas Development' -keyalg RSA -keysize 2048 -validity 10000 -noprompt
fi
java -jar "$BT/lib/apksigner.jar" sign --ks "$KEY" --ks-key-alias electrogas --ks-pass pass:android --key-pass pass:android --out dist/ElectroGas.apk build/aligned.apk
java -jar "$BT/lib/apksigner.jar" verify --verbose --print-certs dist/ElectroGas.apk
"$BT/zipalign" -c -v 4 dist/ElectroGas.apk
sha256sum dist/ElectroGas.apk > dist/ElectroGas.apk.sha256
printf 'Built: %s/dist/ElectroGas.apk\n' "$PWD"
