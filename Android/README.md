# MDReader Android

Native Android client for the remote MDReader backend.

## Server URL

Set the backend address in one place:

```text
app/src/main/res/values/server_config.xml
```

Example:

```xml
<string name="api_base_url">https://your-server.example.com/api</string>
```

## Android Studio Steps

1. Open the `Android` folder in Android Studio.
2. Edit `app/src/main/res/values/server_config.xml`.
3. Choose **File > Sync Project with Gradle Files**.
4. Run the `app` configuration for testing.
5. For release, use **Build > Generate Signed Bundle / APK**.
