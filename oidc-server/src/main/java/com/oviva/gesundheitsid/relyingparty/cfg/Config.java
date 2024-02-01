package com.oviva.gesundheitsid.relyingparty.cfg;

import java.net.URI;
import java.util.List;

public record Config(
    int port, URI baseUri, List<String> supportedResponseTypes, List<URI> validRedirectUris) {}
