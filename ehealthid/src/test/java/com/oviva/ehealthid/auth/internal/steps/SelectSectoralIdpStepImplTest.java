package com.oviva.ehealthid.auth.internal.steps;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oviva.ehealthid.fedclient.FederationMasterClient;
import com.oviva.ehealthid.fedclient.IdpEntry;
import com.oviva.ehealthid.fedclient.api.EntityStatement;
import com.oviva.ehealthid.fedclient.api.EntityStatement.Metadata;
import com.oviva.ehealthid.fedclient.api.EntityStatement.OpenidProvider;
import com.oviva.ehealthid.fedclient.api.EntityStatementJWS;
import com.oviva.ehealthid.fedclient.api.OpenIdClient;
import com.oviva.ehealthid.fedclient.api.OpenIdClient.ParResponse;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectSectoralIdpStepImplTest {

  @Test
  void fetchIdpOptions() {

    var fedmasterClient = mock(FederationMasterClient.class);

    var sut =
        new SelectSectoralIdpStepImpl(
            null, fedmasterClient, null, null, null, null, null, null, null);

    var entries = List.of(new IdpEntry("https://tk.example.com", "Techniker KK", null));
    when(fedmasterClient.listAvailableIdps()).thenReturn(entries);

    // when
    var idps = sut.fetchIdpOptions();

    // then
    assertEquals(entries, idps);
  }

  @Test
  void redirectToSectoralIdp() {

    var self = URI.create("https://fachdienst.example.com");
    var callbackUri = self.resolve("/callback");

    var fedmasterClient = mock(FederationMasterClient.class);
    var openIdClient = mock(OpenIdClient.class);

    var sut =
        new SelectSectoralIdpStepImpl(
            self,
            fedmasterClient,
            openIdClient,
            null,
            callbackUri,
            null,
            "test",
            "test-state",
            List.of());

    var sectoralIdp = URI.create("https://tk.example.com");

    var entityConfig = sectoralIdpEntityConfiguration(sectoralIdp);
    when(fedmasterClient.establishIdpTrust(sectoralIdp)).thenReturn(entityConfig);

    var parResponse = new ParResponse(sectoralIdp.resolve("/auth").toString(), 0);
    when(openIdClient.requestPushedUri(any(), any())).thenReturn(parResponse);

    // when
    var step = sut.redirectToSectoralIdp(sectoralIdp.toString());

    // then
    assertEquals(
        "https://tk.example.com/auth?request_uri=https%3A%2F%2Ftk.example.com%2Fauth&client_id=https%3A%2F%2Ffachdienst.example.com",
        step.idpRedirectUri().toString());
  }

  private EntityStatementJWS sectoralIdpEntityConfiguration(URI sub) {
    var body =
        EntityStatement.create()
            .sub(sub.toString())
            .iss(sub.toString())
            .metadata(
                Metadata.create()
                    .openidProvider(
                        OpenidProvider.create()
                            .pushedAuthorizationRequestEndpoint(sub.resolve("/par").toString())
                            .authorizationEndpoint(sub.resolve("/auth").toString())
                            .build())
                    .build())
            .build();

    return new EntityStatementJWS(null, body);
  }
}
