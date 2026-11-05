# Building and Running

## Prerequisites

- Java Development Kit (JDK 17 or higher)
- Android SDK with build-tools (API 35/36)
- Android Debug Bridge (ADB)
- Make (optional, for automation commands)

## Common Commands

Compile Java sources:
```bash
make compile
```

Build debug APK:
```bash
make assemble
```

Build signed release APKs (multi-arch and universal):
```bash
make build RELEASE=1
```

Verify APK signature:
```bash
make verify-apk RELEASE=1
```

Install to connected device:
```bash
make install
```

Run application on device:
```bash
make run
```

## Keystore Configuration

To build release APKs locally with custom credentials:
```bash
make build RELEASE=1 \
  KEYSTORE=/path/to/keystore \
  KEYSTORE_PASSWORD=storepass \
  KEY_ALIAS=alias \
  KEY_PASSWORD=keypass
```
