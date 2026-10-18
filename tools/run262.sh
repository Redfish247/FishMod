#!/bin/bash
# Builds the 26.2 jar, installs it into the FishTest-26.2 instance and launches the flat singleplayer test world.
# Never touches Skyblock; refuses when FishTest-26.2 already runs.
set -e
cd "$(dirname "$0")/.."
I="$APPDATA/PrismLauncher/instances/FishTest-26.2/minecraft"
L="$I/logs/latest.log"
if powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { \$_.Name -eq 'javaw.exe' -and \$_.CommandLine -like '*FishTest-26.2*' } | Measure-Object | ForEach-Object { exit [int]\$_.Count }"; then :; else echo "FishTest-26.2 already running"; exit 1; fi
./gradlew :dungeons-26.2:build --console=plain -q 2>&1 | grep -E "^e: |error:|went wrong|FAILED" | head -20 && true
VER=$(grep '^mod_version=' dungeons-26.2/gradle.properties | cut -d= -f2)
rm -f "$I"/mods/FishModDungeons-*.jar
cp "dungeons-26.2/build/libs/FishModDungeons-$VER.jar" "$I/mods/"
rm -f "$L"
powershell -NoProfile -Command "Start-Process -FilePath \"\$env:LOCALAPPDATA\\Programs\\PrismLauncher\\prismlauncher.exe\" -ArgumentList '-l','FishTest-26.2','-w','FishTestFlat'"
echo "launched $VER"
