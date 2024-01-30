package com.oviva.gesundheitsid.fedclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.oviva.gesundheitsid.fedclient.api.EntityStatement;
import com.oviva.gesundheitsid.fedclient.api.EntityStatement.FederationEntity;
import com.oviva.gesundheitsid.fedclient.api.EntityStatement.Metadata;
import com.oviva.gesundheitsid.fedclient.api.EntityStatementJWS;
import com.oviva.gesundheitsid.fedclient.api.FederationApiClient;
import com.oviva.gesundheitsid.fedclient.api.IdpList;
import com.oviva.gesundheitsid.fedclient.api.IdpList.IdpEntity;
import com.oviva.gesundheitsid.fedclient.api.IdpListJWS;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class FederationMasterClientImplTest {

  private static final URI FEDERATION_MASTER = URI.create("https://fedmaster.example.com");

  @Mock FederationApiClient federationApiClient;

  @Mock Clock clock;

  @Test
  void getList() {
    var client = new FederationMasterClientImpl(FEDERATION_MASTER, federationApiClient, clock);

    var idpListEndpoint = FEDERATION_MASTER.resolve("/idplist");
    var es =
        new EntityStatement(
            null,
            null,
            0,
            0,
            0,
            null,
            null,
            new Metadata(
                null,
                null,
                new FederationEntity(null, null, null, null, null, idpListEndpoint.toString())));

    var jws = new EntityStatementJWS(null, es);
    when(federationApiClient.fetchEntityConfiguration(FEDERATION_MASTER)).thenReturn(jws);

    var idp1Name = "AOK Testfalen";
    var idp2Name = "AOK Nordheim";

    var idpListJws =
        new IdpListJWS(
            null,
            new IdpList(
                null,
                0,
                0,
                0,
                List.of(
                    new IdpEntity(null, idp1Name, null, null, true),
                    new IdpEntity(null, idp2Name, null, null, true))));
    when(federationApiClient.fetchIdpList(idpListEndpoint)).thenReturn(idpListJws);

    // when
    var got = client.listAvailableIdps();

    // then

    assertEquals(idp1Name, got.get(0).name());
    assertEquals(idp2Name, got.get(1).name());
  }
}
