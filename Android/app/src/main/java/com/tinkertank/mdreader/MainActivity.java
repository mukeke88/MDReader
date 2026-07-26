package com.tinkertank.mdreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.os.Build;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.DisplayCutout;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.Selection;
import android.text.Spannable;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.textclassifier.TextClassifier;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
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
    private static final String DEFAULT_USER_ID = "default";
    private static final String PREFS_NAME = "mdreader";
    private static final String PREF_USER_ID = "userId";
    private static final String PREF_CHAPTER_ID = "chapterId";
    private static final int IMPORT_MARKDOWN_REQUEST = 10;
    private static final String EUDIC_PACKAGE = "com.qianyan.eudic";
    private static final float READ_TRIGGER_FRACTION = 0.08f;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Sentence> sentences = new ArrayList<>();
    private final List<ChapterOption> chapterOptions = new ArrayList<>();
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
    private String activeUserId = DEFAULT_USER_ID;
    private String chapterTitle = "";
    private Integer lastSentenceId = null;
    private int topSafeAreaInset = 0;
    private int greenScore = 0;
    private int redScore = 0;
    private boolean hydrating = false;
    private boolean clearSelectionWhenFocusReturns = false;
    private DirectLookupActionMode activeSelectionMode;
    private final Runnable saveRunnable = this::persistProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiBase = normalizeApiBase(getString(R.string.api_base_url));
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        activeUserId = normalizeUserId(preferences.getString(PREF_USER_ID, DEFAULT_USER_ID));
        activeChapterId = preferences.getString(PREF_CHAPTER_ID, DEFAULT_CHAPTER_ID);
        buildUi();
        loadChapterOptions(false);
        loadPage(activeChapterId);
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
        if (requestCode != IMPORT_MARKDOWN_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        promptForImportTitle(data.getData());
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
        headerRow.addView(settingsButton(), new LinearLayout.LayoutParams(dp(32), dp(32)));

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

    private ImageButton settingsButton() {
        ImageButton button = new ImageButton(this);
        button.setImageResource(android.R.drawable.ic_menu_manage);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setColorFilter(Color.rgb(36, 35, 32));
        button.setContentDescription("Settings");
        button.setPadding(dp(6), dp(6), dp(6), dp(6));
        button.setOnClickListener(v -> openSettings());
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

    private void openSettings() {
        if (chapterOptions.isEmpty()) {
            loadChapterOptions(true);
            return;
        }
        showSettingsDialog();
    }

    private void showSettingsDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), 0);

        TextView userLabel = dialogLabel("User");
        EditText userInput = new EditText(this);
        userInput.setSingleLine(true);
        userInput.setInputType(InputType.TYPE_CLASS_TEXT);
        userInput.setText(activeUserId);
        userInput.setSelectAllOnFocus(true);

        TextView documentLabel = dialogLabel("Document");
        Spinner documentSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                chapterOptionLabels()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        documentSpinner.setAdapter(adapter);
        int selectedIndex = findChapterOptionIndex(activeChapterId);
        if (selectedIndex >= 0) {
            documentSpinner.setSelection(selectedIndex);
        }

        layout.addView(userLabel);
        layout.addView(userInput);
        layout.addView(documentLabel);
        layout.addView(documentSpinner);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", null)
                .create();
        dialog.setOnShowListener(currentDialog ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    String nextUserId = normalizeUserId(userInput.getText().toString());
                    ChapterOption selectedChapter = chapterOptions.get(documentSpinner.getSelectedItemPosition());
                    boolean changed = !nextUserId.equals(activeUserId)
                            || !selectedChapter.id.equals(activeChapterId);
                    activeUserId = nextUserId;
                    activeChapterId = selectedChapter.id;
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putString(PREF_USER_ID, activeUserId)
                            .putString(PREF_CHAPTER_ID, activeChapterId)
                            .apply();
                    dialog.dismiss();
                    if (changed) {
                        loadPage(activeChapterId);
                    }
                })
        );
        dialog.show();
    }

    private TextView dialogLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.rgb(95, 88, 76));
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(0, dp(10), 0, 0);
        return label;
    }

    private List<String> chapterOptionLabels() {
        List<String> labels = new ArrayList<>();
        for (ChapterOption option : chapterOptions) {
            labels.add(option.title.isEmpty() ? option.id : option.title);
        }
        return labels;
    }

    private int findChapterOptionIndex(String chapterId) {
        for (int i = 0; i < chapterOptions.size(); i++) {
            if (chapterOptions.get(i).id.equals(chapterId)) {
                return i;
            }
        }
        return -1;
    }

    private void loadChapterOptions(boolean openSettingsAfterLoad) {
        setStatus(openSettingsAfterLoad ? "Loading documents..." : statusView.getText().toString());
        executor.execute(() -> {
            try {
                JSONArray chapters = getJsonArray("/chapter");
                List<ChapterOption> loadedOptions = new ArrayList<>();
                for (int i = 0; i < chapters.length(); i++) {
                    JSONObject item = chapters.optJSONObject(i);
                    if (item != null) {
                        loadedOptions.add(new ChapterOption(
                                item.optString("id", DEFAULT_CHAPTER_ID),
                                item.optString("title", "")
                        ));
                    }
                }
                mainHandler.post(() -> {
                    chapterOptions.clear();
                    chapterOptions.addAll(loadedOptions);
                    if (chapterOptions.isEmpty()) {
                        chapterOptions.add(new ChapterOption(activeChapterId, chapterTitle));
                    }
                    if (openSettingsAfterLoad) {
                        setStatus("");
                        showSettingsDialog();
                    }
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (chapterOptions.isEmpty()) {
                        chapterOptions.add(new ChapterOption(activeChapterId, chapterTitle));
                    }
                    setStatus(openSettingsAfterLoad ? "Document list failed: " + error.getMessage() : "");
                    if (openSettingsAfterLoad) {
                        showSettingsDialog();
                    }
                });
            }
        });
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

    private void promptForImportTitle(Uri uri) {
        EditText input = new EditText(this);
        input.setHint("Chapter Title");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setPadding(dp(16), dp(8), dp(16), dp(8));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Chapter Title")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Import", null)
                .create();
        dialog.setOnShowListener(currentDialog ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    String title = input.getText().toString().trim();
                    if (title.isEmpty()) {
                        input.setError("Enter a chapter title");
                        return;
                    }
                    dialog.dismiss();
                    importMarkdownFromUri(uri, title);
                })
        );
        dialog.show();
    }

    private void setStatus(String text) {
        statusView.setText(text);
        statusView.setVisibility(text == null || text.trim().isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void importMarkdownFromUri(Uri uri, String title) {
        hydrating = true;
        loading.setVisibility(View.VISIBLE);
        setStatus("Importing Markdown...");
        executor.execute(() -> {
            try {
                String markdown;
                try (InputStream stream = getContentResolver().openInputStream(uri)) {
                    markdown = readText(stream);
                }

                JSONObject payload = new JSONObject();
                payload.put("title", title.trim());
                payload.put("markdown", markdown);
                JSONObject chapter = postJson("/chapter/import", payload);
                mainHandler.post(() -> {
                    applyChapterState(chapter, new JSONObject());
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putString(PREF_CHAPTER_ID, activeChapterId)
                            .apply();
                    loadChapterOptions(false);
                });
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

    private void loadPage(String chapterId) {
        hydrating = true;
        loading.setVisibility(View.VISIBLE);
        setStatus("Loading from " + apiBase);
        executor.execute(() -> {
            try {
                JSONObject chapter = getJson("/chapter/" + encode(chapterId));
                JSONObject progress = getProgressWithFallback(chapter);
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
        hydrating = true;
        mainHandler.removeCallbacks(saveRunnable);
        loading.setVisibility(View.VISIBLE);
        setStatus("Refreshing progress...");
        executor.execute(() -> {
            try {
                JSONObject progress = getJson(progressPath(activeChapterId));
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

        TextView text = new SelectableReaderTextView(this);
        text.setText(sentence.text);
        text.setTextSize(17);
        text.setLineSpacing(dp(2), 1.05f);
        text.setTextColor(Color.rgb(39, 38, 34));
        enableTextSelection(text);
        card.addView(text);

        TextView explanation = new SelectableReaderTextView(this);
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

        if (!scrollView.canScrollVertically(1)) {
            markSentencesUpTo(lastSentenceId());
            return;
        }

        int threshold = scrollView.getScrollY() + Math.round(scrollView.getHeight() * READ_TRIGGER_FRACTION);
        int latestVisibleId = -1;
        for (Sentence sentence : sentences) {
            View card = sentenceViews.get(sentence.id);
            if (card != null && card.getBottom() <= threshold) {
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

    private int lastSentenceId() {
        return sentences.isEmpty() ? -1 : sentences.get(sentences.size() - 1).id;
    }

    private void handleSentenceRead(int sentenceId) {
        boolean changed = readSentenceIds.add(sentenceId);
        if (lastSentenceId == null || sentenceId > lastSentenceId) {
            lastSentenceId = sentenceId;
            changed = true;
        }
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
        JSONObject payload = buildProgressJson();
        executor.execute(() -> {
            try {
                postJson(progressPath(activeChapterId), payload);
            } catch (Exception error) {
                mainHandler.post(() -> setStatus("Save failed: " + error.getMessage()));
            }
        });
    }

    private JSONObject buildProgressJson() {
        JSONObject payload = new JSONObject();
        try {
            payload.put("userId", activeUserId);
            payload.put("chapterId", activeChapterId);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            textView.setTextClassifier(TextClassifier.NO_OP);
        }
        textView.setCustomSelectionActionModeCallback(createSelectionActionCallback(textView));
        if (!selectableTextViews.contains(textView)) {
            selectableTextViews.add(textView);
        }
    }

    private ActionMode startPrivateSelectionActionMode(
            TextView textView,
            ActionMode.Callback callback,
            int type
    ) {
        if (activeSelectionMode != null
                && !activeSelectionMode.isFinished()
                && activeSelectionMode.isFor(textView)) {
            return activeSelectionMode;
        }
        if (activeSelectionMode != null) {
            activeSelectionMode.finish();
        }

        DirectLookupActionMode mode = new DirectLookupActionMode(textView, callback, type);
        if (!mode.start()) {
            return null;
        }
        activeSelectionMode = mode;
        return mode;
    }

    private ActionMode.Callback createSelectionActionCallback(TextView textView) {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mainHandler.post(() -> clearTextSelection(textView, null));
            }
        };
    }

    private Intent createEudicProcessTextIntent() {
        Intent intent = new Intent(Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain")
                .setPackage(EUDIC_PACKAGE)
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
        ResolveInfo resolveInfo = getPackageManager().resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
        );
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return null;
        }

        intent.setClassName(
                resolveInfo.activityInfo.packageName,
                resolveInfo.activityInfo.name
        );
        return intent;
    }

    private Intent createEudicPeekIntent(CharSequence selectedText) {
        Uri uri = new Uri.Builder()
                .scheme("eudic")
                .authority("peek")
                .appendPath(selectedText.toString())
                .build();
        return new Intent(Intent.ACTION_VIEW, uri).setPackage(EUDIC_PACKAGE);
    }

    private void launchEudicLookup(TextView textView, ActionMode mode) {
        CharSequence selectedText = getSelectedText(textView);
        Intent peekIntent = createEudicPeekIntent(selectedText);
        Intent processTextIntent = createEudicProcessTextIntent();
        if (processTextIntent != null) {
            processTextIntent.putExtra(Intent.EXTRA_PROCESS_TEXT, selectedText);
            processTextIntent.putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
        }

        mode.finish();
        mainHandler.post(() -> {
            try {
                startActivity(peekIntent);
            } catch (ActivityNotFoundException | SecurityException peekError) {
                if (processTextIntent == null) {
                    Toast.makeText(
                            MainActivity.this,
                            "无法打开欧路词典，请确认已安装最新版",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }
                try {
                    startActivity(processTextIntent);
                } catch (ActivityNotFoundException | SecurityException processTextError) {
                    Toast.makeText(
                            MainActivity.this,
                            "无法打开欧路词典，请确认已安装最新版",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }
        });
    }

    private CharSequence getSelectedText(TextView textView) {
        int selectionStart = textView.getSelectionStart();
        int selectionEnd = textView.getSelectionEnd();
        int start = Math.max(0, Math.min(selectionStart, selectionEnd));
        int end = Math.min(textView.length(), Math.max(selectionStart, selectionEnd));
        return start < end
                ? textView.getText().subSequence(start, end)
                : textView.getText();
    }

    private void clearAllTextSelection() {
        for (TextView textView : selectableTextViews) {
            clearTextSelection(textView, null);
        }
    }

    private void clearTextSelection(TextView textView, ActionMode mode) {
        if (mode != null) {
            mode.finish();
        } else if (activeSelectionMode != null && activeSelectionMode.isFor(textView)) {
            activeSelectionMode.finish();
        }

        CharSequence text = textView.getText();
        if (text instanceof Spannable) {
            Selection.removeSelection((Spannable) text);
        }
        if (textView.isFocused()) {
            textView.clearFocus();
        }
    }

    private final class SelectableReaderTextView extends TextView {
        SelectableReaderTextView(Context context) {
            super(context);
        }

        @Override
        public ActionMode startActionMode(ActionMode.Callback callback) {
            return startPrivateSelectionActionMode(this, callback, ActionMode.TYPE_PRIMARY);
        }

        @Override
        public ActionMode startActionMode(ActionMode.Callback callback, int type) {
            return startPrivateSelectionActionMode(this, callback, type);
        }
    }

    private final class DirectLookupActionMode extends ActionMode {
        private final TextView textView;
        private final ActionMode.Callback callback;
        private final Menu menu;
        private CharSequence title;
        private CharSequence subtitle;
        private View customView;
        private boolean finished;
        private final Runnable lookupRunnable;

        DirectLookupActionMode(TextView textView, ActionMode.Callback callback, int type) {
            this.textView = textView;
            this.callback = callback;
            lookupRunnable = () -> launchEudicLookup(this.textView, this);
            setType(type);

            PopupMenu menuHolder = new PopupMenu(MainActivity.this, textView);
            menu = menuHolder.getMenu();
        }

        boolean start() {
            if (!callback.onCreateActionMode(this, menu)) {
                finished = true;
                return false;
            }
            callback.onPrepareActionMode(this, menu);
            mainHandler.post(lookupRunnable);
            return true;
        }

        boolean isFor(TextView candidate) {
            return textView == candidate;
        }

        boolean isFinished() {
            return finished;
        }

        @Override
        public void invalidate() {
            if (!finished) {
                callback.onPrepareActionMode(this, menu);
            }
        }

        @Override
        public void finish() {
            if (finished) {
                return;
            }
            finished = true;
            mainHandler.removeCallbacks(lookupRunnable);
            if (activeSelectionMode == this) {
                activeSelectionMode = null;
            }
            callback.onDestroyActionMode(this);
        }

        @Override
        public void setTitle(CharSequence value) {
            title = value;
        }

        @Override
        public void setTitle(int resId) {
            setTitle(getText(resId));
        }

        @Override
        public void setSubtitle(CharSequence value) {
            subtitle = value;
        }

        @Override
        public void setSubtitle(int resId) {
            setSubtitle(getText(resId));
        }

        @Override
        public void setCustomView(View view) {
            customView = view;
        }

        @Override
        public Menu getMenu() {
            return menu;
        }

        @Override
        public CharSequence getTitle() {
            return title;
        }

        @Override
        public CharSequence getSubtitle() {
            return subtitle;
        }

        @Override
        public View getCustomView() {
            return customView;
        }

        @Override
        public MenuInflater getMenuInflater() {
            return new MenuInflater(MainActivity.this);
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

    private JSONArray getJsonArray(String path) throws Exception {
        HttpURLConnection connection = openConnection(path, "GET");
        return readJsonArray(connection);
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

    private JSONObject getProgressWithFallback(JSONObject chapter) throws Exception {
        String chapterId = chapter.optString("chapterId", activeChapterId);
        JSONObject progress = getJson(progressPath(chapterId));
        String legacyProgressKey = buildProgressKey(chapter.optString("title", ""));
        if (!hasSavedProgress(progress)
                && legacyProgressKey != null
                && !legacyProgressKey.equals(chapterId)) {
            progress = getJson(progressPath(legacyProgressKey));
        }
        return progress;
    }

    private boolean hasSavedProgress(JSONObject progress) {
        return progress.optInt("lastSentenceId", 0) > 0
                || progress.optInt("totalScore", 0) > 0
                || progress.optInt("greenScore", 0) > 0
                || progress.optInt("redScore", 0) > 0
                || progress.optInt("manualRedScore", 0) > 0
                || jsonArrayLength(progress, "openedSentenceIds") > 0
                || jsonArrayLength(progress, "readSentenceIds") > 0
                || jsonArrayLength(progress, "scoredSentenceIds") > 0
                || jsonArrayLength(progress, "explanationUsedSentenceIds") > 0;
    }

    private int jsonArrayLength(JSONObject object, String key) {
        JSONArray array = object.optJSONArray(key);
        return array == null ? 0 : array.length();
    }

    private String progressPath(String chapterId) throws Exception {
        return "/progress/" + encode(chapterId) + "?userId=" + encode(activeUserId);
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

    private String normalizeUserId(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? DEFAULT_USER_ID : normalized;
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

    private JSONArray readJsonArray(HttpURLConnection connection) throws Exception {
        int statusCode = connection.getResponseCode();
        InputStream stream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readAll(stream);
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("HTTP " + statusCode + " " + body);
        }
        if (body == null || body.trim().isEmpty()) {
            return new JSONArray();
        }
        return new JSONArray(body);
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

    private static final class ChapterOption {
        final String id;
        final String title;

        ChapterOption(String id, String title) {
            this.id = id;
            this.title = title == null ? "" : title;
        }
    }
}
