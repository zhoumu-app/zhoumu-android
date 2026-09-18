#!/bin/bash
# 打一个签好名的 Release APK，放到 build/ 根目录方便上传。
#
# 用的签名密钥是仓库里公开的 keystore/zhoumu.jks（密码见 README）。
# 目的：让大家装到同一个签名的包，以后能直接覆盖升级。
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -d ".jdk" ]; then
  CANDIDATE=$(find .jdk -maxdepth 3 -name Home -type d 2>/dev/null | head -1)
  [ -n "$CANDIDATE" ] && export JAVA_HOME="$PWD/$CANDIDATE"
fi

GRADLE=${GRADLE:-gradle}
[ -x ./gradlew ] && GRADLE=./gradlew

echo "==> 跑测试"
"$GRADLE" --no-daemon --console=plain :app:testDebugUnitTest 2>&1 | tail -5

echo "==> 打 Release APK"
"$GRADLE" --no-daemon --console=plain :app:assembleRelease 2>&1 | tail -5

SRC=app/build/outputs/apk/release/app-release.apk
[ -f "$SRC" ] || { echo "没有产出：$SRC" >&2; exit 1; }

VERSION=$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | head -1 | cut -d'"' -f2)
OUT="build/ZhouMu-${VERSION}-android.apk"
cp "$SRC" "$OUT"

echo
echo "完成：${OUT}  ($(du -h "${OUT}" | cut -f1))"
echo "sha256: $(shasum -a 256 "${OUT}" | cut -d' ' -f1)"
