#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
TOOLS="${ELECTROGAS_TOOLS:-/workspace/android-tools}"
if [[ ! -f "$TOOLS/platform/android-35/android.jar" || ! -f "$TOOLS/ecj.jar" ]]; then
    python3 tools/bootstrap.py
fi
BT="$TOOLS/build-tools/android-15"
mkdir -p "$TOOLS/sdk/platforms" "$TOOLS/sdk/build-tools" build/test-classes dist
ln -sfn "$TOOLS/platform/android-35" "$TOOLS/sdk/platforms/android-35"
ln -sfn "$BT" "$TOOLS/sdk/build-tools/35.0.0"
export ANDROID_HOME="${ANDROID_HOME:-$TOOLS/sdk}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$TOOLS/user}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$TOOLS/gradle-cache}"
mkdir -p "$ANDROID_USER_HOME"
java -jar "$TOOLS/ecj.jar" -17 -encoding UTF-8 -proc:none -d build/test-classes app/src/main/java/dz/electrogas/Calculator.java tests/CalculatorTest.java
java -cp build/test-classes dz.electrogas.CalculatorTest
./gradlew :app:assembleRelease "$@"
rm -rf build/classes
mkdir -p build/classes
cp -r app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes/. build/classes/
KEY="${ELECTROGAS_KEYSTORE:-$TOOLS/electrogas-development.jks}"
if [[ ! -f "$KEY" ]]; then
    keytool -genkeypair -keystore "$KEY" -storepass android -keypass android -alias electrogas -dname 'CN=ElectroGas Development' -keyalg RSA -keysize 2048 -validity 10000 -noprompt
fi
java -jar "$BT/lib/apksigner.jar" sign --ks "$KEY" --ks-key-alias electrogas --ks-pass pass:android --key-pass pass:android --out dist/ElectroGas.apk app/build/outputs/apk/release/app-release-unsigned.apk
java -jar "$BT/lib/apksigner.jar" verify --verbose --print-certs dist/ElectroGas.apk
"$BT/zipalign" -c 4 dist/ElectroGas.apk
sha256sum dist/ElectroGas.apk > dist/ElectroGas.apk.sha256
printf 'Built: %s/dist/ElectroGas.apk\n' "$PWD"
