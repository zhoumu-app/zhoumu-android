#!/bin/bash
# Android 版校验：核心逻辑单元测试 + 编译检查。
#
# 需要 JDK 17+ 和 Android SDK。SDK 路径从 local.properties 读，
# 没有的话就只跑得动单元测试（那部分不依赖 Android SDK）。
set -euo pipefail
cd "$(dirname "$0")/.."

# 优先用仓库自带的 JDK 21（.jdk/），免得系统 JDK 版本太新跑不动 Gradle
if [ -d ".jdk" ]; then
  CANDIDATE=$(find .jdk -maxdepth 3 -name Home -type d 2>/dev/null | head -1)
  [ -n "$CANDIDATE" ] && export JAVA_HOME="$PWD/$CANDIDATE"
fi
echo "  JAVA_HOME=${JAVA_HOME:-（用系统默认）}"

if command -v java >/dev/null 2>&1 || [ -n "${JAVA_HOME:-}" ]; then
  "${JAVA_HOME:-/usr}/bin/java" -version 2>&1 | head -1 | sed 's/^/  /'
fi

GRADLE=${GRADLE:-gradle}
if ! command -v "$GRADLE" >/dev/null 2>&1 && [ -x ./gradlew ]; then
  GRADLE=./gradlew
fi

if ! command -v "$GRADLE" >/dev/null 2>&1 && [ ! -x ./gradlew ]; then
  echo "找不到 gradle，也没生成 gradlew。" >&2
  echo "装一个：brew install gradle" >&2
  exit 1
fi

echo
echo "==> 1/2 核心逻辑单元测试"
"$GRADLE" --no-daemon --console=plain :app:test 2>&1 | tail -20

echo
echo "==> 2/2 编译 Debug APK"
if [ -f local.properties ]; then
  "$GRADLE" --no-daemon --console=plain :app:assembleDebug 2>&1 | tail -15
  APK=$(find app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -1)
  [ -n "$APK" ] && echo "APK: $APK（$(du -h "$APK" | cut -f1)）"
else
  echo "  没有 local.properties，跳过编译（只跑了单元测试）"
fi

echo
echo "==> 完成"
