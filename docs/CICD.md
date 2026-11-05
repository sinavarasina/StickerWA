# CI/CD Workflow

## Overview

The repository utilizes GitHub Actions to automate build verification, version management, and release publishing.

Workflow file: `.github/workflows/release.yml`

## Triggering a Release

Releases are triggered manually using the GitHub Actions console:
1. Navigate to the Actions tab on GitHub.
2. Select the "Build & Release" workflow.
3. Click "Run workflow".
4. Choose version bump type:
   - patch: Increments patch digit (e.g. 1.0.0 to 1.0.1).
   - minor: Increments minor digit (e.g. 1.0.0 to 1.1.0).
   - major: Increments major digit (e.g. 1.0.0 to 2.0.0).
   - custom: Uses the custom version string entered in the input.
5. Click "Run workflow".

## Automation Steps

1. Calculates next semantic version and monotonic version code.
2. Configures Android keystore from GitHub Secrets.
3. Builds signed multi-arch APKs and universal release APK.
4. Generates SHA-256 checksums file.
5. Creates git tag and pushes to repository.
6. Publishes GitHub Release with attached APK binaries and release notes.
