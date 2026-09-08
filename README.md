# AirHand 0.1

Android prototype that turns front-camera hand tracking into a system-wide accessibility cursor.

## Build status
This source has been reviewed and fixed (drag support, Back/Home gestures, error handling,
missing permissions, missing Gradle wrapper properties) but has **not been compiled**, since
that requires the Android SDK, a JDK, and network access to Google's Maven repo — none of
which are available in the environment this fix was produced in. Build it yourself with
Android Studio (see Setup below); this is also the only way to get a signed APK you can
install without disabling Play Protect warnings for an unsigned/unknown build.

## Current gestures
- Point with index finger: move cursor
- Thumb/index pinch (quick): tap
- Thumb/index pinch (hold ~0.2s): drag, release to drop
- Index + middle fingers raised, move vertically: scroll
- Fist held ~0.5s: Back
- Thumbs-up held ~0.5s: Home
- Open palm: pause/resume cursor and gestures

## Setup
1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Run on a real Android phone (recommended over emulator).
4. Grant Camera permission.
5. Tap **Enable Accessibility** and enable **AirHand**.
6. Return to AirHand and tap **Start Hand Control**.
7. Switch to another app while keeping your hand in view of the front camera.

## Important Android behavior
AirHand starts the camera foreground service only while the activity is visible. Once started, the foreground service can continue using the camera while you switch apps. Android displays a persistent notification while camera tracking is active.

## Architecture
CameraX ImageAnalysis -> MediaPipe HandLandmarker -> GestureEngine -> in-process GestureBus -> AccessibilityService -> TYPE_ACCESSIBILITY_OVERLAY + dispatchGesture().

## Prototype limitations
- Single display coordinate mapping is used. Samsung DeX/multi-display support needs a display-aware overlay and coordinate mapper.
- Pinch currently produces tap, not drag.
- Scrolling is discrete rather than continuous.
- No gesture keyboard yet.
- The threshold values need calibration for distance, lighting and hand size.

## Model
The model is downloaded automatically during Gradle `preBuild` from Google's published MediaPipe model URL into `app/src/main/assets/hand_landmarker.task`. If your network blocks that download, open the URL below in a browser and save the file manually at that path:

https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task
