package com.oviva.ehealthid.relyingparty.cfg;

import java.net.URI;
import java.util.List;

public record RelyingPartyConfig(
    List<String> supportedResponseTypes, List<URI> validRedirectUris) {}
