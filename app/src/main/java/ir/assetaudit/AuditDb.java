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

    public static final class Counts {
        public final int inventory;
        public final int matched;
        public final int mismatched;
        public final int extra;

        Counts(int inventory, int matched, int mismatched, int extra) {
            this.inventory = inventory;
            this.matched = matched;
            this.mismatched = mismatched;
            this.extra = extra;
        }

        public int unseen() { return inventory - matched - mismatched; }
    }

    public AuditDb(Context context) { super(context, "asset_audit.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE inventory (plate TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL)");
        db.execSQL("CREATE TABLE observations (plate TEXT PRIMARY KEY NOT NULL, "
                + "observed_name TEXT NOT NULL, status INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX observations_recent ON observations(updated_at DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // No destructive upgrade. A migration must be added when the schema changes.
        throw new IllegalStateException("Database migration missing: " + oldVersion + " -> " + newVersion);
    }

    public void replaceInventory(List<Item> items) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
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
        boolean known = lookup(plate) != null;
        if ((known && status == EXTRA) || (!known && status != EXTRA)) {
            throw new IllegalArgumentException("وضعیت پلاک با فهرست تطابق ندارد.");
        }
        ContentValues values = new ContentValues();
        values.put("plate", plate);
        values.put("observed_name", observedName);
        values.put("status", status);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("observations", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void removeObservation(String plate) {
        getWritableDatabase().delete("observations", "plate=?", new String[]{plate});
    }

    public Counts counts() {
        int inventory = 0, match = 0, mismatch = 0, extra = 0;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT "
                + "(SELECT COUNT(*) FROM inventory),"
                + "(SELECT COUNT(*) FROM observations WHERE status=1),"
                + "(SELECT COUNT(*) FROM observations WHERE status=2),"
                + "(SELECT COUNT(*) FROM observations WHERE status=3)", null)) {
            if (c.moveToFirst()) {
                inventory = c.getInt(0);
                match = c.getInt(1);
                mismatch = c.getInt(2);
                extra = c.getInt(3);
            }
        }
        return new Counts(inventory, match, mismatch, extra);
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
        return Arrays.asList(mismatch, missing, extra);
    }

    private static String time(long millis) {
        return new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.ROOT).format(new Date(millis));
    }
}
