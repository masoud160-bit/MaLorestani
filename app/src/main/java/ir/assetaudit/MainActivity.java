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
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
    // Classic graphite and muted cobalt palette — approved design option 2.
    private static final int BACKGROUND = Color.rgb(13, 17, 23); // #0D1117
    private static final int NAVY = Color.rgb(21, 26, 33); // #151A21
    private static final int PANEL = Color.rgb(27, 34, 43); // #1B222B
    private static final int PANEL_ALT = Color.rgb(34, 44, 56); // #222C38
    private static final int BORDER = Color.rgb(53, 67, 84); // #354354
    private static final int TEXT = Color.rgb(237, 242, 247); // #EDF2F7
    private static final int BLUE = Color.rgb(59, 105, 165); // #3B69A5
    private static final int GREEN = Color.rgb(52, 134, 92); // #34865C
    private static final int ORANGE = Color.rgb(214, 160, 76); // restrained gold #D6A04C
    private static final int RED = Color.rgb(171, 100, 100); // #AB6464
    private static final int MUTED = Color.rgb(164, 175, 190); // #A4AFBE
    private static final int MAX_IMPORT_BYTES = 25 * 1024 * 1024;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private AuditDb database;
    private ScrollView screenScroll;
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
    private Button importButton;
    private Button exportButton;
    private Button lookupButton;
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
        root.setPadding(dp(12), dp(8), dp(12), dp(24));
        scroll.addView(root);

        LinearLayout hero = card(NAVY);
        hero.setPadding(dp(12), dp(5), dp(12), dp(5));
        LinearLayout heroHeader = new LinearLayout(this);
        heroHeader.setOrientation(LinearLayout.HORIZONTAL);
        heroHeader.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        heroHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView menuButton = text("☰", 25, Color.WHITE, true);
        menuButton.setGravity(Gravity.CENTER);
        menuButton.setContentDescription("منوی وضعیت بررسی و سابقهٔ پلاک‌ها");
        GradientDrawable menuBackground = new GradientDrawable();
        menuBackground.setColor(NAVY);
        menuBackground.setStroke(dp(1), BORDER);
        menuBackground.setCornerRadius(dp(9));
        menuButton.setBackground(menuBackground);
        heroHeader.addView(menuButton, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView title = text("کنترل و تطبیق اثاثیه", 19, Color.WHITE, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(dp(8));
        heroHeader.addView(title, titleParams);
        hero.addView(heroHeader);
        menuButton.setOnClickListener(v -> showAuditMenu(menuButton));
        root.addView(hero, cardParams());

        overview = text("", 14, MUTED, false);
        overview.setLineSpacing(dp(5), 1f);

        LinearLayout lookupCard = card(PANEL);
        lookupCard.setPadding(dp(13), dp(12), dp(13), dp(12));
        TextView lookupHeading = text("بررسی پلاک اثاثیه", 16, TEXT, true);
        lookupHeading.setPadding(0, 0, 0, dp(7));
        lookupCard.addView(lookupHeading);
        LinearLayout lookupRow = new LinearLayout(this);
        lookupRow.setOrientation(LinearLayout.HORIZONTAL);
        lookupRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        lookupRow.setGravity(Gravity.CENTER_VERTICAL);
        plateInput = entry("شماره پلاک اثاثیه");
        plateInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        plateInput.setTextSize(24);
        plateInput.setMinHeight(dp(68));
        plateInput.setGravity(Gravity.CENTER);
        plateInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        plateInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        plateInput.setSingleLine(true);
        LinearLayout.LayoutParams plateParams = new LinearLayout.LayoutParams(0, dp(68), 1f);
        plateParams.setMarginEnd(dp(8));
        lookupRow.addView(plateInput, plateParams);
        lookupButton = button("جست‌وجو", BLUE);
        lookupRow.addView(lookupButton, new LinearLayout.LayoutParams(dp(100), dp(68)));
        lookupCard.addView(lookupRow);
        lookupButton.setOnClickListener(v -> searchPlate());
        plateInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { searchPlate(); return true; }
            return false;
        });
        root.addView(lookupCard, cardParams());

        resultPanel = card(PANEL);
        resultPanel.setPadding(dp(13), dp(12), dp(13), dp(12));
        foundTitle = text("", 18, TEXT, true);
        foundDescription = text("", 15, TEXT, false);
        foundDescription.setPadding(0, dp(5), 0, dp(8));
        previousObservation = text("", 13, ORANGE, false);
        previousObservation.setVisibility(View.GONE);
        previousObservation.setPadding(0, dp(7), 0, dp(9));
        resultPanel.addView(foundTitle);
        resultPanel.addView(foundDescription);
        matchedButton = button("تأیید: نام اثاثیه مطابق است", GREEN);
        resultPanel.addView(matchedButton, fullParams(dp(5)));
        flagButton = button("★ علامت‌گذاری برای بررسی فرسودگی / خروج", ORANGE);
        resultPanel.addView(flagButton, fullParams(dp(4)));
        flagHint = text("", 13, MUTED, false);
        flagHint.setVisibility(View.GONE);
        resultPanel.addView(flagHint);
        resultPanel.addView(previousObservation);
        actualInput = entry("نام واقعی اثاثیه مشاهده‌شده را بنویسید");
        actualInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        actualInput.setSingleLine(false);
        actualInput.setMinLines(1);
        actualInput.setMaxLines(3);
        resultPanel.addView(actualInput, fullParams(dp(10)));
        mismatchButton = button("ثبت مغایرت نام اثاثیه", ORANGE);
        extraButton = button("ثبت پلاک خارج از فهرست", RED);
        removeButton = button("حذف ثبت قبلی این پلاک", MUTED);
        resultPanel.addView(mismatchButton, fullParams(dp(8)));
        resultPanel.addView(extraButton, fullParams(dp(8)));
        resultPanel.addView(removeButton);
        matchedButton.setOnClickListener(v -> submitObservation(AuditDb.MATCH));
        mismatchButton.setOnClickListener(v -> submitObservation(AuditDb.MISMATCH));
        extraButton.setOnClickListener(v -> submitObservation(AuditDb.EXTRA));
        removeButton.setOnClickListener(v -> confirmRemove());
        flagButton.setOnClickListener(v -> showFlagDialog());
        resultPanel.setVisibility(View.GONE);
        root.addView(resultPanel, cardParams());

        recentList = text("", 14, MUTED, false);
        recentList.setLineSpacing(dp(5), 1.0f);

        LinearLayout fileCard = card(PANEL);
        fileCard.addView(text("مدیریت فایل و گزارش‌ها", 16, TEXT, true));
        importButton = button("ورود فایل اکسل", BLUE);
        exportButton = button("دریافت گزارش پنج‌برگه‌ای اکسل", BLUE);
        fileCard.addView(importButton, fullParams(dp(8)));
        fileCard.addView(exportButton);
        root.addView(fileCard, cardParams());
        importButton.setOnClickListener(v -> openExcelPicker());
        exportButton.setOnClickListener(v -> confirmExport());

        loading = new ProgressBar(this);
        loading.setIndeterminate(true);
        loading.setVisibility(View.GONE);
        root.addView(loading, cardParams());

        TextView note = text("نکته: تا پایان بازدید، «مشاهده‌نشده» به معنی مفقود بودن قطعی نیست. "
                + "اطلاعات روی همین دستگاه ذخیره می‌شود و با ورود فهرست جدید، پس از تأیید شما، "
                + "نتایج قبلی پاک می‌شود؛ بنابراین ابتدا گزارش بگیرید.", 12, MUTED, false);
        root.addView(note, fullParams(dp(8)));
        setContentView(scroll);
    }

    private void showAuditMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("وضعیت بررسی");
        popup.getMenu().add("آخرین پلاک‌های بررسی‌شده");
        popup.setOnMenuItemClickListener(item -> {
            String heading = item.getTitle().toString();
            TextView message = text(heading.equals("وضعیت بررسی")
                    ? overview.getText().toString() : recentList.getText().toString(),
                    17, TEXT, false);
            message.setGravity(Gravity.RIGHT | Gravity.TOP);
            message.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            message.setTextDirection(View.TEXT_DIRECTION_RTL);
            message.setLineSpacing(dp(8), 1.12f);
            message.setPadding(dp(22), dp(14), dp(22), dp(18));
            ScrollView panel = new ScrollView(this);
            panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            panel.addView(message);
            new AlertDialog.Builder(this).setTitle(heading).setView(panel)
                    .setPositiveButton("بستن", null).show();
            return true;
        });
        popup.show();
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
        List<AuditDb.Observation> recent = database.recent(8);
        if (recent.isEmpty()) {
            recentList.setText("هنوز پلاکی ثبت نشده است.");
        } else {
            StringBuilder list = new StringBuilder();
            for (AuditDb.Observation o : recent) {
                if (list.length() > 0) list.append('\n');
                list.append("• ").append(o.plate).append(" — ");
                list.append(o.status == AuditDb.MATCH ? "مطابق" :
                        o.status == AuditDb.MISMATCH ? "مغایرت نام" : "خارج از فهرست");
            }
            recentList.setText(list.toString());
        }
        exportButton.setEnabled(!busy && c.inventory > 0);
    }

    private void searchPlate() {
        if (busy) return;
        if (database.counts().inventory == 0) {
            new AlertDialog.Builder(this).setTitle("فهرست اثاثیه وارد نشده است")
                    .setMessage("برای شروع بررسی، ابتدا فایل اکسل اثاثیه را انتخاب کنید.")
                    .setPositiveButton("انتخاب فایل اکسل", (dialog, which) -> openExcelPicker())
                    .setNegativeButton("بعداً", null).show();
            return;
        }
        String plate = PlateNormalizer.normalize(plateInput.getText().toString());
        if (plate.isEmpty()) { plateInput.setError("پلاک را وارد کنید."); return; }
        selectedPlate = plate;
        selectedItem = database.lookup(plate);
        AuditDb.Observation previous = database.observation(plate);
        if (selectedItem == null) {
            foundTitle.setText("پلاک «" + plate + "» در اکسل وجود ندارد");
            foundTitle.setTextColor(RED);
            foundDescription.setText("اگر پلاک را درست خوانده‌اید، نام اثاثیه را وارد و ثبت کنید.");
            flagButton.setVisibility(View.GONE);
            flagHint.setVisibility(View.GONE);
            matchedButton.setVisibility(View.GONE);
            mismatchButton.setVisibility(View.GONE);
            extraButton.setVisibility(View.VISIBLE);
        } else {
            foundTitle.setText("پلاک «" + plate + "» پیدا شد");
            foundTitle.setTextColor(GREEN);
            foundDescription.setText("نام ثبت‌شده: " + selectedItem.name);
            matchedButton.setVisibility(View.VISIBLE);
            mismatchButton.setVisibility(View.VISIBLE);
            updateFlagControls();
            extraButton.setVisibility(View.GONE);
        }
        actualInput.setText(previous == null || previous.status == AuditDb.MATCH ? "" : previous.observedName);
        previousObservation.setVisibility(previous == null ? View.GONE : View.VISIBLE);
        removeButton.setVisibility(previous == null ? View.GONE : View.VISIBLE);
        if (previous != null) previousObservation.setText("این پلاک قبلاً ثبت شده است. ثبت جدید، نتیجهٔ قبلی را اصلاح می‌کند.");
        resultPanel.setVisibility(View.VISIBLE);
        hideKeyboard(plateInput);
        screenScroll.post(() -> screenScroll.smoothScrollTo(0, 0));
    }

    private void updateFlagControls() {
        if (selectedPlate == null || selectedItem == null) {
            flagButton.setVisibility(View.GONE);
            flagHint.setVisibility(View.GONE);
            return;
        }
        AuditDb.Flag flag = database.flag(selectedPlate);
        flagButton.setVisibility(View.VISIBLE);
        if (flag == null) {
            flagButton.setText("★ علامت‌گذاری برای بررسی فرسودگی / خروج");
            flagHint.setVisibility(View.GONE);
        } else {
            flagButton.setText("✓ علامت‌دار — ویرایش شماره آمایش یا برداشتن علامت");
            flagHint.setText("شماره آمایش: " +
                    (flag.surveyNumber.isEmpty() ? "ثبت نشده" : flag.surveyNumber));
            flagHint.setVisibility(View.VISIBLE);
        }
    }

    private void showFlagDialog() {
        if (busy || selectedPlate == null || selectedItem == null) return;
        final String plate = selectedPlate;
        AuditDb.Flag previous = database.flag(plate);
        LinearLayout layout = vertical();
        layout.setPadding(dp(18), dp(10), dp(18), dp(8));
        TextView description = text("شماره آمایش برای این پلاک ثبت می‌شود. "
                + "تعداد رقم محدود نیست و صفرهای ابتدایی حفظ می‌شوند. "
                + "علامت‌گذاری به معنی تأیید خروج از اموال نیست.", 14, TEXT, false);
        layout.addView(description, fullParams(dp(12)));
        EditText survey = entry("شماره آمایش (فقط عدد)");
        survey.setInputType(InputType.TYPE_CLASS_NUMBER);
        survey.setSingleLine(true);
        survey.setTextDirection(View.TEXT_DIRECTION_LTR);
        survey.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        // No maxLength/minLength validation: eight digits was only a width estimate.
        if (previous != null) survey.setText(previous.surveyNumber);
        layout.addView(survey);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(previous == null ? "علامت‌گذاری پلاک «" + plate + "»"
                        : "ویرایش شماره آمایش پلاک «" + plate + "»")
                .setView(layout)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ذخیره", (dialog, which) -> {
                    try {
                        // Keep legacy notes if this flag was saved by older versions.
                        String oldNote = previous == null ? "" : previous.note;
                        database.markForReview(plate, oldNote, survey.getText().toString());
                        updateFlagControls();
                        refreshDashboard();
                        Toast.makeText(this, "علامت ثبت شد؛ وضعیت مشاهده تغییر نکرد.", Toast.LENGTH_SHORT).show();
                        // The user may type the next plate immediately; flagged items
                        // are not silently counted as matched.
                        focusNextPlate();
                    } catch (Exception error) {
                        alert("ثبت انجام نشد: " + error.getMessage());
                    }
                });
        if (previous != null) {
            builder.setNeutralButton("برداشتن علامت", (dialog, which) -> {
                new AlertDialog.Builder(this).setTitle("برداشتن علامت")
                        .setMessage("علامت بررسی فرسودگی / خروج از پلاک «" + plate
                                + "» برداشته شود؟ وضعیت تطبیق این پلاک تغییر نمی‌کند.")
                        .setNegativeButton("انصراف", null)
                        .setPositiveButton("برداشتن", (confirmation, whichButton) -> {
                            database.unmarkForReview(plate);
                            updateFlagControls();
                            refreshDashboard();
                            focusNextPlate();
                        }).show();
            });
        }
        builder.show();
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
            actualInput.setText("");
            selectedPlate = null;
            selectedItem = null;
            resultPanel.setVisibility(View.GONE);
            hideKeyboard(actualInput);
            refreshDashboard();
            focusNextPlate();
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
                    selectedPlate = null;
                    selectedItem = null;
                    resultPanel.setVisibility(View.GONE);
                    plateInput.setText("");
                    refreshDashboard();
                    focusNextPlate();
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
                                selectedPlate = null;
                                selectedItem = null;
                                plateInput.setText("");
                                resultPanel.setVisibility(View.GONE);
                                screenScroll.post(() -> screenScroll.smoothScrollTo(0, 0));
                                refreshDashboard();
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
        importButton.setEnabled(!isBusy);
        lookupButton.setEnabled(!isBusy);
        matchedButton.setEnabled(!isBusy);
        mismatchButton.setEnabled(!isBusy);
        extraButton.setEnabled(!isBusy);
        removeButton.setEnabled(!isBusy);
        flagButton.setEnabled(!isBusy);
        exportButton.setEnabled(!isBusy && database.counts().inventory > 0);
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
