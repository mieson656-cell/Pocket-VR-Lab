# Pocket VR Lab

Pocket VR Lab is a phone-native 3D world viewer built with OpenGL ES 2.0.

## Version 0.3.0

This is **not a split-screen phone-in-a-headset app**. It renders one full-screen 3D view directly on the phone. No cardboard viewer, lenses, or headset are required.

- Turn the phone to look around using its rotation/game-rotation sensor.
- Drag a finger to look around with touch controls.
- Tap the screen to interact with and cycle the highlighted scene object.
- Recenter the view with the on-screen button.
- A simple 3D room with a floor grid and three interactive objects.
- Landscape immersive mode and screen-awake behavior.

The phone's rotation sensor provides orientation (3DoF-style look), not absolute position. This prototype does not implement room-scale positional tracking, hand tracking, physical controllers, or OpenXR. If the phone has no compatible rotation sensor, touch-look remains available.

## Build

- Android Gradle Plugin 8.7.3
- Gradle 8.9
- JDK 17
- Minimum Android 8.0 (API 26)

```bash
gradle --no-daemon assembleDebug assembleRelease
```

## CI

GitHub Actions builds debug and release APKs on pushes and pull requests. Push a version tag such as `v0.4.0` to publish a tagged release.

## License

No license has been selected yet. All rights reserved by default.
