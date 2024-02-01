package com.oviva.gesundheitsid.fedclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.oviva.gesundheitsid.crypto.ECKeyPair;
import com.oviva.gesundheitsid.fedclient.api.EntityStatement;
import com.oviva.gesundheitsid.fedclient.api.EntityStatement.FederationEntity;
import com.oviva.gesundheitsid.fedclient.api.EntityStatement.Metadata;
import com.oviva.gesundheitsid.fedclient.api.EntityStatementJWS;
import com.oviva.gesundheitsid.fedclient.api.FederationApiClient;
import com.oviva.gesundheitsid.fedclient.api.IdpList;
import com.oviva.gesundheitsid.fedclient.api.IdpList.IdpEntity;
import com.oviva.gesundheitsid.fedclient.api.IdpListJWS;
import com.oviva.gesundheitsid.test.ECKeyPairGenerator;
import com.oviva.gesundheitsid.test.JwksUtils;
import com.oviva.gesundheitsid.test.JwsUtils;
import com.oviva.gesundheitsid.util.JsonCodec;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class FederationMasterClientImplTest {

  private static final URI FEDERATION_MASTER = URI.create("https://fedmaster.example.com");
  private final Instant NOW = Instant.parse("2024-01-01T00:12:33.000Z");
  private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
  @Mock FederationApiClient federationApiClient;

  @Test
  void getList() {
    var client = new FederationMasterClientImpl(FEDERATION_MASTER, federationApiClient, clock);

    var idpListEndpoint = FEDERATION_MASTER.resolve("/idplist");
    var es =
        EntityStatement.create()
            .metadata(
                Metadata.create()
                    .federationEntity(
                        FederationEntity.create()
                            .idpListEndpoint(idpListEndpoint.toString())
                            .build())
                    .build())
            .build();

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

  @Test
  void establishTrust_expiredFedmasterConfig() {

    var client = new FederationMasterClientImpl(FEDERATION_MASTER, federationApiClient, clock);

    var issuer = URI.create("https://idp-tk.example.com");

    var fedmasterKeypair = ECKeyPairGenerator.example();

    var fedmasterEntityConfigurationJws = expiredFedmasterConfiguration(fedmasterKeypair);

    when(federationApiClient.fetchEntityConfiguration(FEDERATION_MASTER))
        .thenReturn(fedmasterEntityConfigurationJws);

    // when
    var e = assertThrows(RuntimeException.class, () -> client.establishIdpTrust(issuer));

    // then
    assertEquals(
        "entity statement of 'https://fedmaster.example.com' expired or not yet valid",
        e.getMessage());
  }

  @Test
  void establishTrust_badFedmasterConfigSignature() {

    var client = new FederationMasterClientImpl(FEDERATION_MASTER, federationApiClient, clock);

    var issuer = URI.create("https://idp-tk.example.com");

    var fedmasterKeypair = ECKeyPairGenerator.example();
    var unrelatedKeypair = ECKeyPairGenerator.generate();

    var fedmasterEntityConfigurationJws =
        badSignatureFedmasterConfiguration(fedmasterKeypair, unrelatedKeypair);

    when(federationApiClient.fetchEntityConfiguration(FEDERATION_MASTER))
        .thenReturn(fedmasterEntityConfigurationJws);

    // when
    var e = assertThrows(RuntimeException.class, () -> client.establishIdpTrust(issuer));

    // then
    assertEquals(
        "entity statement of 'https://fedmaster.example.com' has a bad signature", e.getMessage());
  }

  @Test
  void establishTrust_configurationWithUnknownSignature() {

    var client = new FederationMasterClientImpl(FEDERATION_MASTER, federationApiClient, clock);

    var issuer = URI.create("https://idp-tk.example.com");
    var federationFetchUrl = FEDERATION_MASTER.resolve("/fetch");

    var fedmasterKeypair = ECKeyPairGenerator.example();

    var fedmasterEntityConfigurationJws =
        federationFetchFedmasterConfiguration(federationFetchUrl, fedmasterKeypair);

    var trustedSectoralIdpKeypair = ECKeyPairGenerator.generate();
    var trustedFederationStatement =
        trustedFederationStatement(issuer, trustedSectoralIdpKeypair, fedmasterKeypair);

    var untrustedSectoralIdpKeypair = ECKeyPairGenerator.generate();
    var sectoralEntityConfiguration =
        sectoralIdpEntityConfiguration(issuer, untrustedSectoralIdpKeypair);

    when(federationApiClient.fetchEntityConfiguration(FEDERATION_MASTER))
        .thenReturn(fedmasterEntityConfigurationJws);

    when(federationApiClient.fetchFederationStatement(
            federationFetchUrl, FEDERATION_MASTER.toString(), issuer.toString()))
        .thenReturn(trustedFederationStatement);

    when(federationApiClient.fetchEntityConfiguration(issuer))
        .thenReturn(sectoralEntityConfiguration);

    // when
    var e = assertThrows(RuntimeException.class, () -> client.establishIdpTrust(issuer));

    // then
    assertEquals("federation statement untrusted: sub=https://idp-tk.example.com", e.getMessage());
  }

  @Test
  void establishTrust_configurationWithBadJwks() {

    var client = new FederationMasterClientImpl(FEDERATION_MASTER, federationApiClient, clock);

    var issuer = URI.create("https://idp-tk.example.com");
    var federationFetchUrl = FEDERATION_MASTER.resolve("/fetch");

    var fedmasterKeypair = ECKeyPairGenerator.example();

    var fedmasterEntityConfigurationJws =
        federationFetchFedmasterConfiguration(federationFetchUrl, fedmasterKeypair);

    var trustedSectoralIdpKeypair = ECKeyPairGenerator.generate();
    var trustedFederationStatement =
        trustedFederationStatement(issuer, trustedSectoralIdpKeypair, fedmasterKeypair);

    var untrustedSectoralIdpKeypair = ECKeyPairGenerator.generate();
    var sectoralEntityConfiguration =
        badSignedSectoralIdpEntityConfiguration(
            issuer, trustedSectoralIdpKeypair, untrustedSectoralIdpKeypair);

    when(federationApiClient.fetchEntityConfiguration(FEDERATION_MASTER))
        .thenReturn(fedmasterEntityConfigurationJws);

    when(federationApiClient.fetchFederationStatement(
            federationFetchUrl, FEDERATION_MASTER.toString(), issuer.toString()))
        .thenReturn(trustedFederationStatement);

    when(federationApiClient.fetchEntityConfiguration(issuer))
        .thenReturn(sectoralEntityConfiguration);

    // when
    var e = assertThrows(RuntimeException.class, () -> client.establishIdpTrust(issuer));

    // then
    assertEquals(
        "entity statement of 'https://idp-tk.example.com' has a bad signature", e.getMessage());
  }

  @Test
  void establishTrust_configurationExpired() {

    var client = new FederationMasterClientImpl(FEDERATION_MASTER, federationApiClient, clock);

    var issuer = URI.create("https://idp-tk.example.com");
    var federationFetchUrl = FEDERATION_MASTER.resolve("/fetch");

    var fedmasterKeypair = ECKeyPairGenerator.example();

    var fedmasterEntityConfigurationJws =
        federationFetchFedmasterConfiguration(federationFetchUrl, fedmasterKeypair);

    var trustedSectoralIdpKeypair = ECKeyPairGenerator.generate();
    var trustedFederationStatement =
        trustedFederationStatement(issuer, trustedSectoralIdpKeypair, fedmasterKeypair);

    var sectoralEntityConfiguration =
        expiredIdpEntityConfiguration(issuer, trustedSectoralIdpKeypair);

    when(federationApiClient.fetchEntityConfiguration(FEDERATION_MASTER))
        .thenReturn(fedmasterEntityConfigurationJws);

    when(federationApiClient.fetchFederationStatement(
            federationFetchUrl, FEDERATION_MASTER.toString(), issuer.toString()))
        .thenReturn(trustedFederationStatement);

    when(federationApiClient.fetchEntityConfiguration(issuer))
        .thenReturn(sectoralEntityConfiguration);

    // when
    var e = assertThrows(RuntimeException.class, () -> client.establishIdpTrust(issuer));

    // then
    assertEquals(
        "entity statement of 'https://idp-tk.example.com' expired or not yet valid",
        e.getMessage());
  }

  @Test
  void establishTrust_expiredFederationStatement() {

    var client = new FederationMasterClientImpl(FEDERATION_MASTER, federationApiClient, clock);

    var issuer = URI.create("https://idp-tk.example.com");
    var federationFetchUrl = FEDERATION_MASTER.resolve("/fetch");

    var fedmasterKeypair = ECKeyPairGenerator.example();

    var fedmasterEntityConfigurationJws =
        federationFetchFedmasterConfiguration(federationFetchUrl, fedmasterKeypair);

    var trustedSectoralIdpKeypair = ECKeyPairGenerator.generate();
    var trustedFederationStatement =
        expiredFederationStatement(issuer, trustedSectoralIdpKeypair, fedmasterKeypair);

    when(federationApiClient.fetchEntityConfiguration(FEDERATION_MASTER))
        .thenReturn(fedmasterEntityConfigurationJws);

    when(federationApiClient.fetchFederationStatement(
            federationFetchUrl, FEDERATION_MASTER.toString(), issuer.toString()))
        .thenReturn(trustedFederationStatement);

    // when
    var e = assertThrows(RuntimeException.class, () -> client.establishIdpTrust(issuer));

    // then
    assertEquals(
        "federation statement of 'https://idp-tk.example.com' expired or not yet valid",
        e.getMessage());
  }

  @Test
  void establishTrust_badSignatureFederationStatement() {

    var client = new FederationMasterClientImpl(FEDERATION_MASTER, federationApiClient, clock);

    var issuer = URI.create("https://idp-tk.example.com");
    var federationFetchUrl = FEDERATION_MASTER.resolve("/fetch");

    var fedmasterKeypair = ECKeyPairGenerator.example();

    var fedmasterEntityConfigurationJws =
        federationFetchFedmasterConfiguration(federationFetchUrl, fedmasterKeypair);

    var trustedSectoralIdpKeypair = ECKeyPairGenerator.generate();

    var badKeypair = ECKeyPairGenerator.generate();
    var trustedFederationStatement =
        trustedFederationStatement(issuer, trustedSectoralIdpKeypair, badKeypair);

    when(federationApiClient.fetchEntityConfiguration(FEDERATION_MASTER))
        .thenReturn(fedmasterEntityConfigurationJws);

    when(federationApiClient.fetchFederationStatement(
            federationFetchUrl, FEDERATION_MASTER.toString(), issuer.toString()))
        .thenReturn(trustedFederationStatement);

    // when
    var e = assertThrows(RuntimeException.class, () -> client.establishIdpTrust(issuer));

    // then
    assertEquals(
        "federation statement of 'https://idp-tk.example.com' has a bad signature", e.getMessage());
  }

  @Test
  void establishTrust() {

    var client = new FederationMasterClientImpl(FEDERATION_MASTER, federationApiClient, clock);

    var issuer = URI.create("https://idp-tk.example.com");
    var federationFetchUrl = FEDERATION_MASTER.resolve("/fetch");

    var fedmasterKeypair = ECKeyPairGenerator.example();

    var fedmasterEntityConfigurationJws =
        federationFetchFedmasterConfiguration(federationFetchUrl, fedmasterKeypair);

    var sectoralIdpKeypair = ECKeyPairGenerator.generate();
    var trustedFederationStatement =
        trustedFederationStatement(issuer, sectoralIdpKeypair, fedmasterKeypair);
    var sectoralEntityConfiguration = sectoralIdpEntityConfiguration(issuer, sectoralIdpKeypair);

    when(federationApiClient.fetchEntityConfiguration(FEDERATION_MASTER))
        .thenReturn(fedmasterEntityConfigurationJws);

    when(federationApiClient.fetchFederationStatement(
            federationFetchUrl, FEDERATION_MASTER.toString(), issuer.toString()))
        .thenReturn(trustedFederationStatement);

    when(federationApiClient.fetchEntityConfiguration(issuer))
        .thenReturn(sectoralEntityConfiguration);

    // when
    var entityStatementJWS = client.establishIdpTrust(issuer);

    // then
    assertEquals(entityStatementJWS.body().sub(), issuer.toString());
  }

  private EntityStatementJWS badSignedSectoralIdpEntityConfiguration(
      URI sub, ECKeyPair sectoralIdpKeyPair, ECKeyPair actualJwksKeys) {

    var publicJwks = JwksUtils.toPublicJwks(actualJwksKeys);

    var body =
        EntityStatement.create()
            .iss(sub.toString())
            .sub(sub.toString())
            .exp(NOW.plusSeconds(60))
            .jwks(publicJwks)
            .build();

    var signed =
        JwsUtils.toJws(JwksUtils.toJwks(sectoralIdpKeyPair), JsonCodec.writeValueAsString(body));

    return new EntityStatementJWS(signed, body);
  }

  private EntityStatementJWS expiredIdpEntityConfiguration(URI sub, ECKeyPair sectoralIdpKeyPair) {

    var publicJwks = JwksUtils.toPublicJwks(sectoralIdpKeyPair);

    var body =
        EntityStatement.create()
            .iss(sub.toString())
            .sub(sub.toString())
            .exp(NOW.minusSeconds(60))
            .jwks(publicJwks)
            .build();

    var signed =
        JwsUtils.toJws(JwksUtils.toJwks(sectoralIdpKeyPair), JsonCodec.writeValueAsString(body));

    return new EntityStatementJWS(signed, body);
  }

  private EntityStatementJWS sectoralIdpEntityConfiguration(URI sub, ECKeyPair sectoralIdpKeyPair) {

    var publicJwks = JwksUtils.toPublicJwks(sectoralIdpKeyPair);

    var body =
        EntityStatement.create()
            .iss(sub.toString())
            .sub(sub.toString())
            .exp(NOW.plusSeconds(60))
            .jwks(publicJwks)
            .build();

    var signed =
        JwsUtils.toJws(JwksUtils.toJwks(sectoralIdpKeyPair), JsonCodec.writeValueAsString(body));

    return new EntityStatementJWS(signed, body);
  }

  private EntityStatementJWS expiredFederationStatement(
      URI sub, ECKeyPair sectoralIdpKeyPair, ECKeyPair fedmasterKeyPair) {

    var publicJwks = JwksUtils.toPublicJwks(sectoralIdpKeyPair);

    var body =
        EntityStatement.create()
            .iss(FEDERATION_MASTER.toString())
            .sub(sub.toString())
            .exp(NOW.minusSeconds(60))
            .jwks(publicJwks)
            .build();

    var signed =
        JwsUtils.toJws(JwksUtils.toJwks(fedmasterKeyPair), JsonCodec.writeValueAsString(body));

    return new EntityStatementJWS(signed, body);
  }

  private EntityStatementJWS trustedFederationStatement(
      URI sub, ECKeyPair sectoralIdpKeyPair, ECKeyPair fedmasterKeyPair) {

    var publicJwks = JwksUtils.toPublicJwks(sectoralIdpKeyPair);

    var body =
        EntityStatement.create()
            .iss(FEDERATION_MASTER.toString())
            .sub(sub.toString())
            .exp(NOW.plusSeconds(60))
            .jwks(publicJwks)
            .build();

    var signed =
        JwsUtils.toJws(JwksUtils.toJwks(fedmasterKeyPair), JsonCodec.writeValueAsString(body));

    return new EntityStatementJWS(signed, body);
  }

  private EntityStatementJWS federationFetchFedmasterConfiguration(
      URI fetchUrl, ECKeyPair keyPair) {

    var publicJwks = JwksUtils.toPublicJwks(keyPair);

    var body =
        EntityStatement.create()
            .sub(FEDERATION_MASTER.toString())
            .iss(FEDERATION_MASTER.toString())
            .exp(NOW.plusSeconds(60))
            .jwks(publicJwks)
            .metadata(
                Metadata.create()
                    .federationEntity(
                        FederationEntity.create()
                            .federationFetchEndpoint(fetchUrl.toString())
                            .build())
                    .build())
            .build();

    var signed = JwsUtils.toJws(JwksUtils.toJwks(keyPair), JsonCodec.writeValueAsString(body));

    return new EntityStatementJWS(signed, body);
  }

  private EntityStatementJWS expiredFedmasterConfiguration(ECKeyPair keyPair) {

    var publicJwks = JwksUtils.toPublicJwks(keyPair);

    var body =
        EntityStatement.create()
            .sub(FEDERATION_MASTER.toString())
            .iss(FEDERATION_MASTER.toString())
            .exp(NOW.minusSeconds(60))
            .jwks(publicJwks)
            .build();

    var signed = JwsUtils.toJws(JwksUtils.toJwks(keyPair), JsonCodec.writeValueAsString(body));

    return new EntityStatementJWS(signed, body);
  }

  private EntityStatementJWS badSignatureFedmasterConfiguration(
      ECKeyPair keyPair, ECKeyPair unrelatedKeyPair) {

    var publicJwks = JwksUtils.toPublicJwks(keyPair);

    var body =
        EntityStatement.create()
            .sub(FEDERATION_MASTER.toString())
            .iss(FEDERATION_MASTER.toString())
            .exp(NOW.plusSeconds(60))
            .jwks(publicJwks)
            .build();

    var signed =
        JwsUtils.toJws(JwksUtils.toJwks(unrelatedKeyPair), JsonCodec.writeValueAsString(body));

    return new EntityStatementJWS(signed, body);
  }
}
