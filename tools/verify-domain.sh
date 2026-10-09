#!/usr/bin/env bash
# Offline verification of :core:domain without network access or the Android SDK.
#
# It uses the Kotlin compiler, kotlinx libraries and JUnit that ship inside a local
# Gradle 8.14.x distribution, compiles main + test sources, and runs every *Test class
# with JUnit 4. On a normal networked machine prefer:  ./gradlew :core:domain:test
#
# Usage: tools/verify-domain.sh [path-to-gradle-lib-dir]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIB="${1:-${GRADLE_LIB:-/opt/gradle/lib}}"
OUT="$ROOT/build-offline/domain"
MOD="$ROOT/core/domain/src"

need() { ls "$LIB"/$1 >/dev/null 2>&1 || { echo "missing $1 in $LIB" >&2; exit 2; }; }
for j in 'kotlin-compiler-embeddable-*.jar' 'kotlin-stdlib-*.jar' 'kotlinx-coroutines-core-jvm-*.jar' \
         'kotlinx-serialization-json-jvm-*.jar' 'kotlinx-serialization-core-jvm-*.jar' 'junit-4*.jar'; do need "$j"; done

j() { ls "$LIB"/$1 | head -1; }
STDLIB="$(j 'kotlin-stdlib-[0-9]*.jar')"
COMPILER_CP="$(j 'kotlin-compiler-embeddable-*.jar'):$STDLIB:$(j 'kotlin-script-runtime-*.jar'):$(j 'kotlin-reflect-*.jar'):$(j 'kotlin-daemon-embeddable-*.jar'):$(j 'trove4j-*.jar'):$(j 'annotations-*.jar'):$(j 'kotlinx-coroutines-core-jvm-*.jar')"
LIBS="$STDLIB:$(j 'kotlinx-coroutines-core-jvm-*.jar'):$(j 'kotlinx-serialization-core-jvm-*.jar'):$(j 'kotlinx-serialization-json-jvm-*.jar')"
TESTLIBS="$(j 'junit-4*.jar'):$(j 'hamcrest-core-*.jar')"

kotlinc() { java -Xss4m -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -Werror "$@" 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS' || true; }

rm -rf "$OUT" && mkdir -p "$OUT/main" "$OUT/test"
echo "== compiling main"
kotlinc -cp "$LIBS" -d "$OUT/main" $(find "$MOD/main/kotlin" -name '*.kt') | tee "$OUT/main.log"
[ -d "$MOD/main/resources" ] && cp -r "$MOD/main/resources/." "$OUT/main/"
ls "$OUT/main/app" >/dev/null 2>&1 || { echo "main compilation failed"; exit 1; }
grep -q 'error:' "$OUT/main.log" && { echo "main compilation failed"; exit 1; }

echo "== compiling tests"
kotlinc -cp "$LIBS:$TESTLIBS:$OUT/main" -d "$OUT/test" $(find "$MOD/test/kotlin" -name '*.kt') | tee "$OUT/test.log"
grep -q 'error:' "$OUT/test.log" && { echo "test compilation failed"; exit 1; }
[ -d "$MOD/test/resources" ] && cp -r "$MOD/test/resources/." "$OUT/test/"

CLASSES=$(cd "$OUT/test" && find . -name '*Test.class' ! -name '*$*' | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort)
echo "== running $(echo "$CLASSES" | wc -l) test classes"
java -cp "$OUT/test:$OUT/main:$LIBS:$TESTLIBS" org.junit.runner.JUnitCore $CLASSES 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS' | tee "$OUT/junit.log"
grep -q '^OK (' "$OUT/junit.log"
