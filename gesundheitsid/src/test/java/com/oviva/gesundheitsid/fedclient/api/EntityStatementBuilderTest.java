package com.oviva.gesundheitsid.fedclient.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.gesundheitsid.fedclient.api.EntityStatement.FederationEntity;
import com.oviva.gesundheitsid.fedclient.api.EntityStatement.Metadata;
import com.oviva.gesundheitsid.fedclient.api.EntityStatement.OpenIdRelyingParty;
import com.oviva.gesundheitsid.fedclient.api.EntityStatement.OpenidProvider;
import com.oviva.gesundheitsid.test.ECKeyGenerator;
import com.oviva.gesundheitsid.util.JsonCodec;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntityStatementBuilderTest {

  @Test
  void build() {
    var fedmaster = "https://fedmaster.example.com";
    var sub = "https://aok-testfalen.example.com";

    var now = Instant.parse("2023-01-04T00:14:00.000Z");

    var jwk = ECKeyGenerator.example().toPublicJWK();

    var es =
        EntityStatement.create()
            .iss(sub) // issuer is the subject in an entity configuration
            .sub(sub)
            .exp(now.plusSeconds(600))
            .iat(now.minusSeconds(100))
            .nbf(now.minusSeconds(1))
            .authorityHints(List.of(fedmaster))
            .jwks(new JWKSet(jwk))
            .metadata(
                Metadata.create()
                    .federationEntity(
                        FederationEntity.create()
                            .contacts("alice@example.com")
                            .homepageUri("https://dev.example.com")
                            .name("Example Inc.")
                            .build())
                    .openidProvider(
                        OpenidProvider.create()
                            .authorizationEndpoint(sub + "/auth")
                            .tokenEndpoint(sub + "/token")
                            .pushedAuthorizationRequestEndpoint(sub + "/par")
                            .issuer(sub)
                            .scopesSupported(List.of("openid", "email"))
                            .grantTypesSupported(List.of("direct", "code"))
                            .userTypeSupported(List.of("IP"))
                            .requirePushedAuthorizationRequests(true)
                            .build())
                    .openIdRelyingParty(
                        OpenIdRelyingParty.create()
                            .clientName(sub)
                            .clientRegistrationTypes(List.of("magic"))
                            .grantTypes(List.of("direct"))
                            .idTokenEncryptedResponseAlg("ecdh-es")
                            .idTokenEncryptedResponseEnc("AES123")
                            .idTokenSignedResponseAlg("P256")
                            .jwks(new JWKSet(List.of(jwk)))
                            .organizationName("Example Inc.")
                            .redirectUris(
                                List.of(
                                    "https://aok-testfalen.example.com/callback",
                                    "http://localhost:8080/test"))
                            .requirePushedAuthorizationRequests(true)
                            .responseTypes(List.of("code", "token"))
                            .scope("openid closedid")
                            .build())
                    .build())
            .build();

    assertEquals(
        """
            {"iss":"https://aok-testfalen.example.com","sub":"https://aok-testfalen.example.com","iat":1672791140,"exp":1672791840,"nbf":1672791239,"jwks":{"keys":[{"kty":"EC","crv":"P-256","kid":"1272H4X20HLaS_zwI8c7ho5REoZP1uBUvcfm7bJ3jpQ","x":"dM9WxN8ihxfAUq9aqrhAdPxoGDYt1Lk7eNK09vsU414","y":"Qusto6wrCXlSpJ9NwOGwx2TEpXZp_rCho7InBqYyZTA"}]},"authority_hints":["https://fedmaster.example.com"],"metadata":{"openid_provider":{"pushed_authorization_request_endpoint":"https://aok-testfalen.example.com/par","issuer":"https://aok-testfalen.example.com","require_pushed_authorization_requests":true,"token_endpoint":"https://aok-testfalen.example.com/token","authorization_endpoint":"https://aok-testfalen.example.com/auth","scopes_supported":["openid","email"],"grant_types_supported":["direct","code"],"user_type_supported":["IP"]},"openid_relying_party":{"organization_name":"Example Inc.","client_name":"https://aok-testfalen.example.com","redirect_uris":["https://aok-testfalen.example.com/callback","http://localhost:8080/test"],"response_types":["code","token"],"client_registration_types":["magic"],"grant_types":["direct"],"require_pushed_authorization_requests":true,"scope":"openid closedid","id_token_signed_response_alg":"P256","id_token_encrypted_response_alg":"ecdh-es","id_token_encrypted_response_enc":"AES123","jwks":{"keys":[{"kty":"EC","crv":"P-256","kid":"1272H4X20HLaS_zwI8c7ho5REoZP1uBUvcfm7bJ3jpQ","x":"dM9WxN8ihxfAUq9aqrhAdPxoGDYt1Lk7eNK09vsU414","y":"Qusto6wrCXlSpJ9NwOGwx2TEpXZp_rCho7InBqYyZTA"}]}},"federation_entity":{"name":"Example Inc.","contacts":"alice@example.com","homepage_uri":"https://dev.example.com"}}}""",
        JsonCodec.writeValueAsString(es));
  }
}
