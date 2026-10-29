#!/bin/bash
# Builds the 26.2 jar, installs it into the FishTest-26.2 instance and launches the flat singleplayer test world.
# --sim joins the local FishSim server (D:\FishSim, fake Hypixel islands) instead, starting it first if needed.
# Never touches Skyblock; refuses when FishTest-26.2 already runs.
set -e
cd "$(dirname "$0")/.."
I="$APPDATA/PrismLauncher/instances/FishTest-26.2/minecraft"
L="$I/logs/latest.log"
SIM_DIR="D:/FishSim/server"
if powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { \$_.Name -eq 'javaw.exe' -and \$_.CommandLine -like '*FishTest-26.2*' } | Measure-Object | ForEach-Object { exit [int]\$_.Count }"; then :; else echo "FishTest-26.2 already running"; exit 1; fi
if [ "$1" = "--sim" ]; then
    # Reuse a running FishSim server, otherwise start it hidden and wait for "Done"
    if netstat -an | grep -q ":25565 .*LISTENING"; then
        echo "FishSim server already up"
    else
        rm -f "$SIM_DIR/logs/latest.log"
        powershell -NoProfile -Command "Start-Process -WindowStyle Hidden -WorkingDirectory 'D:\FishSim\server' -FilePath java -ArgumentList '-Xms1G','-Xmx2G','-jar','paper.jar','--nogui'"
        for i in $(seq 1 90); do
            grep -q "Done (" "$SIM_DIR/logs/latest.log" 2>/dev/null && break
            sleep 2
        done
        grep -q "Done (" "$SIM_DIR/logs/latest.log" 2>/dev/null || { echo "FishSim server did not start, see $SIM_DIR/logs/latest.log"; exit 1; }
        echo "FishSim server started"
    fi
    JOIN="'-s','localhost:25565'"
else
    JOIN="'-w','FishTestFlat'"
fi
./gradlew :dungeons-26.2:build --console=plain -q 2>&1 | grep -E "^e: |error:|went wrong|FAILED" | head -20 && true
VER=$(grep '^mod_version=' dungeons-26.2/gradle.properties | cut -d= -f2)
rm -f "$I"/mods/FishModDungeons-*.jar
cp "dungeons-26.2/build/libs/FishModDungeons-$VER.jar" "$I/mods/"
rm -f "$L"
powershell -NoProfile -Command "Start-Process -FilePath \"\$env:LOCALAPPDATA\Programs\PrismLauncher\prismlauncher.exe\" -ArgumentList '-l','FishTest-26.2',$JOIN"
echo "launched $VER${1:+ ($1)}"
