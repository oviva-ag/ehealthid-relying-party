package com.oviva.gesundheitsid.auth.steps;

import com.oviva.gesundheitsid.auth.IdTokenJWS;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.URI;

public interface TrustedSectoralIdpStep {

  @NonNull
  URI idpRedirectUri();

  @NonNull
  IdTokenJWS exchangeSectoralIdpCode(@NonNull String code, @NonNull String codeVerifier);
}
