# Pocket VR Lab

Pocket VR Lab is an Android stereoscopic VR sandbox built with OpenGL ES 2.0.

## Version 0.2.0

- Side-by-side stereoscopic rendering with separate left-eye and right-eye camera views.
- A real-time 3D test room with a floor grid and colored reference objects.
- Head orientation from Android's game rotation vector (with rotation-vector fallback): yaw, pitch, and roll.
- Recenter calibration and live head-pose telemetry in degrees.
- Two visible virtual 3D controllers, independently selectable and adjustable in X/Y/Z position and yaw/pitch rotation.
- Adjustable stereo IPD.
- Landscape immersive mode and screen-awake behavior.

## Controller and tracking notes

The two controllers in this version are **virtual test models**. Their positions and rotations are adjusted with the on-screen controls; they are not physical tracked controllers. A phone gyroscope/rotation sensor measures orientation, not absolute position, and this app does not provide 6DoF room tracking, hand tracking, controller pairing, or a full OpenXR runtime.

The stereo renderer is a development sandbox, not a certified Google Cardboard viewer profile or a replacement for a dedicated headset runtime. Lens distortion correction and per-headset optical calibration are not implemented yet.

## Build

- Android Gradle Plugin 8.7.3
- Gradle 8.9
- JDK 17
- Minimum Android 8.0 (API 26)

Run:

```bash
gradle --no-daemon assembleDebug assembleRelease
```

## CI

GitHub Actions builds debug and release APKs on pushes and pull requests. Push a version tag such as `v0.3.0` to publish a tagged release.

## License

No license has been selected yet. All rights reserved by default.
