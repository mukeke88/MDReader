Below is the safest beginner path: build on the Linux server, run Spring Boot behind Nginx, serve the web app with Nginx, and configure Android to call the same server.

I’ll use these placeholders. Replace them everywhere:
- `YOUR_SERVER_IP`: your server IP
- `YOUR_DOMAIN`: your domain, if you have one
- `DB_PASSWORD_HERE`: a strong MySQL password
- `APP_DIR`: `/opt/mdreader`

**Part 1: Prepare MySQL**
1. SSH into your Linux server.
2. Log into MySQL:
```bash
mysql -u root -p
```
3. Create an app user:
```sql
CREATE USER IF NOT EXISTS 'mdreader'@'localhost' IDENTIFIED BY 'DB_PASSWORD_HERE';
GRANT ALL PRIVILEGES ON mdreader.* TO 'mdreader'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

**Part 2: Upload Project**
1. On the server, create the app folder:
```bash
sudo mkdir -p /opt/mdreader
sudo chown $USER:$USER /opt/mdreader
```
2. Copy your project folder from Windows to `/opt/mdreader`.
   If using PowerShell from Windows:
```powershell
scp -r D:\coding\TinkerTank\MDReader\* your-linux-user@YOUR_SERVER_IP:/opt/mdreader/
```

**Part 3: Seed Chapter Data**
1. On the server:
```bash
cd /opt/mdreader
node scripts/create-mysql-seed.js > seed-data.sql
mysql -u mdreader -p mdreader < seed-data.sql
```
2. Enter `DB_PASSWORD_HERE` when asked.
3. If you see a warning about `12-01.json`, that means one local sentence file is missing. The rest can still import.

**Part 4: Backend Config**
1. Create an environment file:
```bash
sudo nano /etc/mdreader.env
```
2. Paste:
```bash
SERVER_PORT=18080
MDREADER_DB_URL=jdbc:mysql://localhost:3306/mdreader?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
MDREADER_DB_USERNAME=mdreader
MDREADER_DB_PASSWORD=DB_PASSWORD_HERE
MDREADER_CORS_ALLOWED_ORIGINS=http://YOUR_SERVER_IP
```
If using a domain, use:
```bash
MDREADER_CORS_ALLOWED_ORIGINS=https://YOUR_DOMAIN
```
3. Save: `Ctrl+O`, Enter, `Ctrl+X`.

**Part 5: Install Maven If Needed**
```bash
mvn -version
```
If not found:
```bash
sudo apt update
sudo apt install -y maven
```

**Part 6: Package Backend**
```bash
cd /opt/mdreader
mvn clean package
```

**Part 7: Create Backend Service**
1. Create service file:
```bash
sudo nano /etc/systemd/system/mdreader.service
```
2. Paste:
```ini
[Unit]
Description=MDReader Spring Boot Backend
After=network.target mysql.service

[Service]
WorkingDirectory=/opt/mdreader
EnvironmentFile=/etc/mdreader.env
ExecStart=/usr/bin/java -jar /opt/mdreader/target/mdreader-backend-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=5
User=YOUR_LINUX_USER

[Install]
WantedBy=multi-user.target
```
3. Replace `YOUR_LINUX_USER`.
4. Save and start:
```bash
sudo systemctl daemon-reload
sudo systemctl enable mdreader
sudo systemctl start mdreader
sudo systemctl status mdreader
```
5. Test backend:
```bash
curl http://127.0.0.1:18080/api/chapter/chapter-1
```

**Part 8: Configure Web Frontend**
1. Edit:
```bash
nano /opt/mdreader/frontend/public/mdreader-config.js
```
2. Use this if Nginx proxies `/api`:
```js
window.MDREADER_CONFIG = {
  apiBaseUrl: '/api'
}
```
3. Build frontend:
```bash
cd /opt/mdreader/frontend
npm install
npm run build
```

**Part 9: Configure Nginx**
1. Create config:
```bash
sudo nano /etc/nginx/sites-available/mdreader
```
2. Paste:
```nginx
server {
    listen 80;
    server_name YOUR_SERVER_IP;

    root /opt/mdreader/frontend/dist;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:18080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```
If using a domain, replace `server_name YOUR_SERVER_IP;` with `server_name YOUR_DOMAIN;`.

3. Enable it:
```bash
sudo ln -s /etc/nginx/sites-available/mdreader /etc/nginx/sites-enabled/mdreader
sudo nginx -t
sudo systemctl reload nginx
```

**Part 10: Test Web App**
1. On Windows 11, open browser.
2. Go to:
```text
http://YOUR_SERVER_IP
```
or:
```text
https://YOUR_DOMAIN
```
3. Open one sentence explanation or scroll.
4. Refresh the page.
5. Confirm progress remains saved.

**Part 11: Configure Android**
1. On Windows, open Android Studio.
2. Choose **File > Open**.
3. Select:
```text
D:\coding\TinkerTank\MDReader\Android
```
4. Open:
```text
app/src/main/res/values/server_config.xml
```
5. If using plain IP over HTTP:
```xml
<string name="api_base_url">http://YOUR_SERVER_IP/api</string>
```
If using HTTPS domain:
```xml
<string name="api_base_url">https://YOUR_DOMAIN/api</string>
```
6. Click **File > Sync Project with Gradle Files**.
7. Connect your Android phone with USB debugging enabled.
8. Click Run.
9. Open the same chapter on Android.
10. Read or change score.
11. Refresh the web app on Windows and confirm the same progress appears.

**Common Checks**
- Backend logs:
```bash
sudo journalctl -u mdreader -f
```
- Restart backend:
```bash
sudo systemctl restart mdreader
```
- Restart Nginx:
```bash
sudo systemctl reload nginx
```
- If web works but Android does not, make sure your phone can reach `http://YOUR_SERVER_IP` or `https://YOUR_DOMAIN` from mobile browser first.

**Updating an Existing Deployment**
After you make program changes locally, update the same server deployment like this:

1. Copy the updated project to the server:
```powershell
scp -r D:\coding\TinkerTank\MDReader\* your-linux-user@YOUR_SERVER_IP:/opt/mdreader/
```

2. Rebuild and restart the backend:
```bash
cd /opt/mdreader
mvn clean package
sudo systemctl restart mdreader
sudo systemctl status mdreader
```

3. Rebuild the web frontend:
```bash
cd /opt/mdreader/frontend
npm install
npm run build
```

4. Reload Nginx:
```bash
sudo nginx -t
sudo systemctl reload nginx
```

commands in one:
cd /opt/mdreader

mvn clean package

sudo systemctl restart mdreader

cd frontend

npm install
npm run build

sudo nginx -t && sudo systemctl reload nginx

5. Only rerun the MySQL seed/import step when you intentionally want to replace the server's chapter content. Do not rerun it for ordinary UI or backend code updates, because reading progress is stored on the server.

6. For Android changes, build and reinstall the APK from Android Studio after updating `app/src/main/res/values/server_config.xml` if the server URL changed.
