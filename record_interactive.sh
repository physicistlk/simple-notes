#!/bin/bash
ADB="/home/lk1/android/sdk/platform-tools/adb"

echo "Starting screenrecord for 16 seconds..."
$ADB shell "screenrecord --size 1200x1920 --bit-rate 6000000 --time-limit 16 /sdcard/touchnotes_interactive.mp4" &
REC_PID=$!

sleep 1.5

echo "Action 1: Tap thumbnail/overview icon (x=105, y=70)..."
$ADB shell input tap 105 70
sleep 3

echo "Action 2: Tap page 6 thumbnail in sidebar (x=200, y=850)..."
$ADB shell input tap 200 850
sleep 2.5

echo "Action 3: Tap canvas to dismiss sidebar (x=900, y=1000)..."
$ADB shell input tap 900 1000
sleep 2

echo "Action 4: Tap top-right Tools/Gear icon (x=1450, y=70)..."
$ADB shell input tap 1450 70
sleep 3.5

echo "Action 5: Tap canvas to dismiss tools panel (x=400, y=1000)..."
$ADB shell input tap 400 1000
sleep 1.5

echo "Waiting for screenrecord to complete..."
wait $REC_PID
sleep 1

echo "Pulling video..."
$ADB pull /sdcard/touchnotes_interactive.mp4 /home/lk1/craft_apps/simple_notes/touchnotes-clone/reference/screens/touchnotes_interactive.mp4
ls -lh /home/lk1/craft_apps/simple_notes/touchnotes-clone/reference/screens/touchnotes_interactive.mp4
