package com.oviva.ehealthid.relyingparty.svc;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimNames;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.URI;
import java.text.ParseException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the client `private_key_jwt` authentication for clients. The signing keys are fetched
 * via the provided JWK source.
 *
 * <p>See also:
 *
 * <ul>
 *   <li><a href="https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication">OpenID
 *       Spec<a>
 *   <li><a
 *       href="https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata">OpenID
 *       Discovery Spec</a>
 * </ul>
 */
public class ClientAuthenticator {

  public static final String CLIENT_ASSERTION_TYPE_PRIVATE_KEY_JWT =
      "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

  private static final Logger logger = LoggerFactory.getLogger(ClientAuthenticator.class);

  private final JWKSource<SecurityContext> jwkSource;

  private final URI baseUri;

  public ClientAuthenticator(JWKSource<SecurityContext> jwkSource, URI baseUri) {
    this.jwkSource = jwkSource;
    this.baseUri = baseUri;
  }

  public void authenticate(Request request) {

    try {
      if (!CLIENT_ASSERTION_TYPE_PRIVATE_KEY_JWT.equals(request.clientAssertionType())) {
        throw new AuthenticationException(
            "unsupported client_assertion_type='%s', expected '%s'"
                .formatted(request.clientAssertionType(), CLIENT_ASSERTION_TYPE_PRIVATE_KEY_JWT));
      }

      var processor = new DefaultJWTProcessor<>();

      var keySelector =
          new JWSVerificationKeySelector<>(
              Set.of(JWSAlgorithm.RS256, JWSAlgorithm.ES256), jwkSource);

      processor.setJWSKeySelector(keySelector);

      processor.setJWTClaimsSetVerifier(
          new DefaultJWTClaimsVerifier<>(
              new JWTClaimsSet.Builder()
                  .issuer(request.clientId())
                  .subject(request.clientId())
                  .audience(baseUri.toString())
                  .build(),
              Set.of(JWTClaimNames.JWT_ID, JWTClaimNames.EXPIRATION_TIME)));

      processor.process(request.clientAssertion(), null);

    } catch (ParseException e) {
      throw new AuthenticationException("failed to parse client assertion", e);
    } catch (BadJOSEException | JOSEException e) {
      logger
          .atDebug()
          .setCause(e)
          .log("failed to authenticate client '{}': {}", request.clientId, e.getMessage());
      throw new AuthenticationException("failed to verify client assertion", e);
    }
  }

  public record Request(String clientId, String clientAssertionType, String clientAssertion) {}
}
