package com.oviva.ehealthid.auth.steps;

import com.oviva.ehealthid.fedclient.IdpEntry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

public interface SelectSectoralIdpStep {

  @NonNull
  List<IdpEntry> fetchIdpOptions();

  @NonNull
  TrustedSectoralIdpStep redirectToSectoralIdp(@NonNull String sectoralIdpIss);
}
