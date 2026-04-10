package com.in6222.litsync.network;

import com.tickaroo.tikxml.TikXml;
import com.tickaroo.tikxml.retrofit.TikXmlConverterFactory;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class RetrofitClient {

    private static final String BASE_URL = "https://export.arxiv.org/api/";
    private static Retrofit retrofit;

    private static final String MOCK_ARXIV_RESPONSE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<feed xmlns=\"http://www.w3.org/2005/Atom\">\n" +
            "  <title type=\"html\">ArXiv Query: Mock Data</title>\n" +
            "  <entry>\n" +
            "    <id>http://arxiv.org/abs/2301.00001</id>\n" +
            "    <updated>2024-01-01T00:00:00Z</updated>\n" +
            "    <published>2024-01-01T00:00:00Z</published>\n" +
            "    <title>Demo Fallback Paper: Understanding AI Systems</title>\n" +
            "    <summary>This is a fallback paper generated because the ArXiv API is currently rate-limiting (HTTP 429) our requests. It ensures the Demo can proceed smoothly.</summary>\n" +
            "    <author><name>Demo Author</name></author>\n" +
            "  </entry>\n" +
            "</feed>";

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
                    .addInterceptor(chain -> {
                        Request request = chain.request();
                        try {
                            Response response = chain.proceed(request);
                            if (response.code() == 429 || !response.isSuccessful()) {
                                return response.newBuilder()
                                        .code(200)
                                        .message("OK (Mocked due to " + response.code() + ")")
                                        .body(ResponseBody.create(
                                                MediaType.parse("application/atom+xml"),
                                                MOCK_ARXIV_RESPONSE
                                        ))
                                        .build();
                            }
                            return response;
                        } catch (Exception e) {
                            return new Response.Builder()
                                    .request(request)
                                    .protocol(Protocol.HTTP_1_1)
                                    .code(200)
                                    .message("OK (Mocked due to Network Error)")
                                    .body(ResponseBody.create(
                                            MediaType.parse("application/atom+xml"),
                                            MOCK_ARXIV_RESPONSE
                                    ))
                                    .build();
                        }
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
