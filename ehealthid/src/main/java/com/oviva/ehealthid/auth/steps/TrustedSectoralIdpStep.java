package com.oviva.ehealthid.auth.steps;

import com.oviva.ehealthid.auth.IdTokenJWS;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.URI;

public interface TrustedSectoralIdpStep {

  @NonNull
  URI idpRedirectUri();

  @NonNull
  IdTokenJWS exchangeSectoralIdpCode(@NonNull String code, @NonNull String codeVerifier);
}
