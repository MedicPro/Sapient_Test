package com.medicpro.myassistant;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    public DatabaseHelper(Context context) { super(context, "my_assistant.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, direction TEXT NOT NULL, amount REAL NOT NULL, person TEXT, mode TEXT, business TEXT, note TEXT, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, due_at INTEGER NOT NULL, done INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE businesses (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL, is_default INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("INSERT INTO businesses(name,is_default) VALUES('General',1)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public long addTransaction(String direction, double amount, String person, String mode, String business, String note) {
        ContentValues v = new ContentValues();
        v.put("direction", direction); v.put("amount", amount); v.put("person", person); v.put("mode", mode);
        v.put("business", business); v.put("note", note); v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("transactions", null, v);
    }

    public long addTask(String title, long dueAt) {
        ContentValues v = new ContentValues(); v.put("title", title); v.put("due_at", dueAt); v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("tasks", null, v);
    }

    public void markTaskDone(long id) { ContentValues v = new ContentValues(); v.put("done", 1); getWritableDatabase().update("tasks", v, "id=?", new String[]{String.valueOf(id)}); }

    public String[] getBusinesses() {
        List<String> names = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT name FROM businesses ORDER BY is_default DESC, name", null)) {
            while (c.moveToNext()) names.add(c.getString(0));
        }
        return names.toArray(new String[0]);
    }

    public String getDefaultBusiness() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT name FROM businesses ORDER BY is_default DESC, id ASC LIMIT 1", null)) {
            return c.moveToFirst() ? c.getString(0) : "General";
        }
    }

    public boolean addBusiness(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        ContentValues v = new ContentValues(); v.put("name", name.trim());
        return getWritableDatabase().insertWithOnConflict("businesses", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    public void setDefaultBusiness(String name) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues zero = new ContentValues(); zero.put("is_default", 0); db.update("businesses", zero, null, null);
            ContentValues one = new ContentValues(); one.put("is_default", 1); db.update("businesses", one, "name=?", new String[]{name});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private long startOfToday() { return LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    private long startOfTomorrow() { return LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(); }

    public double sumToday(String direction) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE direction=? AND created_at>=? AND created_at<?", new String[]{direction, String.valueOf(startOfToday()), String.valueOf(startOfTomorrow())})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public double sumAll(String direction) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE direction=?", new String[]{direction})) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    public String personSummary(String person) {
        String like = "%" + person + "%";
        double in=0,out=0,dueIn=0,dueOut=0;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT direction, COALESCE(SUM(amount),0) FROM transactions WHERE person LIKE ? GROUP BY direction", new String[]{like})) {
            while (c.moveToNext()) {
                String d=c.getString(0); double a=c.getDouble(1);
                if ("IN".equals(d)) in=a; else if ("OUT".equals(d)) out=a; else if ("DUE_IN".equals(d)) dueIn=a; else if ("DUE_OUT".equals(d)) dueOut=a;
            }
        }
        return person + ": मिला ₹" + money(in) + ", दिया ₹" + money(out) + ", लेना ₹" + money(dueIn) + ", देना ₹" + money(dueOut);
    }

    public String todayTasksText() {
        StringBuilder b = new StringBuilder();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT title FROM tasks WHERE done=0 AND due_at>=? AND due_at<? ORDER BY due_at", new String[]{String.valueOf(startOfToday()), String.valueOf(startOfTomorrow())})) {
            int n=1; while(c.moveToNext()) { if (b.length()>0) b.append("; "); b.append(n++).append(". ").append(c.getString(0)); }
        }
        return b.length()==0 ? "आज कोई काम दर्ज नहीं है।" : "आज के काम: " + b;
    }

    public List<String> recentLines() {
        List<String> lines = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT direction,amount,person,mode,business FROM transactions ORDER BY id DESC LIMIT 12", null)) {
            while (c.moveToNext()) {
                String d=c.getString(0), label="";
                if ("IN".equals(d)) label="मिला"; else if ("OUT".equals(d)) label="दिया"; else if ("DUE_IN".equals(d)) label="लेना है"; else label="देना है";
                String person=c.getString(2)==null?"":c.getString(2);
                lines.add("₹"+money(c.getDouble(1))+" • "+label+(person.isEmpty()?"":" • "+person)+" • "+c.getString(3)+" • "+c.getString(4));
            }
        }
        return lines;
    }

    public List<TaskRow> upcomingTasks() {
        List<TaskRow> rows=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,due_at FROM tasks WHERE done=0 ORDER BY due_at LIMIT 10",null)) {
            while(c.moveToNext()) rows.add(new TaskRow(c.getLong(0),c.getString(1),c.getLong(2)));
        }
        return rows;
    }

    public static String money(double n) { return n == Math.rint(n) ? String.format(java.util.Locale.US,"%.0f",n) : String.format(java.util.Locale.US,"%.2f",n); }
    public static class TaskRow { public final long id; public final String title; public final long dueAt; TaskRow(long i,String t,long d){id=i;title=t;dueAt=d;} }
}
