SHELL := /bin/bash

GRADLEW ?= ./Android/gradlew
ADB ?= adb
PYTHON ?= python3

APP_ID ?= id.my.sinanonym.stickerwa
RELEASE ?= 0
DEVICE_SERIAL ?=

# Certificate / Signing Configuration
KEYSTORE ?= $(HOME)/.android/debug.keystore
KEYSTORE_PASSWORD ?= android
KEY_ALIAS ?= androiddebugkey
KEY_PASSWORD ?= android

# Tools Discovery
APKSIGNER ?= $(shell find $(HOME)/Android/Sdk/build-tools -name apksigner 2>/dev/null | sort -V | tail -n 1)

ADB_DEVICE := $(ADB) $(if $(DEVICE_SERIAL),-s $(DEVICE_SERIAL),)
APK_DIR_DEBUG := Android/app/build/outputs/apk/debug
APK_DIR_RELEASE := Android/app/build/outputs/apk/release

.DEFAULT_GOAL := help

.PHONY: \
	help doctor device compile assemble build create-keystore verify-apk \
	install run dev logs errors clear-app reinstall validate-stickers clean stop

help: ## Show available commands
	@awk 'BEGIN {FS = ":.*## "}; /^[a-zA-Z0-9_-]+:.*## / {printf "  %-18s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

doctor: ## Check Gradle, ADB, Java, Python, and signing tools
	@command -v java >/dev/null || { echo "ERROR [TOOLS] Java was not found"; exit 1; }
	@command -v $(ADB) >/dev/null || { echo "ERROR [TOOLS] ADB was not found"; exit 1; }
	@command -v $(PYTHON) >/dev/null || { echo "ERROR [TOOLS] Python 3 was not found"; exit 1; }
	@test -f "$(GRADLEW)" || { echo "ERROR [TOOLS] Gradle wrapper was not found at $(GRADLEW)"; exit 1; }
	@echo "OK [TOOLS]"
	@java -version 2>&1 | head -n 1
	@$(ADB) version | head -n 1
	@$(PYTHON) --version
	@if [ -n "$(APKSIGNER)" ] && [ -x "$(APKSIGNER)" ]; then \
		echo "apksigner: $(APKSIGNER)"; \
	else \
		echo "apksigner: not found (will rely on Gradle signing)"; \
	fi
	@if [ -f "$(KEYSTORE)" ]; then \
		echo "keystore:  $(KEYSTORE) (found)"; \
	else \
		echo "keystore:  $(KEYSTORE) (not found - run 'make create-keystore' or specify KEYSTORE=...)"; \
	fi

device: ## Ensure an Android device is connected
	@$(ADB_DEVICE) get-state >/dev/null 2>&1 || { \
		echo "ERROR [DEVICE] Android device is disconnected or unauthorized"; \
		echo "Run: adb devices -l"; \
		exit 1; \
	}
	@echo "OK [DEVICE]"

create-keystore: ## Generate a dedicated signing keystore (Usage: make create-keystore KEYSTORE=release.keystore KEY_ALIAS=stickerwa)
	@test ! -f "$(KEYSTORE)" || { echo "ERROR: Keystore $(KEYSTORE) already exists"; exit 1; }
	@keytool -genkeypair -v \
		-keystore "$(KEYSTORE)" \
		-alias "$(KEY_ALIAS)" \
		-keyalg RSA \
		-keysize 2048 \
		-validity 10000 \
		-storepass "$(KEYSTORE_PASSWORD)" \
		-keypass "$(KEY_PASSWORD)" \
		-dname "CN=StickerWA, OU=Mobile, O=StickerWA, L=Jakarta, ST=Jakarta, C=ID"
	@echo "OK [KEYSTORE] Generated $(KEYSTORE) with alias '$(KEY_ALIAS)'"

compile: ## Compile Android Java sources
	@$(GRADLEW) -p Android compileDebugJavaWithJavac --no-configuration-cache

assemble: ## Build debug APK (alias to build RELEASE=0)
	@$(MAKE) build RELEASE=0

build: ## Build APK (RELEASE=1: signed multi-arch release APKs; RELEASE=0: debug APK)
ifeq ($(strip $(RELEASE)),1)
	@echo "Building Signed Release APKs (multi-arch: arm64-v8a/armv9, armeabi-v7a, x86, x86_64, and universal)..."
	@$(GRADLEW) -p Android assembleRelease \
		-PRELEASE_BUILD=true \
		-PKEYSTORE_FILE="$(KEYSTORE)" \
		-PKEYSTORE_PASSWORD="$(KEYSTORE_PASSWORD)" \
		-PKEY_ALIAS="$(KEY_ALIAS)" \
		-PKEY_PASSWORD="$(KEY_PASSWORD)" \
		--no-configuration-cache
	@echo ""
	@echo "OK [BUILD] Release APKs created in $(APK_DIR_RELEASE):"
	@ls -lh $(APK_DIR_RELEASE)/*.apk
else
	@echo "Building Debug APK..."
	@$(GRADLEW) -p Android assembleDebug --no-configuration-cache
	@echo ""
	@echo "OK [BUILD] Debug APK created in $(APK_DIR_DEBUG):"
	@ls -lh $(APK_DIR_DEBUG)/*.apk
endif

verify-apk: ## Verify the signature of built release APK
	@test -n "$(APKSIGNER)" || { echo "ERROR [SIGN] apksigner not found"; exit 1; }
	@APK="$$(ls -1 $(APK_DIR_RELEASE)/app-universal-release.apk $(APK_DIR_RELEASE)/*-release.apk 2>/dev/null | head -n 1)"; \
	if [[ -z "$$APK" ]]; then \
		echo "ERROR [SIGN] No release APK found in $(APK_DIR_RELEASE). Run 'make build RELEASE=1' first."; \
		exit 1; \
	fi; \
	echo "Verifying $$APK ..."; \
	$(APKSIGNER) verify --verbose "$$APK"

install: device ## Install APK to device (RELEASE=1 for universal release; RELEASE=0 for debug)
ifeq ($(strip $(RELEASE)),1)
	@echo "Installing universal release APK to device..."
	@APK="$$(ls -1 $(APK_DIR_RELEASE)/app-universal-release.apk $(APK_DIR_RELEASE)/app-universal-release-unsigned.apk 2>/dev/null | head -n 1)"; \
	if [[ -z "$$APK" ]]; then \
		echo "ERROR [INSTALL] Release APK not found. Run 'make build RELEASE=1' first."; \
		exit 1; \
	fi; \
	$(ADB_DEVICE) install -r "$$APK" && echo "OK [INSTALL] Installed $$APK"
else
	@$(GRADLEW) -p Android installDebug --no-configuration-cache
	@echo "OK [INSTALL] Debug APK installed"
endif

run: device ## Launch the application on the connected device
	@$(ADB_DEVICE) shell monkey \
		-p "$(APP_ID)" \
		-c android.intent.category.LAUNCHER \
		1 >/dev/null
	@echo "OK [APP] Application launched"

dev: build install run ## Build, install, and launch the application

logs: device ## Show application logs
	@PID="$$($(ADB_DEVICE) shell pidof -s "$(APP_ID)" | tr -d '\r')"; \
	if [[ -z "$$PID" ]]; then \
		echo "ERROR [APP] Application is not running"; \
		exit 1; \
	fi; \
	$(ADB_DEVICE) logcat --pid="$$PID"

errors: device ## Show important Android and StickerWA runtime errors
	@$(ADB_DEVICE) logcat | grep --line-buffered -E \
		'AndroidRuntime|FATAL EXCEPTION|StickerWA|Sticker|Fresco|WhatsApp'

clear-app: device ## Clear local application data on the device
	@if $(ADB_DEVICE) shell pm list packages "$(APP_ID)" | grep -q "$(APP_ID)"; then \
		$(ADB_DEVICE) shell pm clear "$(APP_ID)"; \
		echo "OK [CLEAR] Cleared data for $(APP_ID)"; \
	else \
		echo "OK [CLEAR] $(APP_ID) is not installed yet, skipping clear"; \
	fi

reinstall: clear-app install run ## Clear app data, reinstall, and launch

validate-stickers: ## Validate sticker assets and JSON manifests
	@$(PYTHON) Android/tools/manage_stickers.py validate
	@$(PYTHON) Android/tools/manage_stickers.py status

clean: ## Clean Gradle build outputs
	@$(GRADLEW) -p Android clean --no-configuration-cache

stop: ## Stop Gradle daemons
	@$(GRADLEW) -p Android --stop
