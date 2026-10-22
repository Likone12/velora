#!/bin/sh
# Builds MasinaMea.apk from ../masina.html with the Ubuntu Android packages:
#   apt-get install aapt apksigner android-sdk-platform-23 dalvik-exchange zipalign
# Needs the private signing key masina-mea.keystore next to this script (not in git).
set -e
cd "$(dirname "$0")"
SDK=/usr/lib/android-sdk
ANDROID_JAR=$SDK/platforms/android-23/android.jar
OUT=build
rm -rf $OUT && mkdir -p $OUT/gen $OUT/classes $OUT/assets

cp ../masina.html $OUT/assets/
# Optional, git-ignored preset.js pre-fills the car's details in this build only.
if [ -f preset.js ]; then
  python3 - preset.js $OUT/assets/masina.html <<'PY'
import sys
preset = open(sys.argv[1], encoding='utf-8').read()
page = open(sys.argv[2], encoding='utf-8').read()
open(sys.argv[2], 'w', encoding='utf-8').write(page.replace('<script>', '<script>\n' + preset + '\n</script>\n<script>', 1))
PY
fi

aapt package -f -m -J $OUT/gen -M AndroidManifest.xml -S res -I $ANDROID_JAR
javac --release 8 -nowarn -Xlint:-options -d $OUT/classes -classpath $ANDROID_JAR \
  $OUT/gen/ro/masinamea/app/R.java src/ro/masinamea/app/*.java
dalvik-exchange --dex --min-sdk-version=21 --output=$OUT/classes.dex $OUT/classes

aapt package -f -0 arsc -M AndroidManifest.xml -S res -A $OUT/assets -I $ANDROID_JAR -F $OUT/unsigned.apk
(cd $OUT && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk
apksigner sign --ks masina-mea.keystore --ks-pass pass:masinamea --key-pass pass:masinamea \
  --out MasinaMea.apk $OUT/aligned.apk
apksigner verify MasinaMea.apk
echo "OK: $(pwd)/MasinaMea.apk"
