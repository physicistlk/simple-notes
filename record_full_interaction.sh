#!/bin/bash
ADB="/home/lk1/android/sdk/platform-tools/adb"
VIDEO_PATH="/home/lk1/craft_apps/simple_notes/touchnotes-clone/reference/screens/touchnotes_button_clicks.mp4"

echo "Starting 20-second recording on tablet..."
$ADB shell "screenrecord --size 1200x1920 --bit-rate 6000000 --time-limit 20 /sdcard/touchnotes_button_clicks.mp4" &
REC_PID=$!

sleep 2

# Currently page thumbnail sidebar is open.
echo "1. Tapping Page 2 thumbnail (x=233, y=802)..."
$ADB shell input tap 233 802
sleep 2.5

echo "2. Tapping open_preview_left (x=120, y=100) to toggle close sidebar..."
$ADB shell input tap 120 100
sleep 2

echo "3. Tapping more_function / Tools menu (x=1550, y=100)..."
$ADB shell input tap 1550 100
sleep 3

echo "4. Tapping Export tab (x=830, y=130)..."
$ADB shell input tap 830 130
sleep 2.5

echo "5. Tapping Settings tab (x=930, y=130)..."
$ADB shell input tap 930 130
sleep 2.5

echo "6. Tapping canvas (x=400, y=1000) to dismiss right panel..."
$ADB shell input tap 400 1000
sleep 2

echo "7. Tapping Next Page button (x=1488, y=476)..."
$ADB shell input tap 1488 476
sleep 2

echo "8. Tapping Prev Page button (x=1488, y=340)..."
$ADB shell input tap 1488 340
sleep 1.5

echo "Waiting for video recording to complete..."
wait $REC_PID
sleep 1

echo "Pulling video..."
$ADB pull /sdcard/touchnotes_button_clicks.mp4 "$VIDEO_PATH"
ls -lh "$VIDEO_PATH"
