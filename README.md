# MDReader MVP

Small local-first reading prototype with a Vue 3 frontend, a Spring Boot backend on Java 8, and local JSON file storage.

## Structure

- `frontend/`: Vue 3 app
- `src/main/java/`: Spring Boot backend
- `data/`: local JSON content and progress files
- `TestMaterial.md`: source text used to seed chapter sentence data

## MVP features

- Vertical sentence-by-sentence reading
- Per-sentence explanation toggle
- Global expand/collapse explanations
- Read detection with `IntersectionObserver`
- One-time scoring per sentence
- Local file persistence through backend JSON files
- Restore score, explanation state, read state, and last sentence on reload

## Run locally

### Backend

```bash
mvn spring-boot:run
```

Runs on `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs on `http://localhost:5173`.

## API

- `GET /api/chapter/chapter-1`
- `GET /api/progress/chapter-1`
- `POST /api/progress/chapter-1`

## Step-by-step implementation plan

1. Seed structured sentence JSON from `TestMaterial.md`.
2. Render the chapter in Vue with sentence blocks.
3. Add per-sentence explanation toggles.
4. Add global expand and collapse controls.
5. Mark sentences as read with `IntersectionObserver`.
6. Score each sentence once based on whether explanation was used before scoring.
7. Persist progress in backend JSON files.
8. Restore reading state and scroll back to `lastSentenceId` on reload.
