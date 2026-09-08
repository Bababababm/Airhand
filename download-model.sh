#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/src/main/assets
curl -L --fail \
  'https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task' \
  -o app/src/main/assets/hand_landmarker.task
echo 'Model saved to app/src/main/assets/hand_landmarker.task'
