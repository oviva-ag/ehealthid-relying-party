package com.oviva.ehealthid.fedclient.api;

import java.time.Instant;

public interface TemporalValid {

  boolean isValidAt(Instant pointInTime);
}
