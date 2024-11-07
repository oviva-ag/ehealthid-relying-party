package com.oviva.ehealthid.relyingparty.providers;

import com.nimbusds.jose.jwk.ECKey;
import java.net.URI;
import java.util.List;

public interface KeyStores {

  /**
   * Signing keys for our openIdProvider, for example the id_token issued by the relying party will
   * be signed with it.
   *
   * @return JWKS for the openIdProvider
   */
  KeyStore openIdProviderJwksKeystore();

  /**
   * @return the trusted keys of our party in the federation, the first key will be used for signing
   *     operations such as self-signing the entity-statement
   */
  KeyStore federationSigJwksKeystore();

  /**
   * @return the keys used for encryption between the federation and the relying party, i.e. to
   *     encrypt id_tokens
   */
  KeyStore relyingPartyEncJwksKeystore();

  /**
   * @param issuer the issuer of the certificate - i.e. itself for mTLS client certificates or a
   *     trusted CA
   * @return signing keys of our relying party, i.e. for mTLS client certificates
   */
  KeyStore relyingPartySigJwksKeystore(URI issuer);

  interface KeyStore {

    /**
     * keys in order of preference, i.e. if something is signed the first key in the list will be
     * preferred
     */
    List<ECKey> keys();
  }
}
