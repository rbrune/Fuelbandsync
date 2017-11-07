package de.rbrune.fuelbandsync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;


public class FuelbandsyncDatabase extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "fuelbandsync.db";
    public static final String FBSYNC_TABLE_NAME = "fbdata";
    public static final String FBSYNC_COLUMN_TIME_END   = "endtime";
    public static final String FBSYNC_COLUMN_TIME_START = "starttime";
    public static final String FBSYNC_COLUMN_FUEL       = "fuel";
    public static final String FBSYNC_COLUMN_CALORIES   = "calories";
    public static final String FBSYNC_COLUMN_STEPS      = "steps";
    public static final String FBSYNC_COLUMN_UNKNOWN1   = "unknown1";
    public static final String FBSYNC_COLUMN_UNKNOWN2   = "unknown2";
    public static final String FBSYNC_COLUMN_SYNCED     = "synced";

    private HashMap hp;

    public FuelbandsyncDatabase(Context context)
    {
        super(context, DATABASE_NAME, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // TODO Auto-generated method stub
        db.execSQL(
                "create table fbdata " +
                        "(endtime integer primary key, starttime integer, fuel integer, calories integer, steps integer, unknown1 integer, unknown2 integer, synced integer)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO Auto-generated method stub
        db.execSQL("DROP TABLE IF EXISTS fbdata");
        onCreate(db);
    }

    public boolean insertDatapoint(long endtime, long starttime, int fuel, int calories, int steps, int unknown1, int unknown2)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("endtime", endtime);
        contentValues.put("starttime", starttime);
        contentValues.put("fuel", fuel);
        contentValues.put("calories", calories);
        contentValues.put("steps", steps);
        contentValues.put("unknown1", unknown1);
        contentValues.put("unknown2", unknown2);
        contentValues.put("synced", 0);
        db.insertWithOnConflict("fbdata", null, contentValues, SQLiteDatabase.CONFLICT_IGNORE);
        db.close();
        return true;
    }

    public Cursor getDatapointCursor(long endtime){
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res =  db.rawQuery( "select * from fbdata where endtime="+endtime+"", null );
        db.close();
        return res;
    }


    public ContentValues getDatapoint(long endtime){

        ContentValues contentValues = new ContentValues();

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res =  db.rawQuery("select * from fbdata where endtime = " + endtime + " ", null);
        res.moveToFirst();
        contentValues.put(FBSYNC_COLUMN_TIME_END,   res.getLong(res.getColumnIndex(FBSYNC_COLUMN_TIME_END)));
        contentValues.put(FBSYNC_COLUMN_TIME_START, res.getLong(res.getColumnIndex(FBSYNC_COLUMN_TIME_START)));
        contentValues.put(FBSYNC_COLUMN_FUEL, res.getInt(res.getColumnIndex(FBSYNC_COLUMN_FUEL)));
        contentValues.put(FBSYNC_COLUMN_CALORIES, res.getInt(res.getColumnIndex(FBSYNC_COLUMN_CALORIES)));
        contentValues.put(FBSYNC_COLUMN_STEPS, res.getInt(res.getColumnIndex(FBSYNC_COLUMN_STEPS)));
        contentValues.put(FBSYNC_COLUMN_SYNCED, res.getInt(res.getColumnIndex(FBSYNC_COLUMN_SYNCED)));
        res.close();
        db.close();


        return contentValues;
    }




    public int numberOfRows(){
        SQLiteDatabase db = this.getReadableDatabase();
        int numRows = (int) DatabaseUtils.queryNumEntries(db, FBSYNC_TABLE_NAME);
        db.close();
        return numRows;
    }


    public boolean updateDatapointSync (long endtime, int synced)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("synced", synced);
        db.update(FBSYNC_TABLE_NAME, contentValues, "endtime = ? ", new String[]{Long.toString(endtime)});
        db.close();
        return true;
    }

    public boolean deleteDatapoint(long endtime)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(FBSYNC_TABLE_NAME,
                "endtime = ? ",
                new String[] { Long.toString(endtime) });
        db.close();
        return true;
    }

    public ArrayList<Long> getAllUnsyncedDatapoints()
    {
        ArrayList<Long> array_list = new ArrayList<Long>();

        //hp = new HashMap();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res =  db.rawQuery( "select * from fbdata where synced = 0", null );
        res.moveToFirst();

        while(res.isAfterLast() == false){
            array_list.add(res.getLong(res.getColumnIndex(FBSYNC_COLUMN_TIME_END)));
            res.moveToNext();
        }

        res.close();
        db.close();
        return array_list;
    }

}
