# APK Signing SHA1

Debug:

```text
71:B4:5A:2A:13:8E:73:19:43:CB:AA:71:4E:2F:1B:54:C3:39:B1:4B
```

Release:

```text
BF:0B:79:E5:7E:94:AF:0A:27:82:9F:73:61:81:00:92:5C:2D:8F:F9
```

Release signing material is intentionally not committed. Configure these GitHub
Secrets to build release-signed APKs:

```text
SIGN_KEYSTORE_BASE64
SIGN_KEYSTORE_PASSWORD
SIGN_ALIAS
SIGN_KEY_PASSWORD
```
