package com.paytech.arch.fixture.r15.violation.protocolother;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ProtocolShortcut {

    public int status(HttpClient client, URI uri) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
