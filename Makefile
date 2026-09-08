APP_ID := com.quizmaster.app
ACTIVITY := $(APP_ID)/.MainActivity
GRADLE := ./gradlew

ANDROID_HOME ?= $(HOME)/Library/Android/sdk
JAVA_HOME ?= /Library/Java/JavaVirtualMachines/jdk-21.0.10.jdk/Contents/Home
EMULATOR := $(ANDROID_HOME)/emulator/emulator
AVD_NAME := simulator_api34

export ANDROID_HOME
export JAVA_HOME

.PHONY: build install run stop uninstall logcat devices clean emulator run-emulator

build:
	$(GRADLE) assembleDebug

# Install the debug APK on a device connected over USB (adb).
install: build
	adb install -r app/build/outputs/apk/debug/app-debug.apk

# Build, install, and launch the app on the connected device.
run: install
	adb shell am start -n $(ACTIVITY)

# Boot the Android emulator (AVD) if one isn't already running.
emulator:
	@if adb devices | grep -q emulator; then \
		echo "Emulator already running."; \
	else \
		nohup $(EMULATOR) -avd $(AVD_NAME) > /tmp/quizmaster-emulator.log 2>&1 & disown; \
		adb wait-for-device; \
		echo "Waiting for emulator to finish booting..."; \
		until [ "$$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done; \
		echo "Emulator ready."; \
	fi

# Boot the emulator (if needed), then build, install, and launch the app on it.
run-emulator: emulator install
	adb shell am start -n $(ACTIVITY)

stop:
	adb shell am force-stop $(APP_ID)

uninstall:
	adb uninstall $(APP_ID)

# Stream the app's logs (Ctrl+C to stop).
logcat:
	adb logcat --pid=$$(adb shell pidof -s $(APP_ID))

devices:
	adb devices -l

clean:
	$(GRADLE) clean
