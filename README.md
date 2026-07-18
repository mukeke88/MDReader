# MDReader

Shared-server reading app with:

- Spring Boot backend
- MySQL 8 storage for chapters, sentences, and reading progress
- Vue web frontend served by Nginx
- Native Android client using the same remote backend

The web app and Android app both call the same `/api` backend, so progress stays synchronized through the shared MySQL database.

## Configuration

Backend environment variables:

```bash
SERVER_PORT=18080
MDREADER_DB_URL=jdbc:mysql://localhost:3306/mdreader?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
MDREADER_DB_USERNAME=mdreader
MDREADER_DB_PASSWORD=change-me
MDREADER_CORS_ALLOWED_ORIGINS=https://your-server.example.com
```

Web frontend API address:

- Edit `frontend/public/mdreader-config.js`.
- Keep `apiBaseUrl: '/api'` when Nginx serves the frontend and proxies `/api` to Spring Boot.
- Change it to a full URL only if the frontend and backend are hosted on different origins.

Android API address:

- Edit `Android/app/src/main/res/values/server_config.xml`.
- Set `api_base_url` to your remote backend URL, for example `https://your-server.example.com/api`.

## Database Setup

Create the database and tables:

```bash
mysql -u root -p < src/main/resources/db/mysql/setup-mysql.sql
```

To migrate the existing JSON chapter content into MySQL, generate and run a seed file:

```bash
node scripts/create-mysql-seed.js > src/main/resources/db/mysql/seed-data.sql
mysql -u root -p mdreader < src/main/resources/db/mysql/seed-data.sql
```

The seed helper writes warnings to stderr for chapter metadata that references missing sentence JSON files.

## Backend Packaging

Run these manually on the machine where you package the backend:

```bash
mvn clean package
```

Run the packaged backend on the server:

```bash
java -jar target/mdreader-backend-0.0.1-SNAPSHOT.jar
```

Use environment variables or your service manager to provide the database credentials.

## Frontend Packaging

Run these manually:

```bash
cd frontend
npm install
npm run build
```

Deploy `frontend/dist` to the Nginx web root. Configure Nginx to serve the frontend and proxy `/api` to the Spring Boot backend on port `18080`.

Example Nginx location:

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:18080/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

## Android Packaging

Open the `Android` folder in Android Studio.

1. Edit `app/src/main/res/values/server_config.xml`.
2. Choose **File > Sync Project with Gradle Files**.
3. Use **Build > Generate Signed Bundle / APK** for a release build, or run the `app` configuration for testing.

## API

- `GET /api/chapter/{chapterId}`
- `GET /api/progress/{chapterId}`
- `POST /api/progress/{chapterId}`
