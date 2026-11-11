# DowNowder

A tiny Android share target that saves images and videos to your phone in their
original quality.

Share a photo or video from any app, pick **Galeriye Kaydet** in the share
sheet, and the file lands in `Download/DowNowder/`. There is no screen: you get
a short toast and you are back where you were. It is meant for files that
messaging apps (WhatsApp, for example) send as documents, or when their own
"save to gallery" option is turned off.

## How it works

- **Byte for byte:** the file is copied as it is, never decoded or re-encoded,
  so resolution, quality and metadata (EXIF) stay untouched.
- **Real file type:** files that arrive as `application/octet-stream` are
  recognized from their first bytes (JPEG, PNG, GIF, WebP, HEIC / HEIF, AVIF,
  MP4, MOV, 3GP, WebM, MKV, AVI), with the file extension as a fallback.
  Anything else is refused with a short message.
- **Safe writes:** each file is written as a pending MediaStore entry and only
  published when the copy is complete; a failed copy leaves nothing behind.
  Name clashes are resolved by MediaStore (`name (1).jpg`).
- **Several files at once:** one toast tells you how many were saved and how
  many were not.

## Privacy

No permissions: no internet, no storage access. Android lets an app add its own
files to `Download/` through MediaStore without asking. Nothing leaves the
device.

## Status

Early and personal: the interface is Turkish only and it is built for a single
device running Android 17. Other languages and devices may come later.

## Build

Requirements: JDK 17 and the Android SDK with the Android 16 (API 36) platform.

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Android 10 (API 29) or newer is required.

## License

[MIT](LICENSE)
