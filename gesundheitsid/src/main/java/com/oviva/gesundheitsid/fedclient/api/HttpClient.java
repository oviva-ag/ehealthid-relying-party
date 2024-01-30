package com.oviva.gesundheitsid.fedclient.api;

import java.net.URI;
import java.util.List;

public interface HttpClient {

  Response call(Request req);

  record Request(URI uri, String method, List<Header> headers, byte[] body) {}

  record Response(int status, List<Header> headers, byte[] body) {}

  record Header(String name, String value) {}
}
