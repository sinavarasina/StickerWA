# Architecture Overview

## Project Structure

```
StickerWA/
├── Android/
│   └── app/
│       ├── build.gradle
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── assets/                  # Pre-packaged sticker assets and contents.json
│           ├── java/id/my/sinanonym/stickerwa/
│           │   ├── core/                # Image conversion, exporter, updater, preferences
│           │   ├── data/                # Sticker repository, user pack manager, models
│           │   ├── provider/            # Content provider for WhatsApp queries
│           │   ├── ui/                  # Activities, adapters, custom widgets
│           │   └── whatsapp/            # Intent launchers and whitelist verification
│           └── res/                     # Layouts, themes, drawables, strings
├── art/                                 # Vector assets, logos, and illustrations
├── docs/                                # Technical documentation
└── Makefile                             # Automation commands
```

## Core Components

- StickerContentProvider: Exposes asset files and metadata to WhatsApp via ContentProvider URIs.
- StickerImageConverter: Resizes and encodes user images to compliant WebP format (512x512, <=100 KB).
- UserStickerPackManager: Manages user-created sticker packs stored in app-internal files directory.
- StickerPackExporter: Compiles custom packs into git patches and zip bundles for community submissions.
- AppUpdateChecker: Communicates with GitHub API to detect newer releases.
- ThemePreferences & LocalePreferences: Persist and apply UI theme modes, color palettes, and languages.
