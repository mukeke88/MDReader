package com.tinkertank.mdreader;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String DEFAULT_CHAPTER_ID = "chapter-1";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Sentence> sentences = new ArrayList<>();
    private final Map<Integer, View> sentenceViews = new HashMap<>();
    private final Set<Integer> openedSentenceIds = new HashSet<>();
    private final Set<Integer> readSentenceIds = new HashSet<>();
    private final Set<Integer> scoredSentenceIds = new HashSet<>();
    private final Set<Integer> explanationUsedSentenceIds = new HashSet<>();

    private LinearLayout root;
    private LinearLayout scoreBar;
    private LinearLayout sentenceList;
    private ScrollView scrollView;
    private TextView titleView;
    private TextView statusView;
    private TextView greenScoreView;
    private TextView redScoreView;
    private TextView manualRedScoreView;
    private ProgressBar loading;

    private String apiBase;
    private String activeChapterId = DEFAULT_CHAPTER_ID;
    private String chapterTitle = "";
    private Integer lastSentenceId = null;
    private int greenScore = 0;
    private int redScore = 0;
    private int manualRedScore = 0;
    private boolean globalExpanded = false;
    private boolean hydrating = false;
    private final Runnable saveRunnable = this::persistProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiBase = normalizeApiBase(getString(R.string.api_base_url));
        buildUi();
        loadPage(DEFAULT_CHAPTER_ID);
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(saveRunnable);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 244, 238));
        setContentView(root);

        scoreBar = new LinearLayout(this);
        scoreBar.setOrientation(LinearLayout.HORIZONTAL);
        scoreBar.setGravity(Gravity.CENTER_VERTICAL);
        scoreBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(scoreBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        greenScoreView = scoreChip("Green 0");
        redScoreView = scoreChip("Red 0");
        manualRedScoreView = scoreChip("Manual 0");
        scoreBar.addView(greenScoreView);
        scoreBar.addView(redScoreView);
        scoreBar.addView(manualRedScoreView);
        scoreBar.addView(actionButton("+", v -> setManualRedScore(manualRedScore + 1)));
        scoreBar.addView(actionButton("-", v -> setManualRedScore(Math.max(0, manualRedScore - 1))));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(12), 0, dp(12), dp(8));
        root.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        actions.addView(actionButton("Expand", v -> setGlobalExpanded(true)));
        actions.addView(actionButton("Collapse", v -> setGlobalExpanded(false)));

        titleView = new TextView(this);
        titleView.setTextSize(24);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(Color.rgb(36, 35, 32));
        titleView.setPadding(dp(18), dp(8), dp(18), dp(6));
        root.addView(titleView);

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(95, 88, 76));
        statusView.setPadding(dp(18), 0, dp(18), dp(10));
        root.addView(statusView);

        loading = new ProgressBar(this);
        loading.setIndeterminate(true);
        root.addView(loading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        scrollView = new ScrollView(this);
        sentenceList = new LinearLayout(this);
        sentenceList.setOrientation(LinearLayout.VERTICAL);
        sentenceList.setPadding(dp(12), dp(4), dp(12), dp(24));
        scrollView.addView(sentenceList);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        scrollView.getViewTreeObserver().addOnScrollChangedListener(this::markVisibleSentencesAsRead);
    }

    private TextView scoreChip(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(Color.rgb(36, 35, 32));
        view.setPadding(dp(8), dp(6), dp(8), dp(6));
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(Color.rgb(235, 230, 220));
        view.setMinWidth(dp(76));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, dp(6), 0);
        view.setLayoutParams(params);
        return view;
    }

    private Button actionButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, dp(6), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void loadPage(String chapterId) {
        hydrating = true;
        loading.setVisibility(View.VISIBLE);
        statusView.setText("Loading from " + apiBase);
        executor.execute(() -> {
            try {
                JSONObject chapter = getJson("/chapter/" + encode(chapterId));
                String progressKey = buildProgressKey(chapter.optString("title", ""));
                JSONObject progress = progressKey == null
                        ? new JSONObject()
                        : getJson("/progress/" + encode(progressKey));
                mainHandler.post(() -> applyChapterState(chapter, progress));
            } catch (Exception error) {
                mainHandler.post(() -> {
                    loading.setVisibility(View.GONE);
                    hydrating = false;
                    statusView.setText("Load failed: " + error.getMessage());
                    Toast.makeText(this, "Failed to load chapter", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void applyChapterState(JSONObject chapter, JSONObject progress) {
        sentences.clear();
        openedSentenceIds.clear();
        readSentenceIds.clear();
        scoredSentenceIds.clear();
        explanationUsedSentenceIds.clear();

        activeChapterId = chapter.optString("chapterId", DEFAULT_CHAPTER_ID);
        chapterTitle = chapter.optString("title", "");
        JSONArray sentenceArray = chapter.optJSONArray("sentences");
        if (sentenceArray != null) {
            for (int i = 0; i < sentenceArray.length(); i++) {
                JSONObject item = sentenceArray.optJSONObject(i);
                if (item != null) {
                    sentences.add(new Sentence(
                            item.optInt("id"),
                            item.optInt("paragraphId", 1),
                            item.optString("text", ""),
                            item.optString("explanation", "")
                    ));
                }
            }
        }

        lastSentenceId = progress.has("lastSentenceId") && !progress.isNull("lastSentenceId")
                ? progress.optInt("lastSentenceId")
                : null;
        addAll(openedSentenceIds, progress.optJSONArray("openedSentenceIds"));
        addAll(readSentenceIds, progress.optJSONArray("readSentenceIds"));
        addAll(scoredSentenceIds, progress.optJSONArray("scoredSentenceIds"));
        addAll(explanationUsedSentenceIds, progress.optJSONArray("explanationUsedSentenceIds"));
        greenScore = progress.has("greenScore") ? progress.optInt("greenScore") : deriveGreenScore();
        redScore = progress.has("redScore") ? progress.optInt("redScore") : deriveRedScore();
        manualRedScore = progress.optInt("manualRedScore", 0);
        globalExpanded = progress.optBoolean("globalExpanded", false);

        renderChapter();
        updateScores();
        loading.setVisibility(View.GONE);
        hydrating = false;
        statusView.setText(sentences.size() + " sentences loaded");

        if (lastSentenceId != null) {
            mainHandler.postDelayed(() -> scrollToSentence(lastSentenceId), 250);
        } else {
            mainHandler.postDelayed(this::markVisibleSentencesAsRead, 250);
        }
    }

    private void renderChapter() {
        titleView.setText(chapterTitle.isEmpty() ? "MDReader" : chapterTitle);
        sentenceList.removeAllViews();
        sentenceViews.clear();

        int currentParagraph = -1;
        for (Sentence sentence : sentences) {
            if (sentence.paragraphId != currentParagraph) {
                currentParagraph = sentence.paragraphId;
                TextView label = new TextView(this);
                label.setText("P" + currentParagraph);
                label.setTextColor(Color.rgb(121, 109, 88));
                label.setTypeface(Typeface.DEFAULT_BOLD);
                label.setPadding(dp(6), dp(14), dp(6), dp(4));
                sentenceList.addView(label);
            }

            View card = createSentenceCard(sentence);
            sentenceViews.put(sentence.id, card);
            sentenceList.addView(card);
        }
    }

    private View createSentenceCard(Sentence sentence) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(readSentenceIds.contains(sentence.id)
                ? Color.rgb(234, 245, 233)
                : Color.WHITE);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardParams);

        TextView text = new TextView(this);
        text.setText(sentence.text);
        text.setTextSize(18);
        text.setLineSpacing(dp(2), 1.05f);
        text.setTextColor(Color.rgb(39, 38, 34));
        card.addView(text);

        TextView explanation = new TextView(this);
        explanation.setText(sentence.explanation);
        explanation.setTextSize(15);
        explanation.setTextColor(Color.rgb(76, 70, 60));
        explanation.setPadding(0, dp(10), 0, dp(8));
        explanation.setVisibility(isExplanationOpen(sentence.id) ? View.VISIBLE : View.GONE);
        card.addView(explanation);

        Button toggle = actionButton(isExplanationOpen(sentence.id) ? "Hide explanation" : "Show explanation", v -> {
            toggleSentenceExplanation(sentence.id);
            explanation.setVisibility(isExplanationOpen(sentence.id) ? View.VISIBLE : View.GONE);
            ((Button) v).setText(isExplanationOpen(sentence.id) ? "Hide explanation" : "Show explanation");
            card.setBackgroundColor(readSentenceIds.contains(sentence.id)
                    ? Color.rgb(234, 245, 233)
                    : Color.WHITE);
        });
        card.addView(toggle);

        return card;
    }

    private boolean isExplanationOpen(int sentenceId) {
        return globalExpanded || openedSentenceIds.contains(sentenceId);
    }

    private void toggleSentenceExplanation(int sentenceId) {
        if (openedSentenceIds.contains(sentenceId)) {
            openedSentenceIds.remove(sentenceId);
            clearExplanationUsed(sentenceId);
        } else {
            openedSentenceIds.add(sentenceId);
            markExplanationUsed(sentenceId);
            handleSentenceRead(sentenceId);
        }
        schedulePersist();
    }

    private void setGlobalExpanded(boolean expanded) {
        globalExpanded = expanded;
        if (expanded) {
            for (Sentence sentence : sentences) {
                if (!scoredSentenceIds.contains(sentence.id)) {
                    explanationUsedSentenceIds.add(sentence.id);
                }
            }
        }
        renderChapter();
        schedulePersist();
    }

    private void markVisibleSentencesAsRead() {
        if (hydrating || sentenceViews.isEmpty()) {
            return;
        }

        int threshold = scrollView.getScrollY() + Math.round(scrollView.getHeight() * 0.85f);
        int latestVisibleId = -1;
        for (Sentence sentence : sentences) {
            View card = sentenceViews.get(sentence.id);
            if (card != null && card.getTop() <= threshold) {
                latestVisibleId = sentence.id;
            }
        }

        if (latestVisibleId > -1) {
            markSentencesUpTo(latestVisibleId);
        }
    }

    private void markSentencesUpTo(int targetSentenceId) {
        for (Sentence sentence : sentences) {
            if (sentence.id > targetSentenceId) {
                break;
            }
            handleSentenceRead(sentence.id);
        }
    }

    private void handleSentenceRead(int sentenceId) {
        boolean changed = readSentenceIds.add(sentenceId);
        lastSentenceId = sentenceId;
        changed = scoreSentence(sentenceId) || changed;

        View card = sentenceViews.get(sentenceId);
        if (card != null) {
            card.setBackgroundColor(Color.rgb(234, 245, 233));
        }

        if (changed) {
            updateScores();
            schedulePersist();
        }
    }

    private boolean scoreSentence(int sentenceId) {
        if (scoredSentenceIds.contains(sentenceId)) {
            return false;
        }

        if (explanationUsedSentenceIds.contains(sentenceId)) {
            redScore++;
        } else {
            greenScore++;
        }
        scoredSentenceIds.add(sentenceId);
        return true;
    }

    private void markExplanationUsed(int sentenceId) {
        if (!explanationUsedSentenceIds.add(sentenceId)) {
            return;
        }

        if (scoredSentenceIds.contains(sentenceId)) {
            greenScore = Math.max(0, greenScore - 1);
            redScore++;
            updateScores();
        }
    }

    private void clearExplanationUsed(int sentenceId) {
        if (!explanationUsedSentenceIds.remove(sentenceId)) {
            return;
        }

        if (scoredSentenceIds.contains(sentenceId)) {
            redScore = Math.max(0, redScore - 1);
            greenScore++;
            updateScores();
        }
    }

    private void setManualRedScore(int value) {
        manualRedScore = Math.max(0, value);
        updateScores();
        schedulePersist();
    }

    private void updateScores() {
        greenScoreView.setText("Green " + greenScore);
        redScoreView.setText("Red " + redScore);
        manualRedScoreView.setText("Manual " + manualRedScore);
    }

    private void schedulePersist() {
        if (hydrating) {
            return;
        }
        mainHandler.removeCallbacks(saveRunnable);
        mainHandler.postDelayed(saveRunnable, 250);
    }

    private void persistProgress() {
        String progressKey = getCurrentProgressKey();
        if (progressKey == null) {
            return;
        }

        JSONObject payload = buildProgressJson(progressKey);
        executor.execute(() -> {
            try {
                postJson("/progress/" + encode(progressKey), payload);
            } catch (Exception error) {
                mainHandler.post(() -> statusView.setText("Save failed: " + error.getMessage()));
            }
        });
    }

    private JSONObject buildProgressJson(String progressKey) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("chapterId", progressKey);
            payload.put("lastSentenceId", lastSentenceId == null ? JSONObject.NULL : lastSentenceId);
            payload.put("totalScore", greenScore + redScore + manualRedScore);
            payload.put("greenScore", greenScore);
            payload.put("redScore", redScore);
            payload.put("manualRedScore", manualRedScore);
            payload.put("globalExpanded", globalExpanded);
            payload.put("openedSentenceIds", toArray(openedSentenceIds));
            payload.put("readSentenceIds", toArray(readSentenceIds));
            payload.put("scoredSentenceIds", toArray(scoredSentenceIds));
            payload.put("explanationUsedSentenceIds", toArray(explanationUsedSentenceIds));
        } catch (Exception ignored) {
            // JSONObject mutations above use valid keys and in-memory values.
        }
        return payload;
    }

    private void scrollToSentence(int sentenceId) {
        View card = sentenceViews.get(sentenceId);
        if (card != null) {
            scrollView.smoothScrollTo(0, Math.max(0, card.getTop() - dp(80)));
        }
    }

    private JSONObject getJson(String path) throws Exception {
        HttpURLConnection connection = openConnection(path, "GET");
        return readJson(connection);
    }

    private JSONObject postJson(String path, JSONObject payload) throws Exception {
        HttpURLConnection connection = openConnection(path, "POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        try (OutputStream output = connection.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            writer.write(payload.toString());
        }
        return readJson(connection);
    }

    private HttpURLConnection openConnection(String path, String method) throws Exception {
        URL url = new URL(apiBase + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private String normalizeApiBase(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private JSONObject readJson(HttpURLConnection connection) throws Exception {
        int statusCode = connection.getResponseCode();
        InputStream stream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readAll(stream);
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("HTTP " + statusCode + " " + body);
        }
        if (body == null || body.trim().isEmpty()) {
            return new JSONObject();
        }
        return new JSONObject(body);
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private String getCurrentProgressKey() {
        return buildProgressKey(chapterTitle);
    }

    private String buildProgressKey(String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty()) {
            return activeChapterId;
        }
        if ("temp".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized
                .replace("\\", "_")
                .replace("/", "_")
                .replace("#", "_")
                .replace("?", "_")
                .replace("%", "_");
    }

    private int deriveGreenScore() {
        int value = 0;
        for (Integer id : scoredSentenceIds) {
            if (!explanationUsedSentenceIds.contains(id)) {
                value++;
            }
        }
        return value;
    }

    private int deriveRedScore() {
        int value = 0;
        for (Integer id : scoredSentenceIds) {
            if (explanationUsedSentenceIds.contains(id)) {
                value++;
            }
        }
        return value;
    }

    private void addAll(Set<Integer> target, JSONArray values) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.length(); i++) {
            target.add(values.optInt(i));
        }
    }

    private JSONArray toArray(Set<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        JSONArray array = new JSONArray();
        for (Integer value : sorted) {
            array.put(value);
        }
        return array;
    }

    private String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Sentence {
        final int id;
        final int paragraphId;
        final String text;
        final String explanation;

        Sentence(int id, int paragraphId, String text, String explanation) {
            this.id = id;
            this.paragraphId = paragraphId;
            this.text = text;
            this.explanation = explanation;
        }
    }
}
