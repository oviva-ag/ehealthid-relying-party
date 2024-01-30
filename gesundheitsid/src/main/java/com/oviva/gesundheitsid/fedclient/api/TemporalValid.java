package com.oviva.gesundheitsid.fedclient.api;

import java.time.Instant;

public interface TemporalValid {

  boolean isValidAt(Instant pointInTime);
}
