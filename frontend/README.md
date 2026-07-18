# MDReader Frontend

Vue frontend for the shared MDReader backend.

## API Configuration

Edit:

```text
public/mdreader-config.js
```

Use this when Nginx serves the frontend and proxies `/api` to Spring Boot:

```js
window.MDREADER_CONFIG = {
  apiBaseUrl: '/api'
}
```

Use a full remote URL only when the frontend and backend are hosted on different origins.

## Manual Build Steps

```bash
npm install
npm run build
```

Deploy the generated `dist` directory to Nginx.
