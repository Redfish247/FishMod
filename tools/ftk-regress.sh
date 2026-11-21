#!/bin/bash
# Runs FishTestKit suites against THIS checkout's built jars (26.1.2 + 26.2 in parallel), then scans logs.
# Usage: FT_ONLY=sb,sb2,ui bash tools/ftk-regress.sh [versions...]   (build first)
REPO="$(cd "$(dirname "$0")/.." && pwd -W 2>/dev/null || pwd)"
KIT=/d/FishTestKit
vs=("$@"); [ ${#vs[@]} -eq 0 ] && vs=(26.1.2 26.2)
cd "$KIT" || exit 1
for v in "${vs[@]}"; do
	jar=$(ls -t "$REPO"/dungeons-$v/build/libs/FishModDungeons-*-$v.jar | grep -v sources | head -1)
	echo "== $v jar: $jar"
	R="build/$v/run"; rm -rf "$R/screenshots" "$R/ftk-results.txt"
	( ./gradlew -Pmc=$v -Pfmd_jar="$jar" --project-cache-dir "build/gc-$v" runClientGameTest --console=plain > "build/gt-$v.log" 2>&1; echo $? > "build/exit-$v" ) &
done
wait
for v in "${vs[@]}"; do
	R="build/$v/run"
	echo "== $v: exit $(cat build/exit-$v)"
	cp "$R/ftk-results.txt" "build/results-$v.txt" 2>/dev/null && sed "s/^/  /" "build/results-$v.txt"
	rm -rf "build/shots/$v"; mkdir -p build/shots; cp -r "$R/screenshots" "build/shots/$v" 2>/dev/null
	echo "  -- FishMod errors in log:"
	grep -E 'FishDiag|Exception|Mixin apply failed' "build/gt-$v.log" | grep -iE 'fishmod|mixin' | sort | uniq -c | sort -rn | head -15
done
