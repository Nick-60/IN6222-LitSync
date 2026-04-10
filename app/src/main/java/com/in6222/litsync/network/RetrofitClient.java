package com.in6222.litsync.network;

import com.tickaroo.tikxml.TikXml;
import com.tickaroo.tikxml.retrofit.TikXmlConverterFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class RetrofitClient {

    private static final String BASE_URL = "https://export.arxiv.org/api/";
    private static Retrofit retrofit;

    public static Retrofit getInstance() {
        if (retrofit == null) {
            HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
            interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(40, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .callTimeout(45, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .addInterceptor(chain -> {
                        Request request = chain.request();
                        int maxRetries = 2;
                        long backoffMs = 500L;
                        Response response = null;
                        IOException lastException = null;
                        for (int attempt = 0; attempt <= maxRetries; attempt++) {
                            try {
                                response = chain.proceed(request);
                                if (response.code() != 503) {
                                    return response;
                                }
                                response.close();
                            } catch (IOException e) {
                                lastException = e;
                            }
                            if (attempt < maxRetries) {
                                try {
                                    Thread.sleep(backoffMs);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                                backoffMs *= 2;
                            }
                        }
                        if (lastException != null) {
                            throw lastException;
                        }
                        return response;
                    })
                    .addInterceptor(interceptor)
                    .build();

            TikXml tikXml = new TikXml.Builder()
                    .exceptionOnUnreadXml(false)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(TikXmlConverterFactory.create(tikXml))
                    .build();
        }
        return retrofit;
    }

    public static ArxivApiService getService() {
        return getInstance().create(ArxivApiService.class);
    }
}
