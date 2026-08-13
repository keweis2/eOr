# Shizuku provenance

Pinned upstream revision: `b844bc491f1790c72328e1a8e5b2349f8978f0ea`.

The package names, provider authority, native library names, process name,
keystore alias, and Binder API are eOr-specific. The design adapts the upstream ADB
protocol/TLS/RSA/mDNS/pairing and native starter architecture. Copyright headers are
retained in adapted native files. The upstream Apache-2.0 license is reproduced in
`LICENSE` in this directory.

Adapted upstream sources:

- `manager/.../adb/AdbClient.kt`, `AdbKey.kt`, `AdbMdns.kt`, `AdbMessage.kt`,
  `AdbPairingClient.kt`, and `AdbProtocol.kt` → `EmbeddedAdb.kt`
- `manager/src/main/jni/adb_pairing.cpp` → `eor_adb.cpp`
- the detach/`app_process` portion of `manager/src/main/jni/starter.cpp` →
  `eor_starter.cpp`
- the external-provider Binder delivery pattern in
  `starter/.../ServiceStarter.java` and `IContentProviderCompat.java` →
  `EmbeddedBrokerBootstrapProvider.kt`

Security deviations from a general ADB client: discovery is restricted to local device
interfaces; messages are size bounded; pairing/bootstrap secrets and authenticated
commands are never logged; and no arbitrary command API is exposed to the app.
