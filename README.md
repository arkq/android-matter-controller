<!--
SPDX-FileCopyrightText: 2026 The Authors
SPDX-License-Identifier: Apache-2.0
-->

# Æther - Matter Controller for Android

Æther is a simple standalone [Matter](https://csa-iot.org/all-solutions/matter/)
controller for Android.

The app keeps the Matter fabric on your Android phone, so it does not require a
hub or other third-party controller hardware.

## Build

From the repository root:

```bash
./gradlew :app:assembleDebug
```

It is also possible to build for a specific ABI, e.g. for `arm64-v8a`:

```bash
./gradlew -PselectedAbi=arm64-v8a :app:assembleDebug
```

## License

Licensed under Apache-2.0. See [LICENSE](LICENSE).
