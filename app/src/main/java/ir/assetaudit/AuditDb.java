package ir.assetaudit;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Local-only durable inventory and audit observations. */
public final class AuditDb extends SQLiteOpenHelper {
    public static final int MATCH = 1;
    public static final int MISMATCH = 2;
    public static final int EXTRA = 3;

    public static final class Item {
        public final String plate;
        public final String name;

        public Item(String plate, String name) { this.plate = plate; this.name = name; }
    }

    public static final class Observation {
        public final String plate;
        public final String observedName;
        public final int status;

        Observation(String plate, String observedName, int status) {
            this.plate = plate;
            this.observedName = observedName;
            this.status = status;
        }
    }

    public static final class Flag {
        public final String plate;
        public final String note;
        public final String surveyNumber;
        public final long updatedAt;

        Flag(String plate, String note, String surveyNumber, long updatedAt) {
            this.plate = plate;
            this.note = note;
            this.surveyNumber = surveyNumber;
            this.updatedAt = updatedAt;
        }
    }

    public static final class Counts {
        public final int inventory;
        public final int matched;
        public final int mismatched;
        public final int extra;
        public final int flagged;

        Counts(int inventory, int matched, int mismatched, int extra, int flagged) {
            this.inventory = inventory;
            this.matched = matched;
            this.mismatched = mismatched;
            this.extra = extra;
            this.flagged = flagged;
        }

        public int unseen() { return inventory - matched - mismatched; }
    }

    public AuditDb(Context context) { super(context, "asset_audit.db", null, 3); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE inventory (plate TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL)");
        db.execSQL("CREATE TABLE observations (plate TEXT PRIMARY KEY NOT NULL, "
                + "observed_name TEXT NOT NULL, status INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX observations_recent ON observations(updated_at DESC)");
        createFlagsTable(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Additive migration: preserve existing inventory and observations in v1.5.
        if (oldVersion < 2 && newVersion >= 2) createFlagsTable(db);
        // v2 -> v3: keep previously flagged assets and all existing audit records.
        if (oldVersion >= 2 && oldVersion < 3 && newVersion >= 3) {
            db.execSQL("ALTER TABLE asset_flags ADD COLUMN survey_number TEXT NOT NULL DEFAULT ''");
        }
    }

    private static void createFlagsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS asset_flags (plate TEXT PRIMARY KEY NOT NULL, "
                + "note TEXT NOT NULL DEFAULT '', survey_number TEXT NOT NULL DEFAULT '', "
                + "updated_at INTEGER NOT NULL)");
    }

    public void replaceInventory(List<Item> items) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("asset_flags", null, null);
            db.delete("observations", null, null);
            db.delete("inventory", null, null);
            for (Item item : items) {
                ContentValues values = new ContentValues();
                values.put("plate", item.plate);
                values.put("name", item.name);
                db.insertOrThrow("inventory", null, values);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public Item lookup(String plate) {
        try (Cursor c = getReadableDatabase().query("inventory", new String[]{"plate", "name"},
                "plate=?", new String[]{plate}, null, null, null)) {
            return c.moveToFirst() ? new Item(c.getString(0), c.getString(1)) : null;
        }
    }

    public Observation observation(String plate) {
        try (Cursor c = getReadableDatabase().query("observations", new String[]{"plate", "observed_name", "status"},
                "plate=?", new String[]{plate}, null, null, null)) {
            return c.moveToFirst() ? new Observation(c.getString(0), c.getString(1), c.getInt(2)) : null;
        }
    }

    public void record(String plate, String observedName, int status) {
        recordWithReview(plate, observedName, status, false, "");
    }

    /** One all-or-nothing write: the inspection result and the selected
     *  review flag (including the survey number) must never diverge.
     *  A false flag selection does not remove an older persisted flag.
     */
    public void recordWithReview(String plate, String observedName, int status,
                                 boolean markForReview, String surveyNumber) {
        boolean known = lookup(plate) != null;
        if ((known && status == EXTRA) || (!known && status != EXTRA)) {
            throw new IllegalArgumentException("وضعیت پلاک با فهرست تطابق ندارد.");
        }
        if (markForReview && !known) {
            throw new IllegalArgumentException("فقط پلاک موجود در فهرست قابل علامت‌گذاری است.");
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long timestamp = System.currentTimeMillis();
            ContentValues observation = new ContentValues();
            observation.put("plate", plate);
            observation.put("observed_name", observedName);
            observation.put("status", status);
            observation.put("updated_at", timestamp);
            if (db.insertWithOnConflict("observations", null, observation,
                    SQLiteDatabase.CONFLICT_REPLACE) == -1) {
                throw new IllegalStateException("ثبت نتیجه انجام نشد.");
            }
            if (markForReview) {
                String previousNote = "";
                try (Cursor c = db.query("asset_flags", new String[]{"note"},
                        "plate=?", new String[]{plate}, null, null, null)) {
                    if (c.moveToFirst()) previousNote = c.getString(0);
                }
                ContentValues flag = new ContentValues();
                flag.put("plate", plate);
                flag.put("note", previousNote);
                // Store as TEXT to preserve leading zeros, regardless of length.
                flag.put("survey_number", surveyNumber == null ? "" : surveyNumber.trim());
                flag.put("updated_at", timestamp);
                if (db.insertWithOnConflict("asset_flags", null, flag,
                        SQLiteDatabase.CONFLICT_REPLACE) == -1) {
                    throw new IllegalStateException("ثبت علامت انجام نشد.");
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public void removeObservation(String plate) {
        getWritableDatabase().delete("observations", "plate=?", new String[]{plate});
    }

    /** Marked items remain in their audit category; a flag does not imply disposal approval. */
    public Flag flag(String plate) {
        try (Cursor c = getReadableDatabase().query("asset_flags", new String[]{"plate", "note", "survey_number", "updated_at"},
                "plate=?", new String[]{plate}, null, null, null)) {
            return c.moveToFirst() ? new Flag(c.getString(0), c.getString(1), c.getString(2), c.getLong(3)) : null;
        }
    }

    public void markForReview(String plate, String note, String surveyNumber) {
        if (lookup(plate) == null) throw new IllegalArgumentException("پلاک در فهرست اموال ثبت نشده است.");
        ContentValues values = new ContentValues();
        values.put("plate", plate);
        values.put("note", note == null ? "" : note.trim());
        // Keep exactly the entered digits (including zeros at the beginning).
        // There is intentionally no minimum or maximum length rule.
        values.put("survey_number", surveyNumber == null ? "" : surveyNumber.trim());
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("asset_flags", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void unmarkForReview(String plate) {
        getWritableDatabase().delete("asset_flags", "plate=?", new String[]{plate});
    }

    public Counts counts() {
        int inventory = 0, match = 0, mismatch = 0, extra = 0, flagged = 0;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT "
                + "(SELECT COUNT(*) FROM inventory),"
                + "(SELECT COUNT(*) FROM observations WHERE status=1),"
                + "(SELECT COUNT(*) FROM observations WHERE status=2),"
                + "(SELECT COUNT(*) FROM observations WHERE status=3),"
                + "(SELECT COUNT(*) FROM asset_flags)", null)) {
            if (c.moveToFirst()) {
                inventory = c.getInt(0);
                match = c.getInt(1);
                mismatch = c.getInt(2);
                extra = c.getInt(3);
                flagged = c.getInt(4);
            }
        }
        return new Counts(inventory, match, mismatch, extra, flagged);
    }

    public List<Observation> recent(int max) {
        List<Observation> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("observations",
                new String[]{"plate", "observed_name", "status"}, null, null,
                null, null, "updated_at DESC, plate", Integer.toString(max))) {
            while (c.moveToNext()) result.add(new Observation(c.getString(0), c.getString(1), c.getInt(2)));
        }
        return result;
    }

    public List<XlsxWriter.Sheet> reports() {
        XlsxWriter.Sheet mismatch = new XlsxWriter.Sheet("مغایرت نام",
                "پلاک", "نام ثبت‌شده", "نام مشاهده‌شده", "زمان ثبت");
        XlsxWriter.Sheet missing = new XlsxWriter.Sheet("مشاهده‌نشده",
                "پلاک", "نام ثبت‌شده");
        XlsxWriter.Sheet extra = new XlsxWriter.Sheet("خارج از فهرست",
                "پلاک", "نام مشاهده‌شده", "زمان ثبت");
        XlsxWriter.Sheet matched = new XlsxWriter.Sheet("مطابق",
                "پلاک", "نام ثبت‌شده", "زمان تأیید");
        XlsxWriter.Sheet flagged = new XlsxWriter.Sheet("بررسی فرسودگی",
                "پلاک", "شماره آمایش", "نام ثبت‌شده", "وضعیت بررسی",
                "نام مشاهده‌شده", "تاریخ علامت‌گذاری");
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT i.plate,i.name,o.observed_name,o.updated_at "
                + "FROM observations o JOIN inventory i ON i.plate=o.plate "
                + "WHERE o.status=2 ORDER BY i.plate", null)) {
            while (c.moveToNext()) mismatch.add(c.getString(0), c.getString(1), c.getString(2), time(c.getLong(3)));
        }
        try (Cursor c = db.rawQuery("SELECT i.plate,i.name FROM inventory i "
                + "LEFT JOIN observations o ON o.plate=i.plate "
                + "WHERE o.plate IS NULL ORDER BY i.plate", null)) {
            while (c.moveToNext()) missing.add(c.getString(0), c.getString(1));
        }
        try (Cursor c = db.rawQuery("SELECT plate,observed_name,updated_at "
                + "FROM observations WHERE status=3 ORDER BY plate", null)) {
            while (c.moveToNext()) extra.add(c.getString(0), c.getString(1), time(c.getLong(2)));
        }
        try (Cursor c = db.rawQuery("SELECT i.plate,i.name,o.updated_at "
                + "FROM observations o JOIN inventory i ON i.plate=o.plate "
                + "WHERE o.status=1 ORDER BY i.plate", null)) {
            while (c.moveToNext()) matched.add(c.getString(0), c.getString(1), time(c.getLong(2)));
        }
        try (Cursor c = db.rawQuery("SELECT i.plate,i.name,COALESCE(o.status,0),"
                + "COALESCE(o.observed_name,''),f.survey_number,f.updated_at "
                + "FROM asset_flags f JOIN inventory i ON i.plate=f.plate "
                + "LEFT JOIN observations o ON o.plate=i.plate ORDER BY i.plate", null)) {
            while (c.moveToNext()) {
                int state = c.getInt(2);
                String stateLabel = state == MATCH ? "مطابق" :
                        state == MISMATCH ? "مغایرت نام" : "مشاهده‌نشده";
                flagged.add(c.getString(0), c.getString(4), c.getString(1), stateLabel,
                        c.getString(3), time(c.getLong(5)));
            }
        }
        return Arrays.asList(mismatch, missing, extra, matched, flagged);
    }

    private static String time(long millis) {
        return new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.ROOT).format(new Date(millis));
    }
}
