#!/usr/bin/env bash
# Prueba de humo: instala el APK de prueba, abre la app y verifica que siga viva.
set -u
API="$1"
PKG="com.warriorsbox.app.debug"
mkdir -p smoke
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -W -n "$PKG/com.warriorsbox.app.MainActivity"
sleep 15
adb exec-out screencap -p > "smoke/inicio-api-$API.png"
# Tocar un botón buscando su texto en la jerarquía de accesibilidad.
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml smoke/ui.xml > /dev/null 2>&1
  python3 - "$1" <<'PY'
import re, sys, subprocess
xml = open("smoke/ui.xml", encoding="utf-8").read()
for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    if sys.argv[1].lower() in m.group(1).lower():
        x = (int(m.group(2)) + int(m.group(4))) // 2
        y = (int(m.group(3)) + int(m.group(5))) // 2
        subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)])
        print("tap", m.group(1), x, y)
        break
else:
    print("no encontrado:", sys.argv[1])
PY
}
tap_text "USUARIOS"; sleep 4; adb exec-out screencap -p > "smoke/usuarios-api-$API.png"
tap_text "Soy coach"; sleep 4; adb exec-out screencap -p > "smoke/coach-api-$API.png"
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 2
tap_text "RUTINAS"; sleep 4; adb exec-out screencap -p > "smoke/rutinas-api-$API.png"
adb shell input keyevent KEYCODE_BACK; sleep 2
adb logcat -d > "smoke/logcat-api-$API.txt"
if grep -q "FATAL EXCEPTION" "smoke/logcat-api-$API.txt"; then
  echo "::error::La app se cerró en API $API"; grep -A 30 "FATAL EXCEPTION" "smoke/logcat-api-$API.txt"; exit 1
fi
if [ -z "$(adb shell pidof "$PKG")" ]; then
  echo "::error::La app no está corriendo en API $API"; exit 1
fi
echo "OK en API $API"
