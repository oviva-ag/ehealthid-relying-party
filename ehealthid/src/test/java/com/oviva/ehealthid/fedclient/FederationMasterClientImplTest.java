package com.oviva.ehealthid.fedclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.fedclient.api.EntityStatement;
import com.oviva.ehealthid.fedclient.api.EntityStatement.FederationEntity;
import com.oviva.ehealthid.fedclient.api.EntityStatement.Metadata;
import com.oviva.ehealthid.fedclient.api.EntityStatementJWS;
import com.oviva.ehealthid.fedclient.api.FederationApiClient;
import com.oviva.ehealthid.fedclient.api.IdpList;
import com.oviva.ehealthid.fedclient.api.IdpList.IdpEntity;
import com.oviva.ehealthid.fedclient.api.IdpListJWS;
import com.oviva.ehealthid.test.ECKeyGenerator;
import com.oviva.ehealthid.util.JsonCodec;
import com.oviva.ehealthid.util.JwsUtils;
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

  public static JWKSet toPublicJwks(ECKey key) {
    try {

      if (key.getKeyID() == null || key.getKeyID().isBlank()) {
        key = new ECKey.Builder(key).keyIDFromThumbprint().build();
      }

      var pub = key.toPublicJWK();

      return new JWKSet(pub);
    } catch (JOSEException e) {
      throw new IllegalArgumentException("bad key", e);
    }
  }

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

    var fedmasterKeypair = ECKeyGenerator.example();

    var fedmasterEntityConfigurationJws = expiredFedmasterConfiguration(fedmasterKeypair);

    when(federationApiClient.fetchEntityConfiguration(FEDERATION_MASTER))
        .thenReturn(fedmasterEntityConfigurationJws);

    // when
    var e = assertThrows(FederationException.class, () -> client.establishIdpTrust(issuer));

    // then
    assertEquals(
        "entity statement of 'https://fedmaster.example.com' expired or not yet valid",
        e.getMessage());
  }

  @Test
  void establishTrust_badFedmasterConfigSignature() {

    var client = new FederationMasterClientImpl(FEDERATION_MASTER, federationApiClient, clock);

    var issuer = URI.create("https://idp-tk.example.com");

    var fedmasterKeypair = ECKeyGenerator.example();
    var unrelatedKeypair = ECKeyGenerator.generate();

    var fedmasterEntityConfigurationJws =
        badSignatureFedmasterConfiguration(fedmasterKeypair, unrelatedKeypair);

    when(federationApiClient.fetchEntityConfiguration(FEDERATION_MASTER))
        .thenReturn(fedmasterEntityConfigurationJws);

    // when
    var e = assertThrows(FederationException.class, () -> client.establishIdpTrust(issuer));

    // then
    assertEquals(
        "entity statement of 'https://fedmaster.example.com' has a bad signature", e.getMessage());
  }

  @Test
  void establishTrust_configurationWithUnknownSignature() {

    var client = new FederationMasterClientImpl(FEDERATION_MASTER, federationApiClient, clock);

    var issuer = URI.create("https://idp-tk.example.com");
    var federationFetchUrl = FEDERATION_MASTER.resolve("/fetch");

    var fedmasterKeypair = ECKeyGenerator.example();

    var fedmasterEntityConfigurationJws =
        federationFetchFedmasterConfiguration(federationFetchUrl, fedmasterKeypair);

    var trustedSectoralIdpKeypair = ECKeyGenerator.generate();
    var trustedFederationStatement =
        trustedFederationStatement(issuer, trustedSectoralIdpKeypair, fedmasterKeypair);

    var untrustedSectoralIdpKeypair = ECKeyGenerator.generate();
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
    var e = assertThrows(FederationException.class, () -> client.establishIdpTrust(issuer));

    // then
    assertEquals("federation statement untrusted: sub=https://idp-tk.example.com", e.getMessage());
  }

  @Test
  void establishTrust_configurationWithBadJwks() throws JOSEException {

    var client = new FederationMasterClientImpl(FEDERATION_MASTER, federationApiClient, clock);

    var issuer = URI.create("https://idp-tk.example.com");
    var federationFetchUrl = FEDERATION_MASTER.resolve("/fetch");

    var fedmasterKeypair = ECKeyGenerator.example();

    var fedmasterEntityConfigurationJws =
        federationFetchFedmasterConfiguration(federationFetchUrl, fedmasterKeypair);

    var trustedSectoralIdpKeypair =
        new com.nimbusds.jose.jwk.gen.ECKeyGenerator(Curve.P_256).generate();
    var trustedFederationStatement =
        trustedFederationStatement(issuer, trustedSectoralIdpKeypair, fedmasterKeypair);

    var untrustedSectoralIdpKeypair =
        new com.nimbusds.jose.jwk.gen.ECKeyGenerator(Curve.P_256).generate();
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
    var e = assertThrows(FederationException.class, () -> client.establishIdpTrust(issuer));

    // then
    assertEquals("federation statement untrusted: sub=https://idp-tk.example.com", e.getMessage());
  }

  @Test
  void establishTrust_configurationExpired() {

    var client = new FederationMasterClientImpl(FEDERATION_MASTER, federationApiClient, clock);

    var issuer = URI.create("https://idp-tk.example.com");
    var federationFetchUrl = FEDERATION_MASTER.resolve("/fetch");

    var fedmasterKeypair = ECKeyGenerator.example();

    var fedmasterEntityConfigurationJws =
        federationFetchFedmasterConfiguration(federationFetchUrl, fedmasterKeypair);

    var trustedSectoralIdpKeypair = ECKeyGenerator.generate();
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
    var e = assertThrows(FederationException.class, () -> client.establishIdpTrust(issuer));

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

    var fedmasterKeypair = ECKeyGenerator.example();

    var fedmasterEntityConfigurationJws =
        federationFetchFedmasterConfiguration(federationFetchUrl, fedmasterKeypair);

    var trustedSectoralIdpKeypair = ECKeyGenerator.generate();
    var trustedFederationStatement =
        expiredFederationStatement(issuer, trustedSectoralIdpKeypair, fedmasterKeypair);

    when(federationApiClient.fetchEntityConfiguration(FEDERATION_MASTER))
        .thenReturn(fedmasterEntityConfigurationJws);

    when(federationApiClient.fetchFederationStatement(
            federationFetchUrl, FEDERATION_MASTER.toString(), issuer.toString()))
        .thenReturn(trustedFederationStatement);

    // when
    var e = assertThrows(FederationException.class, () -> client.establishIdpTrust(issuer));

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

    var fedmasterKeypair = ECKeyGenerator.example();

    var fedmasterEntityConfigurationJws =
        federationFetchFedmasterConfiguration(federationFetchUrl, fedmasterKeypair);

    var trustedSectoralIdpKeypair = ECKeyGenerator.generate();

    var badKeypair = ECKeyGenerator.generate();
    var trustedFederationStatement =
        trustedFederationStatement(issuer, trustedSectoralIdpKeypair, badKeypair);

    when(federationApiClient.fetchEntityConfiguration(FEDERATION_MASTER))
        .thenReturn(fedmasterEntityConfigurationJws);

    when(federationApiClient.fetchFederationStatement(
            federationFetchUrl, FEDERATION_MASTER.toString(), issuer.toString()))
        .thenReturn(trustedFederationStatement);

    // when
    var e = assertThrows(FederationException.class, () -> client.establishIdpTrust(issuer));

    // then
    assertEquals(
        "federation statement of 'https://idp-tk.example.com' has a bad signature", e.getMessage());
  }

  @Test
  void establishTrust() {

    var client = new FederationMasterClientImpl(FEDERATION_MASTER, federationApiClient, clock);

    var issuer = URI.create("https://idp-tk.example.com");
    var federationFetchUrl = FEDERATION_MASTER.resolve("/fetch");

    var fedmasterKeypair = ECKeyGenerator.example();

    var fedmasterEntityConfigurationJws =
        federationFetchFedmasterConfiguration(federationFetchUrl, fedmasterKeypair);

    var sectoralIdpKeypair = ECKeyGenerator.generate();
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
      URI sub, ECKey sectoralIdpKeyPair, ECKey actualJwksKeys) {

    var publicJwks = toPublicJwks(actualJwksKeys);

    var body =
        EntityStatement.create()
            .iss(sub.toString())
            .sub(sub.toString())
            .exp(NOW.plusSeconds(60))
            .jwks(publicJwks)
            .build();

    var signed = JwsUtils.toJws(sectoralIdpKeyPair, JsonCodec.writeValueAsString(body));

    return new EntityStatementJWS(signed, body);
  }

  private EntityStatementJWS expiredIdpEntityConfiguration(URI sub, ECKey sectoralIdpKeyPair) {

    var publicJwks = toPublicJwks(sectoralIdpKeyPair);

    var body =
        EntityStatement.create()
            .iss(sub.toString())
            .sub(sub.toString())
            .exp(NOW.minusSeconds(60))
            .jwks(publicJwks)
            .build();

    var signed = JwsUtils.toJws(sectoralIdpKeyPair, JsonCodec.writeValueAsString(body));

    return new EntityStatementJWS(signed, body);
  }

  private EntityStatementJWS sectoralIdpEntityConfiguration(URI sub, ECKey sectoralIdpKeyPair) {

    var publicJwks = toPublicJwks(sectoralIdpKeyPair);

    var body =
        EntityStatement.create()
            .iss(sub.toString())
            .sub(sub.toString())
            .exp(NOW.plusSeconds(60))
            .jwks(publicJwks)
            .build();

    var signed = JwsUtils.toJws(sectoralIdpKeyPair, JsonCodec.writeValueAsString(body));

    return new EntityStatementJWS(signed, body);
  }

  private EntityStatementJWS expiredFederationStatement(
      URI sub, ECKey sectoralIdpKeyPair, ECKey fedmasterKeyPair) {

    var publicJwks = toPublicJwks(sectoralIdpKeyPair);

    var body =
        EntityStatement.create()
            .iss(FEDERATION_MASTER.toString())
            .sub(sub.toString())
            .exp(NOW.minusSeconds(60))
            .jwks(publicJwks)
            .build();

    var signed = JwsUtils.toJws(fedmasterKeyPair, JsonCodec.writeValueAsString(body));

    return new EntityStatementJWS(signed, body);
  }

  private EntityStatementJWS trustedFederationStatement(
      URI sub, ECKey sectoralIdpKeyPair, ECKey fedmasterKeyPair) {

    var publicJwks = toPublicJwks(sectoralIdpKeyPair);

    var body =
        EntityStatement.create()
            .iss(FEDERATION_MASTER.toString())
            .sub(sub.toString())
            .exp(NOW.plusSeconds(60))
            .jwks(publicJwks)
            .build();

    var signed = JwsUtils.toJws(fedmasterKeyPair, JsonCodec.writeValueAsString(body));

    return new EntityStatementJWS(signed, body);
  }

  private EntityStatementJWS federationFetchFedmasterConfiguration(URI fetchUrl, ECKey key) {

    var publicJwks = toPublicJwks(key);

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

    var signed = JwsUtils.toJws(key, JsonCodec.writeValueAsString(body));

    return new EntityStatementJWS(signed, body);
  }

  private EntityStatementJWS expiredFedmasterConfiguration(ECKey key) {

    var publicJwks = toPublicJwks(key);

    var body =
        EntityStatement.create()
            .sub(FEDERATION_MASTER.toString())
            .iss(FEDERATION_MASTER.toString())
            .exp(NOW.minusSeconds(60))
            .jwks(publicJwks)
            .build();

    var signed = JwsUtils.toJws(key, JsonCodec.writeValueAsString(body));

    return new EntityStatementJWS(signed, body);
  }

  private EntityStatementJWS badSignatureFedmasterConfiguration(ECKey key, ECKey unrelatedKey) {

    var publicJwks = toPublicJwks(key);

    var body =
        EntityStatement.create()
            .sub(FEDERATION_MASTER.toString())
            .iss(FEDERATION_MASTER.toString())
            .exp(NOW.plusSeconds(60))
            .jwks(publicJwks)
            .build();

    var signed = JwsUtils.toJws(unrelatedKey, JsonCodec.writeValueAsString(body));

    return new EntityStatementJWS(signed, body);
  }
}
