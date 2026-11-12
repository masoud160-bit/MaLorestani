package ir.assetaudit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int OPEN_XLSX = 100;
    private static final int SAVE_REPORT = 101;
    // v2.0: grounded in the two supplied visual samples and selected portrait design.
    private static final int BACKGROUND = Color.rgb(25, 30, 36); // #191E24; user's background sample
    private static final int NAVY = Color.rgb(20, 30, 42);
    private static final int PANEL = Color.rgb(28, 41, 55);
    private static final int PANEL_ALT = Color.rgb(33, 50, 67);
    private static final int BORDER = Color.rgb(62, 89, 111);
    private static final int TEXT = Color.rgb(244, 247, 250);
    private static final int BLUE = Color.rgb(80, 155, 213); // #509BD5; user's blue sample
    private static final int GREEN = Color.rgb(24, 150, 73);
    private static final int ORANGE = Color.rgb(201, 150, 64); // restrained gold, flag only
    private static final int RED = Color.rgb(179, 97, 94);
    private static final int MUTED = Color.rgb(176, 190, 206);
    private static final int MAX_IMPORT_BYTES = 25 * 1024 * 1024;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private AuditDb database;
    private ScrollView screenScroll;
    private ScrollView menuScreen;
    private LinearLayout flagArea;
    private LinearLayout mismatchArea;
    private EditText surveyInput;
    private Button saveFlagButton;
    private Button clearFlagButton;
    private Button mismatchChooseButton;
    private LinearLayout resultDetail;
    private TextView[] statValues = new TextView[5];
    private boolean isMenuVisible;

    private TextView overview;
    private TextView recentList;
    private EditText plateInput;
    private EditText actualInput;
    private TextView foundTitle;
    private TextView foundDescription;
    private TextView previousObservation;
    private LinearLayout resultPanel;
    private Button matchedButton;
    private Button flagButton;
    private TextView flagHint;
    private Button mismatchButton;
    private Button extraButton;
    private Button removeButton;
    private ImageView lookupButton;
    private LinearLayout actionButtons;
    private ProgressBar loading;
    private File pendingImport;
    private String selectedPlate;
    private AuditDb.Item selectedItem;
    private boolean busy;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(0);
        database = new AuditDb(this);
        buildScreen();
        refreshDashboard();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        screenScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout root = vertical();
        root.setPadding(dp(12), dp(8), dp(12), dp(18));
        scroll.addView(root);

        // The photographed portrait reference: slim header, menu at upper right,
        // centered title, a compact status control at upper left.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(7), dp(4), dp(7), dp(5));
        header.setBackground(rounded(NAVY, dp(0), dp(0)));
        TextView menu = text("☰", 27, TEXT, false);
        menu.setGravity(Gravity.CENTER);
        menu.setContentDescription("باز کردن منو");
        menu.setOnClickListener(v -> showAuditMenu(menu));
        header.addView(menu, new LinearLayout.LayoutParams(dp(46), dp(45)));
        TextView heading = text("کنترل و تطبیق اثاثیه", 18, TEXT, true);
        heading.setGravity(Gravity.CENTER);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(45), 1f));
        ImageView info = new ImageView(this);
        info.setImageResource(R.drawable.ic_status_bell);
        info.setPadding(dp(12), dp(10), dp(12), dp(10));
        info.setContentDescription("مشاهده وضعیت بررسی");
        info.setOnClickListener(v -> showDetailsDialog("وضعیت بررسی", overview.getText().toString()));
        header.addView(info, new LinearLayout.LayoutParams(dp(46), dp(45)));
        root.addView(header, fullParams(dp(11)));

        overview = text("", 14, MUTED, false);
        recentList = text("", 14, MUTED, false);
        recentList.setLineSpacing(dp(5), 1f);

        // Plate input is high and spacious. The left icon acts as SEARCH, not an
        // undocumented camera scanner. IME search also performs the lookup.
        LinearLayout plateCard = vertical();
        plateCard.setPadding(dp(5), dp(4), dp(5), dp(5));
        plateCard.addView(text("شماره پلاک", 15, TEXT, true), fullParams(dp(6)));
        LinearLayout plateRow = new LinearLayout(this);
        plateRow.setOrientation(LinearLayout.HORIZONTAL);
        plateRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        plateRow.setGravity(Gravity.CENTER_VERTICAL);
        plateRow.setBackground(rounded(PANEL, dp(12), BLUE));
        plateInput = entry("شماره پلاک را وارد کنید");
        plateInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        plateInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        plateInput.setSingleLine(true);
        plateInput.setTextSize(23);
        plateInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        plateInput.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        plateInput.setBackgroundColor(Color.TRANSPARENT);
        plateInput.setPadding(dp(13), dp(4), dp(12), dp(4));
        plateRow.addView(plateInput, new LinearLayout.LayoutParams(0, dp(59), 1f));
        lookupButton = new ImageView(this);
        lookupButton.setImageResource(R.drawable.ic_search_plate);
        lookupButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        lookupButton.setPadding(dp(11), dp(11), dp(11), dp(11));
        lookupButton.setContentDescription("جست‌وجوی پلاک");
        lookupButton.setBackground(rounded(PANEL_ALT, dp(10), BORDER));
        plateRow.addView(lookupButton, new LinearLayout.LayoutParams(dp(56), dp(53)));
        plateCard.addView(plateRow);
        root.addView(plateCard, fullParams(dp(10)));
        lookupButton.setOnClickListener(v -> searchPlate());
        plateInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { searchPlate(); return true; }
            return false;
        });

        resultPanel = vertical();
        resultPanel.setPadding(dp(11), dp(12), dp(11), dp(12));
        resultPanel.setBackground(rounded(PANEL, dp(14), BORDER));
        TextView label = text("نتیجه جست‌وجو", 14, MUTED, false);
        resultPanel.addView(label, fullParams(dp(6)));
        LinearLayout resultRow = new LinearLayout(this);
        resultRow.setOrientation(LinearLayout.HORIZONTAL);
        resultRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        resultRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView assetGlyph = new ImageView(this);
        assetGlyph.setImageResource(R.drawable.ic_asset_desk);
        assetGlyph.setPadding(dp(11), dp(11), dp(11), dp(11));
        assetGlyph.setBackground(rounded(PANEL_ALT, dp(10), 0));
        resultRow.addView(assetGlyph, new LinearLayout.LayoutParams(dp(68), dp(73)));
        resultDetail = vertical();
        resultDetail.setPadding(dp(9), 0, 0, 0);
        foundTitle = text("", 19, TEXT, true);
        foundDescription = text("", 13, MUTED, false);
        foundDescription.setPadding(0, dp(5), 0, 0);
        resultDetail.addView(foundTitle);
        resultDetail.addView(foundDescription);
        resultRow.addView(resultDetail, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        resultPanel.addView(resultRow, fullParams(dp(8)));
        previousObservation = text("", 12, ORANGE, false);
        previousObservation.setVisibility(View.GONE);
        resultPanel.addView(previousObservation);
        root.addView(resultPanel, fullParams(dp(10)));
        resultPanel.setVisibility(View.GONE);

        // Three actions, same positions and proportions as approved drawing:
        // right = confirm, middle = mismatch, left = flag.
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        matchedButton = button("✓\nتأیید مشاهده", GREEN);
        matchedButton.setTextSize(14);
        actionRow.addView(matchedButton, weightedAction());
        mismatchChooseButton = outlinedButton("!\nثبت مغایرت", BLUE);
        actionRow.addView(mismatchChooseButton, weightedAction());
        flagButton = outlinedButton("⚑\nعلامت‌گذاری", ORANGE);
        actionRow.addView(flagButton, weightedAction());
        root.addView(actionRow, fullParams(dp(10)));
        actionRow.setTag("actionRow");
        actionRow.setVisibility(View.GONE);
        // Keep a field instead of recreating buttons after every lookup.
        actionButtons = actionRow;
        matchedButton.setOnClickListener(v -> submitObservation(AuditDb.MATCH));
        mismatchChooseButton.setOnClickListener(v -> {
            mismatchArea.setVisibility(mismatchArea.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            if (mismatchArea.getVisibility() == View.VISIBLE) {
                actualInput.requestFocus();
                actualInput.post(() -> {
                    InputMethodManager input = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (input != null) input.showSoftInput(actualInput, InputMethodManager.SHOW_IMPLICIT);
                });
            }
        });
        flagButton.setOnClickListener(v -> showFlagDialog());

        // Mismatch/extra entry is an expandable secondary operation, not a
        // permanent block pushing the confirm button underneath the keyboard.
        mismatchArea = card(PANEL);
        mismatchArea.addView(text("نام واقعی اثاثیه", 14, TEXT, true), fullParams(dp(6)));
        actualInput = entry("نام واقعی اثاثیه مشاهده‌شده");
        actualInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        actualInput.setSingleLine(false);
        actualInput.setMinLines(1);
        actualInput.setMaxLines(3);
        mismatchArea.addView(actualInput, fullParams(dp(8)));
        mismatchButton = button("ثبت مغایرت نام", BLUE);
        extraButton = button("ثبت پلاک خارج از فهرست", BLUE);
        mismatchArea.addView(mismatchButton, fullParams(dp(5)));
        mismatchArea.addView(extraButton, fullParams(dp(5)));
        removeButton = outlinedButton("حذف مشاهده قبلی این پلاک", RED);
        mismatchArea.addView(removeButton);
        mismatchButton.setOnClickListener(v -> submitObservation(AuditDb.MISMATCH));
        extraButton.setOnClickListener(v -> submitObservation(AuditDb.EXTRA));
        removeButton.setOnClickListener(v -> confirmRemove());
        mismatchArea.setVisibility(View.GONE);
        root.addView(mismatchArea, fullParams(dp(9)));

        flagArea = card(PANEL);
        flagArea.addView(text("شماره آمایش", 14, TEXT, true), fullParams(dp(5)));
        surveyInput = entry("شماره آمایش را وارد کنید ...");
        surveyInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        surveyInput.setSingleLine(true);
        surveyInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        surveyInput.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        flagArea.addView(surveyInput, fullParams(dp(9)));
        flagHint = text("", 12, MUTED, false);
        flagArea.addView(flagHint, fullParams(dp(5)));
        LinearLayout flagActions = new LinearLayout(this);
        flagActions.setOrientation(LinearLayout.HORIZONTAL);
        flagActions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        saveFlagButton = button("ثبت / ذخیره علامت", BLUE);
        clearFlagButton = outlinedButton("برداشتن علامت", RED);
        flagActions.addView(saveFlagButton, weightedAction());
        flagActions.addView(clearFlagButton, weightedAction());
        flagArea.addView(flagActions);
        saveFlagButton.setOnClickListener(v -> saveFlagFromInput());
        clearFlagButton.setOnClickListener(v -> removeFlagFromInput());
        flagArea.setVisibility(View.GONE);
        root.addView(flagArea, fullParams(dp(8)));

        // The overview is stacked with right-justified labels and left values,
        // without vertical dividers. Detailed metrics remain in the menu.
        LinearLayout status = vertical();
        status.setPadding(dp(8), dp(7), dp(8), dp(9));
        TextView statusHeading = text("▮ وضعیت کنترل", 15, TEXT, true);
        statusHeading.setTextColor(BLUE);
        status.addView(statusHeading, fullParams(dp(5)));
        String[] statNames = new String[]{"کل اقلام", "بررسی‌شده", "مطابق", "مغایرت", "علامت‌گذاری‌شده"};
        for (int i = 0; i < statNames.length; i++) {
            LinearLayout statRow = new LinearLayout(this);
            statRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            statRow.setOrientation(LinearLayout.HORIZONTAL);
            statRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView statLabel = text(statNames[i] + ":", 13, TEXT, false);
            statRow.addView(statLabel, new LinearLayout.LayoutParams(0, dp(26), 1f));
            TextView statValue = text("۰", 14, i == 2 ? GREEN : TEXT, i == 2);
            statValue.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            statValue.setTextDirection(View.TEXT_DIRECTION_LTR);
            statValues[i] = statValue;
            statRow.addView(statValue, new LinearLayout.LayoutParams(dp(88), dp(26)));
            status.addView(statRow);
        }
        root.addView(status, fullParams(dp(9)));

        loading = new ProgressBar(this);
        loading.setIndeterminate(true);
        loading.setVisibility(View.GONE);
        root.addView(loading, fullParams(dp(9)));
        setContentView(scroll);
        buildMenuScreen();
    }

    private LinearLayout.LayoutParams weightedAction() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(72), 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private GradientDrawable rounded(int color, int radius, int border) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius);
        if (border != 0) bg.setStroke(dp(1), border);
        return bg;
    }

    private Button outlinedButton(String caption, int line) {
        Button b = button(caption, PANEL);
        b.setTextColor(line == ORANGE ? ORANGE : TEXT);
        b.setBackground(rounded(PANEL, dp(10), line));
        b.setTextSize(14);
        return b;
    }

    private void buildMenuScreen() {
        ScrollView menu = new ScrollView(this);
        menuScreen = menu;
        menu.setFillViewport(true);
        menu.setBackgroundColor(BACKGROUND);
        menu.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout items = vertical();
        items.setPadding(dp(14), dp(18), dp(14), dp(16));
        menu.addView(items);

        LinearLayout masthead = vertical();
        masthead.setPadding(dp(18), dp(21), dp(18), dp(22));
        GradientDrawable mast = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(17, 35, 52), Color.rgb(25, 91, 133)});
        mast.setCornerRadius(dp(18));
        masthead.setBackground(mast);
        TextView emblem = text("◇", 36, TEXT, true);
        emblem.setGravity(Gravity.CENTER);
        masthead.addView(emblem, fullParams(dp(6)));
        TextView title = text("کنترل و تطبیق اثاثیه", 21, TEXT, true);
        title.setGravity(Gravity.CENTER);
        masthead.addView(title, fullParams(dp(4)));
        TextView subtitle = text("سامانه پایش و ممیزی دارایی‌های منقول", 13, TEXT, false);
        subtitle.setGravity(Gravity.CENTER);
        masthead.addView(subtitle);
        items.addView(masthead, fullParams(dp(23)));

        addMenuItem(items, "⌂", "صفحه اصلی", true, () -> showHomeScreen());
        addMenuItem(items, "☑", "کنترل و تطبیق اثاثیه", false, () -> showHomeScreen());
        addMenuItem(items, "▣", "ورود فایل اکسل", false, () -> {
            showHomeScreen();
            openExcelPicker();
        });
        addMenuItem(items, "▥", "دریافت گزارش", false, () -> {
            showHomeScreen();
            confirmExport();
        });
        addMenuItem(items, "◫", "وضعیت بررسی", false, () -> {
            showHomeScreen();
            showDetailsDialog("وضعیت بررسی", overview.getText().toString());
        });
        addMenuItem(items, "☷", "آخرین پلاک‌های بررسی‌شده", false, () -> {
            showHomeScreen();
            showDetailsDialog("آخرین پلاک‌های بررسی‌شده", recentList.getText().toString());
        });
        addMenuItem(items, "⚙", "تنظیمات", false, () -> {
            showHomeScreen();
            showDetailsDialog("تنظیمات", "حالت ورود سریع فعال است: پس از ثبت موفق، کادر پلاک پاک و صفحه‌کلید عددی برای پلاک بعدی باز می‌شود.\nاطلاعات برنامه روی همین گوشی نگهداری می‌شود.");
        });
        addMenuItem(items, "?", "راهنما", false, () -> {
            showHomeScreen();
            showDetailsDialog("راهنما", "ابتدا فایل اکسل را از منو وارد کنید. پلاک را بنویسید و نماد جست‌وجو را بزنید. سپس تأیید، ثبت مغایرت یا علامت‌گذاری را انجام دهید. برای پلاک خارج از فهرست، نام واقعی را وارد کنید. شماره آمایش اختیاری است و محدودیت تعداد رقم ندارد.");
        });
        addMenuItem(items, "ⓘ", "درباره برنامه", false, () -> {
            showHomeScreen();
            showDetailsDialog("درباره برنامه", "کنترل و تطبیق اثاثیه — نسخهٔ ۲٫۰\nعملکرد آفلاین، گزارش پنج‌برگه‌ای، حفظ علامت‌های فرسودگی و شماره آمایش.");
        });
    }

    private void addMenuItem(LinearLayout parent, String symbol, String label,
                             boolean active, Runnable operation) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(13), dp(8), dp(14), dp(8));
        if (active) {
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.rgb(42, 139, 195), BLUE});
            bg.setCornerRadius(dp(10));
            line.setBackground(bg);
        }
        TextView image = text(symbol, 24, TEXT, false);
        image.setGravity(Gravity.CENTER);
        line.addView(image, new LinearLayout.LayoutParams(dp(38), dp(41)));
        TextView title = text(label, 16, TEXT, active);
        title.setPadding(dp(7), 0, 0, 0);
        line.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        if (!active) {
            TextView chevron = text("‹", 25, MUTED, false);
            line.addView(chevron);
        }
        line.setOnClickListener(v -> operation.run());
        parent.addView(line, fullParams(dp(4)));
        if (!active) {
            View divider = new View(this);
            divider.setBackgroundColor(Color.rgb(40, 59, 76));
            LinearLayout.LayoutParams rule = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            rule.setMargins(dp(10), 0, dp(10), dp(5));
            parent.addView(divider, rule);
        }
    }

    private void showAuditMenu(View anchor) {
        if (busy) return;
        hideKeyboard(plateInput);
        isMenuVisible = true;
        setContentView(menuScreen);
    }

    private void showHomeScreen() {
        isMenuVisible = false;
        setContentView(screenScroll);
        screenScroll.post(() -> screenScroll.scrollTo(0, 0));
    }

    private void showDetailsDialog(String heading, String value) {
        TextView message = text(value, 16, TEXT, false);
        message.setGravity(Gravity.RIGHT | Gravity.TOP);
        message.setTextDirection(View.TEXT_DIRECTION_RTL);
        message.setLineSpacing(dp(7), 1.10f);
        message.setPadding(dp(17), dp(15), dp(17), dp(15));
        ScrollView panel = new ScrollView(this);
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        panel.addView(message);
        new AlertDialog.Builder(this).setTitle(heading).setView(panel)
                .setPositiveButton("بستن", null).show();
    }

    @Override public void onBackPressed() {
        if (isMenuVisible) showHomeScreen();
        else super.onBackPressed();
    }

    private void refreshDashboard() {
        AuditDb.Counts c = database.counts();
        overview.setText("کل پلاک‌های فهرست: " + c.inventory
                + "\nبررسی‌شده از فهرست: " + (c.matched + c.mismatched)
                + "\nمطابق: " + c.matched
                + "\nمغایرت نام: " + c.mismatched
                + "\nپلاک خارج از فهرست: " + c.extra
                + "\nمشاهده‌نشده: " + c.unseen()
                + "\nعلامت‌دار برای بررسی فرسودگی / خروج: " + c.flagged);
        statValues[0].setText(String.valueOf(c.inventory));
        statValues[1].setText(String.valueOf(c.matched + c.mismatched));
        statValues[2].setText(String.valueOf(c.matched));
        statValues[3].setText(String.valueOf(c.mismatched));
        statValues[4].setText(String.valueOf(c.flagged));
        List<AuditDb.Observation> recent = database.recent(8);
        if (recent.isEmpty()) recentList.setText("هنوز پلاکی ثبت نشده است.");
        else {
            StringBuilder list = new StringBuilder();
            for (AuditDb.Observation o : recent) {
                if (list.length() > 0) list.append('\n');
                list.append("• ").append(o.plate).append(" — ");
                list.append(o.status == AuditDb.MATCH ? "مطابق" :
                        o.status == AuditDb.MISMATCH ? "مغایرت نام" : "خارج از فهرست");
            }
            recentList.setText(list.toString());
        }
    }

    private void searchPlate() {
        if (busy) return;
        if (database.counts().inventory == 0) {
            new AlertDialog.Builder(this).setTitle("فهرست اثاثیه وارد نشده است")
                    .setMessage("برای شروع بررسی، از منو فایل اکسل اثاثیه را انتخاب کنید.")
                    .setPositiveButton("انتخاب فایل اکسل", (dialog, which) -> openExcelPicker())
                    .setNegativeButton("بعداً", null).show();
            return;
        }
        String plate = PlateNormalizer.normalize(plateInput.getText().toString());
        if (plate.isEmpty()) { plateInput.setError("پلاک را وارد کنید."); return; }
        selectedPlate = plate;
        selectedItem = database.lookup(plate);
        AuditDb.Observation previous = database.observation(plate);
        flagArea.setVisibility(View.GONE);
        mismatchArea.setVisibility(View.GONE);
        if (selectedItem == null) {
            foundTitle.setText("این پلاک در فهرست نیست");
            foundTitle.setTextColor(TEXT);
            foundDescription.setText("پلاک: " + plate + "\nنام مشاهده‌شده را برای ثبت وارد کنید.");
            actionButtons.setVisibility(View.GONE);
            mismatchArea.setVisibility(View.VISIBLE);
            mismatchButton.setVisibility(View.GONE);
            extraButton.setVisibility(View.VISIBLE);
        } else {
            foundTitle.setText(selectedItem.name);
            foundTitle.setTextColor(TEXT);
            foundDescription.setText("شماره پلاک: " + selectedItem.plate);
            actionButtons.setVisibility(View.VISIBLE);
            mismatchButton.setVisibility(View.VISIBLE);
            extraButton.setVisibility(View.GONE);
            updateFlagControls();
        }
        actualInput.setText(previous == null || previous.status == AuditDb.MATCH ? "" : previous.observedName);
        previousObservation.setVisibility(previous == null ? View.GONE : View.VISIBLE);
        removeButton.setVisibility(previous == null ? View.GONE : View.VISIBLE);
        if (previous != null) {
            previousObservation.setText("این پلاک قبلاً ثبت شده است؛ ثبت مجدد، نتیجه قبلی را اصلاح می‌کند.");
            // Keep deletion available for previous records without permanently
            // expanding the mismatch form in the usual, matched-item path.
            if (selectedItem != null) mismatchArea.setVisibility(View.VISIBLE);
        }
        resultPanel.setVisibility(View.VISIBLE);
        hideKeyboard(plateInput);
        screenScroll.post(() -> screenScroll.scrollTo(0, 0));
    }

    private void updateFlagControls() {
        if (selectedPlate == null || selectedItem == null) {
            flagButton.setVisibility(View.GONE);
            flagArea.setVisibility(View.GONE);
            return;
        }
        AuditDb.Flag flag = database.flag(selectedPlate);
        flagButton.setVisibility(View.VISIBLE);
        flagButton.setText(flag == null ? "⚑\nعلامت‌گذاری" : "⚑\nویرایش علامت");
        surveyInput.setText(flag == null ? "" : flag.surveyNumber);
        clearFlagButton.setVisibility(flag == null ? View.GONE : View.VISIBLE);
        flagHint.setText(flag == null ? "تعداد رقم محدود نیست؛ صفرهای ابتدایی حفظ می‌شوند."
                : "علامت قبلاً ثبت شده است. می‌توانید شماره آمایش را اصلاح کنید.");
        flagArea.setVisibility(flag == null ? View.GONE : View.VISIBLE);
    }

    private void showFlagDialog() {
        if (busy || selectedPlate == null || selectedItem == null) return;
        flagArea.setVisibility(View.VISIBLE);
        if (flagArea.getVisibility() == View.VISIBLE) {
            surveyInput.requestFocus();
            screenScroll.post(() -> screenScroll.smoothScrollTo(0, flagArea.getTop()));
        }
    }

    private void saveFlagFromInput() {
        if (busy || selectedPlate == null || selectedItem == null) return;
        String plate = selectedPlate;
        AuditDb.Flag old = database.flag(plate);
        try {
            String note = old == null ? "" : old.note;
            database.markForReview(plate, note, surveyInput.getText().toString());
            refreshDashboard();
            Toast.makeText(this, "علامت ثبت شد؛ وضعیت تطبیق تغییر نکرد.", Toast.LENGTH_SHORT).show();
            finishCurrentPlate();
        } catch (Exception error) { alert("ثبت علامت ناموفق بود: " + error.getMessage()); }
    }

    private void removeFlagFromInput() {
        if (busy || selectedPlate == null || selectedItem == null) return;
        String plate = selectedPlate;
        new AlertDialog.Builder(this).setTitle("برداشتن علامت")
                .setMessage("علامت فرسودگی / خروج این پلاک برداشته شود؟ وضعیت تطبیق تغییر نمی‌کند.")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("برداشتن", (dialog, which) -> {
                    database.unmarkForReview(plate);
                    refreshDashboard();
                    finishCurrentPlate();
                }).show();
    }

    private void finishCurrentPlate() {
        selectedPlate = null;
        selectedItem = null;
        resultPanel.setVisibility(View.GONE);
        actionButtons.setVisibility(View.GONE);
        mismatchArea.setVisibility(View.GONE);
        flagArea.setVisibility(View.GONE);
        actualInput.setText("");
        surveyInput.setText("");
        hideKeyboard(actualInput);
        hideKeyboard(surveyInput);
        focusNextPlate();
    }

    private void submitObservation(int status) {
        if (busy || selectedPlate == null) return;
        String observed = actualInput.getText().toString().trim();
        if (status != AuditDb.MATCH && observed.isEmpty()) {
            actualInput.setError("نام واقعی اثاثیه را وارد کنید.");
            actualInput.requestFocus();
            return;
        }
        String plate = selectedPlate;
        String value = status == AuditDb.MATCH ? selectedItem.name : observed;
        try {
            database.record(plate, value, status);
            Toast.makeText(this, "ثبت شد: " + plate, Toast.LENGTH_SHORT).show();
            refreshDashboard();
            finishCurrentPlate();
        } catch (Exception ex) { alert("ثبت ناموفق بود: " + ex.getMessage()); }
    }

    private void confirmRemove() {
        if (selectedPlate == null) return;
        String plate = selectedPlate;
        new AlertDialog.Builder(this).setTitle("حذف مشاهدهٔ قبلی")
                .setMessage("ثبت قبلی پلاک «" + plate + "» حذف شود؟ می‌توانید بعداً دوباره آن را بررسی کنید.")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("حذف ثبت", (d, w) -> {
                    database.removeObservation(plate);
                    refreshDashboard();
                    finishCurrentPlate();
                }).show();
    }

    private void openExcelPicker() {
        if (busy) return;
        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        open.addCategory(Intent.CATEGORY_OPENABLE);
        open.setType("*/*");
        open.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/octet-stream", "application/zip"});
        startActivityForResult(open, OPEN_XLSX);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode != RESULT_OK || intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        if (requestCode == OPEN_XLSX) prepareImport(uri);
        else if (requestCode == SAVE_REPORT) writeReport(uri);
    }

    private void prepareImport(Uri uri) {
        discardPendingImport();
        setBusy(true);
        worker.execute(() -> {
            File temp = null;
            try {
                temp = File.createTempFile("asset_inventory_", ".xlsx", getCacheDir());
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(temp)) {
                    if (in == null) throw new IOException("فایل انتخاب‌شده باز نشد.");
                    byte[] buffer = new byte[8192];
                    int count;
                    int total = 0;
                    while ((count = in.read(buffer)) >= 0) {
                        total += count;
                        if (total > MAX_IMPORT_BYTES) throw new IOException("حجم فایل نباید بیشتر از ۲۵ مگابایت باشد.");
                        out.write(buffer, 0, count);
                    }
                }
                List<XlsxReader.SheetInfo> sheets = XlsxReader.inspect(temp);
                File selected = temp;
                onUi(() -> {
                    pendingImport = selected;
                    setBusy(false);
                    if (sheets.size() == 1) {
                        loadSheet(selected, sheets.get(0));
                        return;
                    }
                    String[] names = new String[sheets.size()];
                    for (int i = 0; i < sheets.size(); i++) names[i] = sheets.get(i).name;
                    new AlertDialog.Builder(this).setTitle("برگهٔ فهرست اثاثیه را انتخاب کنید")
                            .setItems(names, (dialog, index) -> loadSheet(selected, sheets.get(index)))
                            .setNegativeButton("انصراف", (dialog, which) -> discardPendingImport()).show();
                });
            } catch (Exception ex) {
                if (temp != null) temp.delete();
                String message = ex.getMessage();
                onUi(() -> {
                    setBusy(false);
                    alert("فایل خوانده نشد. فرمت موردنیاز، اکسل جدید با پسوند xlsx است.\n\n" + message);
                });
            }
        });
    }

    private void loadSheet(File file, XlsxReader.SheetInfo sheet) {
        setBusy(true);
        worker.execute(() -> {
            try {
                List<List<String>> rows = XlsxReader.readRows(file, sheet);
                onUi(() -> { setBusy(false); selectHeader(rows); });
            } catch (Exception ex) {
                onUi(() -> { setBusy(false); alert("برگه خوانده نشد: " + ex.getMessage()); });
            }
        });
    }

    private void selectHeader(List<List<String>> rows) {
        AssetImportMapping.Mapping mapping = AssetImportMapping.detect(rows);
        if (mapping != null) {
            new AlertDialog.Builder(this)
                    .setTitle("ستون‌های گزارش اموال شناسایی شد")
                    .setMessage("ستون پلاک: «پلاک اموال» (شمارهٔ " + (mapping.plateColumn + 1)
                            + ")\nستون نام: «عنوان کالا» (شمارهٔ " + (mapping.nameColumn + 1)
                            + ")\n\nآیا با همین ستون‌ها فهرست را وارد کنیم؟")
                    .setPositiveButton("ادامه با این ستون‌ها", (dialog, which) ->
                            validateImport(rows, mapping.headerRow + 1,
                                    mapping.plateColumn, mapping.nameColumn))
                    .setNeutralButton("انتخاب دستی", (dialog, which) -> selectHeaderManually(rows))
                    .setNegativeButton("انصراف", null).show();
            return;
        }
        selectHeaderManually(rows);
    }

    private void selectHeaderManually(List<List<String>> rows) {
        int choices = Math.min(5, rows.size());
        String[] names = new String[choices + 1];
        names[0] = "بدون سطر عنوان (داده از سطر اول)";
        for (int i = 0; i < choices; i++) {
            StringBuilder preview = new StringBuilder();
            int max = Math.min(3, rows.get(i).size());
            for (int j = 0; j < max; j++) {
                if (preview.length() > 0) preview.append(" | ");
                preview.append(rows.get(i).get(j));
            }
            names[i + 1] = "سطر " + (i + 1) + ": " + trim(preview.toString(), 65);
        }
        new AlertDialog.Builder(this).setTitle("سطر عنوان ستون‌ها را مشخص کنید")
                .setItems(names, (d, chosen) -> chooseColumns(rows, chosen - 1))
                .setNegativeButton("انصراف", null).show();
    }

    private void chooseColumns(List<List<String>> rows, int headerIndex) {
        int max = 0;
        for (int r = 0; r < Math.min(rows.size(), 10); r++) max = Math.max(max, rows.get(r).size());
        if (max < 2) { alert("حداقل دو ستون برای پلاک و نام اثاثیه لازم است."); return; }
        String[] headers = new String[max];
        String[] labels = new String[max];
        for (int i = 0; i < max; i++) {
            headers[i] = headerIndex >= 0 && i < rows.get(headerIndex).size() ? rows.get(headerIndex).get(i) : "";
            labels[i] = "ستون " + (i + 1) + (headers[i].isEmpty() ? "" : " — " + trim(headers[i], 40));
        }
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(12), dp(18), dp(6));
        form.addView(text("ستون شماره پلاک", 15, TEXT, true));
        Spinner plateColumn = selector(labels);
        form.addView(plateColumn, fullParams(dp(10)));
        form.addView(text("ستون نام / شرح اثاثیه", 15, TEXT, true));
        Spinner nameColumn = selector(labels);
        form.addView(nameColumn);
        plateColumn.setSelection(guess(headers, new String[]{"پلاک", "شماره", "کد", "plate", "tag"}, 0));
        nameColumn.setSelection(guess(headers, new String[]{"عنوان کالا", "نام کالا", "نام اثاثیه",
                "شرح کالا", "نام", "شرح", "عنوان", "اثاثیه", "name", "description"}, 1));
        new AlertDialog.Builder(this).setTitle("ستون‌های فایل اکسل")
                .setView(form).setNegativeButton("انصراف", null)
                .setPositiveButton("بررسی فهرست", (d, which) -> {
                    int plate = plateColumn.getSelectedItemPosition();
                    int name = nameColumn.getSelectedItemPosition();
                    if (plate == name) { alert("ستون پلاک و نام باید متفاوت باشند."); return; }
                    validateImport(rows, headerIndex + 1, plate, name);
                }).show();
    }

    private void validateImport(List<List<String>> rows, int begin, int plateCol, int nameCol) {
        setBusy(true);
        worker.execute(() -> {
            try {
                Set<String> seen = new HashSet<>();
                List<AuditDb.Item> items = new ArrayList<>();
                for (int i = begin; i < rows.size(); i++) {
                    List<String> row = rows.get(i);
                    String plate = PlateNormalizer.normalize(value(row, plateCol));
                    String name = value(row, nameCol).trim();
                    if (plate.isEmpty()) continue;
                    if (name.isEmpty()) throw new IOException("نام اثاثیه برای پلاک «" + plate + "» خالی است (سطر " + (i + 1) + ").");
                    if (!seen.add(plate)) throw new IOException("پلاک تکراری در اکسل: " + plate + " (سطر " + (i + 1) + ").");
                    items.add(new AuditDb.Item(plate, name));
                }
                if (items.isEmpty()) throw new IOException("هیچ پلاک معتبری در ستون انتخاب‌شده وجود ندارد.");
                onUi(() -> {
                    setBusy(false);
                    confirmNewInventory(items);
                });
            } catch (Exception ex) {
                onUi(() -> { setBusy(false); alert("فهرست وارد نشد: " + ex.getMessage()); });
            }
        });
    }

    private void confirmNewInventory(List<AuditDb.Item> items) {
        AuditDb.Counts old = database.counts();
        String warning = old.inventory > 0
                ? "\n\nتوجه: فهرست فعلی و همهٔ " + (old.matched + old.mismatched + old.extra)
                + " نتیجهٔ ثبت‌شده و " + old.flagged + " علامت فرسودگی / خروج پاک می‌شوند. "
                + "پیش از ادامه، حتماً از اطلاعات لازم گزارش بگیرید."
                : "";
        new AlertDialog.Builder(this).setTitle("تأیید ورود فهرست")
                .setMessage(items.size() + " پلاک معتبر شناسایی شد." + warning)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("تأیید و شروع", (dialog, which) -> {
                    setBusy(true);
                    worker.execute(() -> {
                        try {
                            database.replaceInventory(items);
                            onUi(() -> {
                                setBusy(false);
                                refreshDashboard();
                                finishCurrentPlate();
                                discardPendingImport();
                                Toast.makeText(this, "فهرست جدید آمادهٔ کنترل است.", Toast.LENGTH_LONG).show();
                            });
                        } catch (Exception ex) {
                            onUi(() -> { setBusy(false); alert("خطا در ذخیرهٔ فهرست: " + ex.getMessage()); });
                        }
                    });
                }).show();
    }

    private void confirmExport() {
        if (busy) return;
        AuditDb.Counts c = database.counts();
        if (c.inventory == 0) { alert("ابتدا فهرست اثاثیه را وارد کنید."); return; }
        new AlertDialog.Builder(this).setTitle("تهیهٔ گزارش پنج‌برگه‌ای")
                .setMessage("مطابق: " + c.matched + " مورد\n"
                        + "مغایرت نام: " + c.mismatched + " مورد\n"
                        + "پلاک‌های مشاهده‌نشده: " + c.unseen() + " مورد\n"
                        + "پلاک‌های خارج از فهرست: " + c.extra + " مورد\n"
                        + "علامت‌دار برای بررسی فرسودگی / خروج: " + c.flagged + " مورد\n\n"
                        + "اقلام مشاهده‌نشده فقط پس از پایان بازدید، نامزد بررسی مفقودی هستند.")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("انتخاب محل ذخیره", (dialog, which) -> {
                    Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    save.addCategory(Intent.CATEGORY_OPENABLE);
                    save.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    save.putExtra(Intent.EXTRA_TITLE, "AssetAudit_" +
                            new SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(new Date()) + ".xlsx");
                    startActivityForResult(save, SAVE_REPORT);
                }).show();
    }

    private void writeReport(Uri destination) {
        setBusy(true);
        worker.execute(() -> {
            try {
                List<XlsxWriter.Sheet> reports = database.reports();
                try (OutputStream stream = getContentResolver().openOutputStream(destination, "w")) {
                    if (stream == null) throw new IOException("محل ذخیره باز نشد.");
                    XlsxWriter.write(stream, reports);
                }
                onUi(() -> {
                    setBusy(false);
                    new AlertDialog.Builder(this).setTitle("گزارش ذخیره شد")
                            .setMessage("فایل اکسل پنج‌برگه‌ای ذخیره شد. برگهٔ «بررسی فرسودگی» پلاک‌های علامت‌دار و شماره آمایش آن‌ها را همراه با وضعیت تطبیق، جداگانه فهرست می‌کند.")
                            .setPositiveButton("متوجه شدم", null).show();
                });
            } catch (Exception ex) {
                onUi(() -> { setBusy(false); alert("ذخیرهٔ گزارش ناموفق بود: " + ex.getMessage()); });
            }
        });
    }

    private static String value(List<String> row, int index) {
        return index >= 0 && index < row.size() ? row.get(index) : "";
    }

    private void discardPendingImport() {
        if (pendingImport != null) {
            pendingImport.delete();
            pendingImport = null;
        }
    }

    private static String trim(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length) + "…";
    }

    private static int guess(String[] names, String[] keywords, int fallback) {
        for (String keyword : keywords) {
            for (int i = 0; i < names.length; i++) {
                if (names[i].toLowerCase(Locale.ROOT).contains(keyword)) return i;
            }
        }
        return Math.min(fallback, names.length - 1);
    }

    private void setBusy(boolean isBusy) {
        busy = isBusy;
        loading.setVisibility(isBusy ? View.VISIBLE : View.GONE);
        lookupButton.setEnabled(!isBusy);
        matchedButton.setEnabled(!isBusy);
        mismatchButton.setEnabled(!isBusy);
        extraButton.setEnabled(!isBusy);
        removeButton.setEnabled(!isBusy);
        flagButton.setEnabled(!isBusy);
        mismatchChooseButton.setEnabled(!isBusy);
        saveFlagButton.setEnabled(!isBusy);
        clearFlagButton.setEnabled(!isBusy);
    }

    private void onUi(Runnable callback) { ui.post(() -> { if (!isFinishing() && !isDestroyed()) callback.run(); }); }

    private void alert(String message) {
        new AlertDialog.Builder(this).setTitle("کنترل اثاثیه")
                .setMessage(message == null ? "خطای نامشخص" : message)
                .setPositiveButton("بستن", null).show();
    }

    /** Move directly to the next plate after a successful record operation.
     *  Delay the IME request until the current dialog/button tap has finished.
     *  Do not call this for validation failures or cancelled dialogs.
     */
    private void focusNextPlate() {
        plateInput.setText("");
        plateInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        plateInput.setSingleLine(true);
        plateInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        screenScroll.post(() -> {
            if (isFinishing() || isDestroyed() || busy) return;
            plateInput.requestFocus();
            screenScroll.scrollTo(0, 0);
            plateInput.postDelayed(() -> {
                if (isFinishing() || isDestroyed() || busy || !plateInput.hasFocus()) return;
                InputMethodManager keyboard = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                if (keyboard != null) {
                    keyboard.showSoftInput(plateInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 220);
        });
    }

    private void hideKeyboard(View input) {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return layout;
    }

    private LinearLayout card(int color) {
        LinearLayout view = vertical();
        view.setPadding(dp(17), dp(17), dp(17), dp(17));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), BORDER);
        view.setBackground(background);
        view.setElevation(0f);
        return view;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        view.setTextDirection(View.TEXT_DIRECTION_RTL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private EditText entry(String hint) {
        EditText input = new EditText(this);
        input.setTextSize(17);
        input.setHint(hint);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        input.setPadding(dp(13), dp(9), dp(13), dp(9));
        GradientDrawable background = new GradientDrawable();
        background.setColor(PANEL_ALT);
        background.setCornerRadius(dp(9));
        background.setStroke(dp(1), BORDER);
        input.setBackground(background);
        return input;
    }

    private Button button(String caption, int color) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(caption);
        button.setTextColor(color == ORANGE ? BACKGROUND : TEXT);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(48));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(9));
        button.setBackground(background);
        button.setElevation(0f);
        return button;
    }

    private Spinner selector(String[] labels) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        spinner.setAdapter(adapter);
        spinner.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return spinner;
    }

    private LinearLayout.LayoutParams cardParams() { return fullParams(dp(12)); }

    private LinearLayout.LayoutParams fullParams(int bottomMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = bottomMargin;
        return p;
    }

    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }

    @Override protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
