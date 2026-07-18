package com.tinkertank.mdreader;

import android.app.Activity;
import android.os.Build;
import android.content.Intent;
import android.database.Cursor;
import android.view.DisplayCutout;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.os.Handler;
import android.os.Looper;
import android.text.Selection;
import android.text.Spannable;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String DEFAULT_CHAPTER_ID = "chapter-1";
    private static final int IMPORT_MARKDOWN_REQUEST = 10;
    private static final int TRANSLATE_TEXT_REQUEST = 11;
    private static final float READ_TRIGGER_FRACTION = 0.08f;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Sentence> sentences = new ArrayList<>();
    private final Map<Integer, View> sentenceViews = new HashMap<>();
    private final Set<Integer> openedSentenceIds = new HashSet<>();
    private final Set<Integer> readSentenceIds = new HashSet<>();
    private final Set<Integer> scoredSentenceIds = new HashSet<>();
    private final Set<Integer> explanationUsedSentenceIds = new HashSet<>();
    private final List<TextView> selectableTextViews = new ArrayList<>();

    private FrameLayout root;
    private LinearLayout contentRoot;
    private LinearLayout sentenceList;
    private ScrollView scrollView;
    private TextView titleView;
    private TextView statusView;
    private TextView greenScoreView;
    private TextView redScoreView;
    private ProgressBar loading;

    private String apiBase;
    private String activeChapterId = DEFAULT_CHAPTER_ID;
    private String chapterTitle = "";
    private Integer lastSentenceId = null;
    private int topSafeAreaInset = 0;
    private int greenScore = 0;
    private int redScore = 0;
    private boolean hydrating = false;
    private boolean clearSelectionWhenFocusReturns = false;
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && hasActiveTextSelection()) {
            clearSelectionWhenFocusReturns = true;
        } else if (hasFocus && clearSelectionWhenFocusReturns) {
            clearSelectionWhenFocusReturns = false;
            mainHandler.post(this::clearAllTextSelection);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == TRANSLATE_TEXT_REQUEST) {
            clearAllTextSelection();
            return;
        }

        if (requestCode != IMPORT_MARKDOWN_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        importMarkdownFromUri(data.getData());
    }

    private void buildUi() {
        configureEdgeToEdge();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(247, 244, 238));
        setContentView(root);

        contentRoot = new LinearLayout(this);
        contentRoot.setOrientation(LinearLayout.VERTICAL);
        root.addView(contentRoot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        applySafeAreaInsets();

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(8), dp(4), dp(8), dp(4));
        contentRoot.addView(headerRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button importButton = actionButton("IM", v -> openMarkdownFilePicker());
        importButton.setMinWidth(0);
        importButton.setMinimumWidth(0);
        importButton.setMinHeight(0);
        importButton.setMinimumHeight(0);
        importButton.setPadding(dp(8), 0, dp(8), 0);
        headerRow.addView(importButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(32)
        ));

        greenScoreView = scoreNumber(Color.rgb(4, 120, 87));
        TextView scoreDivider = new TextView(this);
        scoreDivider.setText("/");
        scoreDivider.setTextSize(16);
        scoreDivider.setTypeface(Typeface.DEFAULT_BOLD);
        scoreDivider.setTextColor(Color.rgb(156, 163, 175));
        scoreDivider.setGravity(Gravity.CENTER);
        redScoreView = scoreNumber(Color.rgb(185, 28, 28));

        LinearLayout.LayoutParams greenScoreParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        greenScoreParams.setMargins(dp(8), 0, 0, 0);
        headerRow.addView(greenScoreView, greenScoreParams);
        headerRow.addView(scoreDivider);
        headerRow.addView(redScoreView);
        headerRow.addView(refreshButton(), new LinearLayout.LayoutParams(dp(32), dp(32)));

        titleView = new TextView(this);
        titleView.setTextSize(22);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(Color.rgb(36, 35, 32));
        titleView.setSingleLine(true);
        titleView.setPadding(dp(10), 0, 0, 0);
        headerRow.addView(titleView, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(95, 88, 76));
        statusView.setPadding(dp(12), 0, dp(12), dp(4));
        statusView.setVisibility(View.GONE);
        contentRoot.addView(statusView);

        loading = new ProgressBar(this);
        loading.setIndeterminate(true);
        contentRoot.addView(loading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        scrollView = new ScrollView(this);
        sentenceList = new LinearLayout(this);
        sentenceList.setOrientation(LinearLayout.VERTICAL);
        sentenceList.setPadding(dp(12), dp(4), dp(12), dp(24));
        scrollView.addView(sentenceList);
        contentRoot.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        scrollView.getViewTreeObserver().addOnScrollChangedListener(this::markVisibleSentencesAsRead);
    }

    private void configureEdgeToEdge() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.rgb(247, 244, 238));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }
    }

    private void applySafeAreaInsets() {
        if (root == null || contentRoot == null) {
            return;
        }

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            int leftInset = insets.getSystemWindowInsetLeft();
            int rightInset = insets.getSystemWindowInsetRight();
            int bottomInset = insets.getSystemWindowInsetBottom();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    topInset = Math.max(topInset, cutout.getSafeInsetTop());
                    leftInset = Math.max(leftInset, cutout.getSafeInsetLeft());
                    rightInset = Math.max(rightInset, cutout.getSafeInsetRight());
                    bottomInset = Math.max(bottomInset, cutout.getSafeInsetBottom());
                }
            }

            topSafeAreaInset = topInset;
            contentRoot.setPadding(leftInset, topInset + dp(6), rightInset, bottomInset);
            return insets;
        });
        root.requestApplyInsets();
    }

    private TextView scoreNumber(int color) {
        TextView view = new TextView(this);
        view.setText("0");
        view.setTextSize(16);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(color);
        view.setPadding(dp(2), 0, dp(2), 0);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(dp(24));
        return view;
    }

    private ImageButton refreshButton() {
        ImageButton button = new ImageButton(this);
        button.setImageResource(android.R.drawable.ic_popup_sync);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setColorFilter(Color.rgb(36, 35, 32));
        button.setContentDescription("Refresh reading progress");
        button.setPadding(dp(6), dp(6), dp(6), dp(6));
        button.setOnClickListener(v -> refreshProgressFromServer(true));
        return button;
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

    private void openMarkdownFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/markdown",
                "text/plain",
                "application/octet-stream"
        });
        startActivityForResult(intent, IMPORT_MARKDOWN_REQUEST);
    }

    private void setStatus(String text) {
        statusView.setText(text);
        statusView.setVisibility(text == null || text.trim().isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void importMarkdownFromUri(Uri uri) {
        hydrating = true;
        loading.setVisibility(View.VISIBLE);
        setStatus("Importing Markdown...");
        executor.execute(() -> {
            try {
                String markdown;
                try (InputStream stream = getContentResolver().openInputStream(uri)) {
                    markdown = readText(stream);
                }
                String title = titleFromUri(uri);
                String lowerTitle = title.toLowerCase();
                if (lowerTitle.endsWith(".md")) {
                    title = title.substring(0, title.length() - 3);
                } else if (lowerTitle.endsWith(".markdown")) {
                    title = title.substring(0, title.length() - 9);
                }

                JSONObject payload = new JSONObject();
                payload.put("title", title.trim().isEmpty() ? "Imported Markdown" : title.trim());
                payload.put("markdown", markdown);
                JSONObject chapter = postJson("/chapter/" + encode(activeChapterId) + "/import", payload);
                mainHandler.post(() -> applyChapterState(chapter, new JSONObject()));
            } catch (Exception error) {
                mainHandler.post(() -> {
                    hydrating = false;
                    loading.setVisibility(View.GONE);
                    setStatus("Import failed: " + error.getMessage());
                    Toast.makeText(this, "Failed to import Markdown", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String titleFromUri(Uri uri) {
        String title = null;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    title = cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {
            // Fall back to URI path below.
        }

        if (title == null || title.trim().isEmpty()) {
            String path = uri.getLastPathSegment();
            title = path == null || path.trim().isEmpty() ? "Imported Markdown" : path;
        }
        return title;
    }

    private void loadPage(String chapterId) {
        hydrating = true;
        loading.setVisibility(View.VISIBLE);
        setStatus("Loading from " + apiBase);
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
                    setStatus("Load failed: " + error.getMessage());
                    Toast.makeText(this, "Failed to load chapter", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void refreshProgressFromServer(boolean showFeedback) {
        String progressKey = getCurrentProgressKey();
        if (progressKey == null) {
            return;
        }

        hydrating = true;
        mainHandler.removeCallbacks(saveRunnable);
        loading.setVisibility(View.VISIBLE);
        setStatus("Refreshing progress...");
        executor.execute(() -> {
            try {
                JSONObject progress = getJson("/progress/" + encode(progressKey));
                mainHandler.post(() -> applyProgressState(progress, true, showFeedback));
            } catch (Exception error) {
                mainHandler.post(() -> {
                    hydrating = false;
                    loading.setVisibility(View.GONE);
                    setStatus("Refresh failed: " + error.getMessage());
                    Toast.makeText(this, "Failed to refresh progress", Toast.LENGTH_LONG).show();
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

        applyProgressState(progress, true, false);
    }

    private void applyProgressState(JSONObject progress, boolean scrollToLast, boolean showFeedback) {
        openedSentenceIds.clear();
        readSentenceIds.clear();
        scoredSentenceIds.clear();
        explanationUsedSentenceIds.clear();

        lastSentenceId = progress.has("lastSentenceId") && !progress.isNull("lastSentenceId")
                ? progress.optInt("lastSentenceId")
                : null;
        addAll(openedSentenceIds, progress.optJSONArray("openedSentenceIds"));
        addAll(readSentenceIds, progress.optJSONArray("readSentenceIds"));
        addAll(scoredSentenceIds, progress.optJSONArray("scoredSentenceIds"));
        addAll(explanationUsedSentenceIds, progress.optJSONArray("explanationUsedSentenceIds"));
        greenScore = progress.has("greenScore") ? progress.optInt("greenScore") : deriveGreenScore();
        redScore = progress.has("redScore") ? progress.optInt("redScore") : deriveRedScore();
        renderChapter();
        updateScores();
        loading.setVisibility(View.GONE);
        hydrating = false;
        setStatus("");
        if (showFeedback) {
            Toast.makeText(this, "Progress refreshed", Toast.LENGTH_SHORT).show();
        }

        if (scrollToLast && lastSentenceId != null) {
            mainHandler.postDelayed(() -> scrollToSentence(lastSentenceId), 250);
        } else {
            mainHandler.postDelayed(this::markVisibleSentencesAsRead, 250);
        }
    }

    private void renderChapter() {
        titleView.setText(chapterTitle.isEmpty() ? "MDReader" : chapterTitle);
        sentenceList.removeAllViews();
        sentenceViews.clear();
        selectableTextViews.clear();

        int currentParagraph = -1;
        for (Sentence sentence : sentences) {
            if (sentence.paragraphId != currentParagraph) {
                currentParagraph = sentence.paragraphId;
                TextView label = new TextView(this);
                label.setText("P");
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
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rowParams);

        View readStrip = new View(this);
        readStrip.setBackgroundColor(markerColor(sentence.id));
        row.addView(readStrip, new LinearLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(8), dp(10));
        row.addView(card, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView text = new TextView(this);
        text.setText(sentence.text);
        text.setTextSize(17);
        text.setLineSpacing(dp(2), 1.05f);
        text.setTextColor(Color.rgb(39, 38, 34));
        enableTextSelection(text);
        card.addView(text);

        TextView explanation = new TextView(this);
        explanation.setText(sentence.explanation);
        explanation.setTextSize(15);
        explanation.setTextColor(Color.rgb(76, 70, 60));
        explanation.setPadding(0, dp(10), 0, dp(8));
        enableTextSelection(explanation);
        explanation.setVisibility(isExplanationOpen(sentence.id) ? View.VISIBLE : View.GONE);
        card.addView(explanation);

        View.OnClickListener toggleListener = v -> {
            toggleSentenceExplanation(sentence.id);
            explanation.setVisibility(isExplanationOpen(sentence.id) ? View.VISIBLE : View.GONE);
            updateSentenceMarker(row, sentence.id);
            clearAllTextSelection();
        };
        attachDoubleTapToggle(row, toggleListener);
        attachDoubleTapToggle(card, toggleListener);
        attachDoubleTapToggle(text, toggleListener);
        attachDoubleTapToggle(explanation, toggleListener);

        return row;
    }

    private void attachDoubleTapToggle(View view, View.OnClickListener toggleListener) {
        final boolean[] consumeDoubleTap = {false};
        final boolean[] consumeGestureUntilUp = {false};
        GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent event) {
                consumeDoubleTap[0] = true;
                consumeGestureUntilUp[0] = true;
                toggleListener.onClick(view);
                return true;
            }
        });

        view.setOnTouchListener((target, event) -> {
            consumeDoubleTap[0] = false;
            detector.onTouchEvent(event);
            boolean consume = consumeDoubleTap[0] || consumeGestureUntilUp[0];
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                consumeGestureUntilUp[0] = false;
            }
            return consume;
        });
    }

    private boolean isExplanationOpen(int sentenceId) {
        return openedSentenceIds.contains(sentenceId);
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

    private void markVisibleSentencesAsRead() {
        if (hydrating || sentenceViews.isEmpty()) {
            return;
        }

        int threshold = scrollView.getScrollY() + Math.round(scrollView.getHeight() * READ_TRIGGER_FRACTION);
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
            updateSentenceMarker(card, sentenceId);
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

    private void updateScores() {
        greenScoreView.setText(String.valueOf(greenScore));
        redScoreView.setText(String.valueOf(redScore));
    }

    private void updateSentenceMarker(View row, int sentenceId) {
        if (!(row instanceof LinearLayout)) {
            return;
        }
        View strip = ((LinearLayout) row).getChildAt(0);
        strip.setBackgroundColor(markerColor(sentenceId));
    }

    private int markerColor(int sentenceId) {
        if (openedSentenceIds.contains(sentenceId)) {
            return Color.rgb(126, 34, 206);
        }
        if (readSentenceIds.contains(sentenceId)) {
            return Color.rgb(16, 185, 129);
        }
        return Color.TRANSPARENT;
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
                mainHandler.post(() -> setStatus("Save failed: " + error.getMessage()));
            }
        });
    }

    private JSONObject buildProgressJson(String progressKey) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("chapterId", progressKey);
            payload.put("lastSentenceId", lastSentenceId == null ? JSONObject.NULL : lastSentenceId);
            payload.put("totalScore", greenScore + redScore);
            payload.put("greenScore", greenScore);
            payload.put("redScore", redScore);
            payload.put("manualRedScore", 0);
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
            scrollView.smoothScrollTo(0, Math.max(0, card.getTop() - topSafeAreaInset - dp(56)));
        }
    }

    private void enableTextSelection(TextView textView) {
        textView.setTextIsSelectable(true);
        textView.setCustomSelectionActionModeCallback(createSelectionActionCallback(textView));
        if (!selectableTextViews.contains(textView)) {
            selectableTextViews.add(textView);
        }
    }

    private ActionMode.Callback createSelectionActionCallback(TextView textView) {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                Intent intent = item.getIntent();
                if (intent != null && Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
                    launchProcessTextAction(textView, mode, intent);
                    return true;
                }

                if (isTranslateAction(item)) {
                    mainHandler.postDelayed(
                            () -> clearTextSelection(textView, mode),
                            300
                    );
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mainHandler.post(() -> clearTextSelection(textView, null));
            }
        };
    }

    private void launchProcessTextAction(TextView textView, ActionMode mode, Intent sourceIntent) {
        int selectionStart = textView.getSelectionStart();
        int selectionEnd = textView.getSelectionEnd();
        int start = Math.max(0, Math.min(selectionStart, selectionEnd));
        int end = Math.min(textView.length(), Math.max(selectionStart, selectionEnd));
        CharSequence selectedText = start < end
                ? textView.getText().subSequence(start, end)
                : textView.getText();

        Intent intent = new Intent(sourceIntent);
        intent.putExtra(Intent.EXTRA_PROCESS_TEXT, selectedText);
        intent.putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
        mode.finish();
        startActivityForResult(intent, TRANSLATE_TEXT_REQUEST);
    }

    private boolean isTranslateAction(MenuItem item) {
        CharSequence title = item.getTitle();
        if (title != null) {
            String normalizedTitle = title.toString().toLowerCase(Locale.ROOT);
            if (normalizedTitle.contains("translate") || normalizedTitle.contains("翻译")) {
                return true;
            }
        }

        Intent intent = item.getIntent();
        return intent != null && Intent.ACTION_PROCESS_TEXT.equals(intent.getAction());
    }

    private void clearAllTextSelection() {
        for (TextView textView : selectableTextViews) {
            clearTextSelection(textView, null);
        }
    }

    private void clearTextSelection(TextView textView, ActionMode mode) {
        if (mode != null) {
            mode.finish();
        }

        CharSequence text = textView.getText();
        if (text instanceof Spannable) {
            Selection.removeSelection((Spannable) text);
        }
        if (textView.isFocused()) {
            textView.clearFocus();
        }
    }

    private boolean hasActiveTextSelection() {
        for (TextView textView : selectableTextViews) {
            int start = textView.getSelectionStart();
            int end = textView.getSelectionEnd();
            if (start >= 0 && end > start) {
                return true;
            }
        }
        return false;
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

    private String readText(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
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
