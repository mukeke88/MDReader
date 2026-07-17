# MDReader Android

Native Android client for the MDReader Spring Boot backend.

## Open in Android Studio

1. Start the backend from the repository root:

   ```bash
   mvn spring-boot:run
   ```

   The backend runs on `http://localhost:18080`.

2. In Android Studio, choose **File > Open** and select the `Android` folder.
3. Let Android Studio sync the Gradle project.
4. Run the `app` configuration.

## Server URL

The Android emulator reaches your Windows host through:

```text
http://10.0.2.2:18080/api
```

That is the default used by the app.

For a physical Android device on the same Wi-Fi network, tap **Server** in the app and replace the URL with your PC LAN address, for example:

```text
http://192.168.1.25:18080/api
```

The backend must be reachable from the device, and Windows Firewall may need to allow Java/Spring Boot inbound access on port `18080`.
