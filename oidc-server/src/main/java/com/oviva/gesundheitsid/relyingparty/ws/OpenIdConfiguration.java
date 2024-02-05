package com.oviva.gesundheitsid.relyingparty.ws;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata
public record OpenIdConfiguration(

    // REQUIRED. URL using the https scheme with no query or fragment components that the OP asserts
    // as its Issuer Identifier. If Issuer discovery is supported (see Section 2), this value MUST
    // be identical to the issuer value returned by WebFinger. This also MUST be identical to the
    // iss Claim value in ID Tokens issued from this Issuer.
    @JsonProperty("issuer") String issuer,

    // REQUIRED. URL of the OP's OAuth 2.0 Authorization Endpoint [OpenID.Core]. This URL MUST use
    // the https scheme and MAY contain port, path, and query parameter components.
    @JsonProperty("authorization_endpoint") String authorizationEndpoint,

    // URL of the OP's OAuth 2.0 Token Endpoint [OpenID.Core]. This is REQUIRED unless only the
    // Implicit Flow is used. This URL MUST use the https scheme and MAY contain port, path, and
    // query parameter components.
    @JsonProperty("token_endpoint") String tokenEndpoint,

    // REQUIRED. URL of the OP's JWK Set [JWK] document, which MUST use the https scheme. This
    // contains the signing key(s) the RP uses to validate signatures from the OP. The JWK Set MAY
    // also contain the Server's encryption key(s), which are used by RPs to encrypt requests to the
    // Server. When both signing and encryption keys are made available, a use (public key use)
    // parameter value is REQUIRED for all keys in the referenced JWK Set to indicate each key's
    // intended usage. Although some algorithms allow the same key to be used for both signatures
    // and encryption, doing so is NOT RECOMMENDED, as it is less secure. The JWK x5c parameter MAY
    // be used to provide X.509 representations of keys provided. When used, the bare key values
    // MUST still be present and MUST match those in the certificate. The JWK Set MUST NOT contain
    // private or symmetric key values.
    @JsonProperty("jwks_uri") String jwksUri,

    // RECOMMENDED. JSON array containing a list of the OAuth 2.0 [RFC6749] scope values that this
    // server supports. The server MUST support the openid scope value. Servers MAY choose not to
    // advertise some supported scope values even when this parameter is used, although those
    // defined in [OpenID.Core] SHOULD be listed, if supported.
    @JsonProperty("scopes_supported") List<String> scopesSupported,

    // REQUIRED. JSON array containing a list of the OAuth 2.0 response_type values that this OP
    // supports. Dynamic OpenID Providers MUST support the code, id_token, and the id_token token
    // Response Type values.
    @JsonProperty("response_types_supported") List<String> responseTypesSupported,

    // OPTIONAL. JSON array containing a list of the OAuth 2.0 Grant Type values that this OP
    // supports. Dynamic OpenID Providers MUST support the authorization_code and implicit Grant
    // Type values and MAY support other Grant Types. If omitted, the default value is
    // ["authorization_code", "implicit"].
    @JsonProperty("grant_types_supported") List<String> grantTypesSupported,

    // REQUIRED. JSON array containing a list of the Subject Identifier types that this OP supports.
    // Valid types include pairwise and public.
    // https://openid.net/specs/openid-connect-core-1_0.html#SubjectIDTypes
    @JsonProperty("subject_types_supported") List<String> subjectTypesSupported,

    // REQUIRED. JSON array containing a list of the JWS signing algorithms (alg values) supported
    // by the OP for the ID Token to encode the Claims in a JWT [JWT]. The algorithm RS256 MUST be
    // included. The value none MAY be supported but MUST NOT be used unless the Response Type used
    // returns no ID Token from the Authorization Endpoint (such as when using the Authorization
    // Code Flow).
    @JsonProperty("id_token_signing_alg_values_supported")
        List<String> idTokenSigningAlgValuesSupported,

    // OPTIONAL. JSON array containing a list of the JWE encryption algorithms (alg values)
    // supported by the OP for the ID Token to encode the Claims in a JWT [JWT].
    @JsonProperty("id_token_encryption_alg_values_supported")
        List<String> idTokenEncryptionAlgValuesSupported,

    // OPTIONAL. JSON array containing a list of the JWE encryption algorithms (enc values)
    // supported by the OP for the ID Token to encode the Claims in a JWT [JWT].
    @JsonProperty("id_token_encryption_enc_values_supported")
        List<String> idTokenEncryptionEncValuesSupported) {}
