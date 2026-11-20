# Pocket VR Lab

Pocket VR Lab is an experimental, phone-based stereoscopic viewer and motion-control sandbox for Android.

## What it does

- Splits the landscape display into left-eye and right-eye views for a basic Cardboard-style viewer.
- Uses the phone's rotation-vector sensor (gyroscope/fusion sensor when available) for head-look rotation.
- Smooths head movement and provides a recenter control.
- Includes on-screen side controls for move/turn, plus Jump and Use buttons.
- Keeps the screen awake and uses immersive fullscreen mode.

## Important limitations

This is a rotation-only prototype. It does **not** provide positional tracking, room-scale tracking, controller tracking, or a complete VR runtime. The scene is a technical demo, not a game engine or a universal VR compatibility layer. A phone without a supported rotation sensor can still display the split-screen demo, but head-look will not work.

For best results, use a landscape phone mount/headset with lenses designed for the phone's screen size. Remove the phone from the viewer before interacting with settings or connecting accessories.

## Build

The project uses Android Gradle Plugin 8.7.3 and JDK 17. Open the repository in Android Studio or run:

```bash
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## CI

GitHub Actions builds the debug and release APKs on pushes and pull requests. Pushing a version tag such as `v1.0.0` builds the release APK and publishes a GitHub Release automatically.

## License

No license has been selected yet. All rights reserved by default.
