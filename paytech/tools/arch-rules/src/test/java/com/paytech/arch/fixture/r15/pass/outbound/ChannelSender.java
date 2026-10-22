package com.paytech.arch.fixture.r15.pass.outbound;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ChannelSender {

    private final HttpClient client = HttpClient.newHttpClient();

    public String send(URI uri) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString()).body();
    }
}
