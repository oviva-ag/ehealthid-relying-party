package com.oviva.ehealthid.relyingparty.test;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class PropertiesUtils {

  public static Map<String, String> loadFromString(String raw) {
    try {
      var properties = new Properties();
      properties.load(new StringReader(raw));
      var map = new HashMap<String, String>();
      for (String name : properties.stringPropertyNames()) {
        map.put(name, properties.getProperty(name));
      }
      return map;
    } catch (IOException e) {
      throw new IllegalStateException("failed to parse", e);
    }
  }
}
