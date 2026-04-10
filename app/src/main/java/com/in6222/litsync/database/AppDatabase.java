package com.in6222.litsync.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/**
 * Room 数据库入口。
 */
@Database(entities = {FavoritePaper.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract PaperDao paperDao();
}
