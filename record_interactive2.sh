#!/bin/bash
ADB="/home/lk1/android/sdk/platform-tools/adb"

echo "Starting screenrecord for 16 seconds..."
$ADB shell "screenrecord --size 1200x1920 --bit-rate 6000000 --time-limit 16 /sdcard/touchnotes_buttons_demo.mp4" &
REC_PID=$!

sleep 1.5

echo "Action 1: Tap thumbnail overview icon (x=120, y=140)..."
$ADB shell input tap 120 140
sleep 3

echo "Action 2: Tap page thumbnail (x=200, y=850)..."
$ADB shell input tap 200 850
sleep 2

echo "Action 3: Tap canvas to close thumbnail drawer (x=900, y=1000)..."
$ADB shell input tap 900 1000
sleep 2

echo "Action 4: Tap top-right Tools icon (x=1540, y=140)..."
$ADB shell input tap 1540 140
sleep 3

echo "Action 5: Tap canvas to close Tools panel (x=500, y=1000)..."
$ADB shell input tap 500 1000
sleep 1.5

echo "Action 6: Tap next page arrow on floating strip (x=1500, y=450)..."
$ADB shell input tap 1500 450
sleep 1.5

echo "Waiting for recording..."
wait $REC_PID
sleep 1

echo "Pulling video..."
$ADB pull /sdcard/touchnotes_buttons_demo.mp4 /home/lk1/craft_apps/simple_notes/touchnotes-clone/reference/screens/touchnotes_buttons_demo.mp4
ls -lh /home/lk1/craft_apps/simple_notes/touchnotes-clone/reference/screens/touchnotes_buttons_demo.mp4
