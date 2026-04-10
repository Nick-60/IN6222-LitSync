package com.in6222.litsync.network;

import com.in6222.litsync.model.ArxivFeed;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ArxivApiService {

    @GET("query")
    Call<ArxivFeed> searchPapers(
            @Query("search_query") String searchQuery,
            @Query("start") int start,
            @Query("max_results") int maxResults,
            @Query("sortBy") String sortBy,
            @Query("sortOrder") String sortOrder
    );
}
