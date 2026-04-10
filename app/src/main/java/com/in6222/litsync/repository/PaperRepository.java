package com.in6222.litsync.repository;

import com.in6222.litsync.database.AppDatabase;
import com.in6222.litsync.database.FavoritePaper;
import com.in6222.litsync.database.PaperDao;
import com.in6222.litsync.model.ArxivFeed;
import com.in6222.litsync.network.ArxivApiService;
import retrofit2.Call;

import java.util.List;

public class PaperRepository {

    private static final long REQUEST_INTERVAL_MS = 3000L;

    private final ArxivApiService apiService;
    private final PaperDao paperDao;
    private long lastRequestTime;

    public PaperRepository(ArxivApiService apiService, AppDatabase database) {
        this.apiService = apiService;
        this.paperDao = database.paperDao();
    }

    public Call<ArxivFeed> searchPapers(
            String searchQuery,
            int start,
            int maxResults,
            String sortBy,
            String sortOrder
    ) {
        enforceRequestInterval();
        return apiService.searchPapers(searchQuery, start, maxResults, sortBy, sortOrder);
    }

    public long saveFavorite(FavoritePaper paper) {
        return paperDao.insert(paper);
    }

    public int updateFavorite(FavoritePaper paper) {
        return paperDao.update(paper);
    }

    public int deleteFavorite(FavoritePaper paper) {
        return paperDao.delete(paper);
    }

    public List<FavoritePaper> getAllFavorites() {
        return paperDao.getAll();
    }

    public FavoritePaper getFavoriteById(int id) {
        return paperDao.getById(id);
    }

    public FavoritePaper getFavoriteByLink(String link) {
        return paperDao.getByLink(link);
    }

    public int deleteAllFavorites() {
        return paperDao.deleteAll();
    }

    private synchronized void enforceRequestInterval() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        if (elapsed < REQUEST_INTERVAL_MS) {
            try {
                Thread.sleep(REQUEST_INTERVAL_MS - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime = System.currentTimeMillis();
    }
}
