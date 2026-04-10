package com.in6222.litsync.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

/**
 * 收藏论文数据访问接口。
 */
@Dao
public interface PaperDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(FavoritePaper paper);

    @Update
    int update(FavoritePaper paper);

    @Delete
    int delete(FavoritePaper paper);

    @Query("SELECT * FROM favorite_papers ORDER BY publishedDate DESC")
    List<FavoritePaper> getAll();

    @Query("SELECT * FROM favorite_papers WHERE id = :id LIMIT 1")
    FavoritePaper getById(int id);

    @Query("SELECT * FROM favorite_papers WHERE link = :link LIMIT 1")
    FavoritePaper getByLink(String link);

    @Query("DELETE FROM favorite_papers")
    int deleteAll();
}
