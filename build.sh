#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK:-/root/android}"
BT="$SDK/bt/android-14"
JAR="$SDK/platform/android-34-ext12/android.jar"
AAPT2="/usr/bin/aapt2"
KEYSTORE="$ROOT/tools/waterline.keystore"
OUT="$ROOT/dist/流水入杯-正式版-v1.0.40.apk"

cd "$ROOT"
rm -rf build dist
mkdir -p build/gen build/classes build/dex build/assets dist android/assets

echo "==> package source into assets/source.zip"
python3 - <<'PY'
import os, zipfile
root='.'
out='android/assets/source.zip'
incl=['android/src','android/res','android/AndroidManifest.xml','build.sh','README.md',
      'test','tools/package_apk.py']
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
    for item in incl:
        if os.path.isfile(item):
            z.write(item,'source/'+item.replace(os.sep,'/'))
        elif os.path.isdir(item):
            for dp,dn,fn in os.walk(item):
                dn[:]=[d for d in dn if d not in ('__pycache__','.git')]
                for f in fn:
                    p=os.path.join(dp,f)
                    z.write(p,'source/'+p.replace(os.sep,'/'))
print('source.zip', os.path.getsize(out), 'bytes')
PY

echo "==> compile resources"
(cd android && "$AAPT2" compile --dir res -o ../build/res.zip)

echo "==> link resources + assets"
(cd android && "$AAPT2" link -o ../build/app.apk -I "$JAR" \
  --manifest AndroidManifest.xml -A "$ROOT/android/assets" \
  --java ../build/gen --min-sdk-version 24 --target-sdk-version 34 ../build/res.zip)

echo "==> javac"
javac --release 11 -encoding UTF-8 -cp "$JAR" -d build/classes \
  $(find android/src build/gen -name '*.java')

echo "==> d8"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --min-api 24 --lib "$JAR" \
  --output build/dex $(find build/classes -name '*.class')

echo "==> package + align"
python3 tools/package_apk.py build/app.apk build/dex/classes.dex build/unsigned.apk

if [ ! -f "$KEYSTORE" ]; then
  echo "==> generate signing key"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass waterline -keypass waterline \
    -alias waterline -dname "CN=Waterline,O=DSH,C=CN" -keyalg RSA -keysize 2048 \
    -validity 20000 >/dev/null 2>&1
fi

echo "==> sign"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:waterline --key-pass pass:waterline \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" build/unsigned.apk

echo "==> verify"
"$BT/apksigner" verify --print-certs "$OUT" | head -6
ls -lh "$OUT"
