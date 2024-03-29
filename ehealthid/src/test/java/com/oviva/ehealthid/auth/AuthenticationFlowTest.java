package com.oviva.ehealthid.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.oviva.ehealthid.auth.AuthenticationFlow.Session;
import com.oviva.ehealthid.crypto.KeySupplier;
import com.oviva.ehealthid.fedclient.FederationMasterClient;
import com.oviva.ehealthid.fedclient.api.OpenIdClient;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthenticationFlowTest {

  @Test
  void start() {
    var self = URI.create("https://fachdienst.example.com");
    var fedmasterClient = mock(FederationMasterClient.class);
    var openIdClient = mock(OpenIdClient.class);
    var keySupplier = mock(KeySupplier.class);

    var flow = new AuthenticationFlow(self, fedmasterClient, openIdClient, keySupplier);

    var step = flow.start(new Session(null, null, null, null, List.of()));

    assertNotNull(step);
  }
}
