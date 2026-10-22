package com.paytech.arch.fixture.r15.violation.connectorcore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class DirectChannelCaller {

    private final HttpClient client = HttpClient.newHttpClient();

    public String call(URI uri) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    public CompletableFuture<HttpResponse<String>> callAsync(URI uri) {
        return client.sendAsync(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
    }
}
