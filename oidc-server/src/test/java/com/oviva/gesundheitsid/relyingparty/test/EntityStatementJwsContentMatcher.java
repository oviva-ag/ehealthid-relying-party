package com.oviva.gesundheitsid.relyingparty.test;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.nimbusds.jose.JWSObject;
import java.io.IOException;
import java.text.ParseException;
import java.util.Optional;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

public class EntityStatementJwsContentMatcher<T> extends BaseMatcher<String> {

  private final JsonPointer pointer;
  private final Matcher<T> matcher;

  private EntityStatementJwsContentMatcher(JsonPointer pointer, Matcher<T> matcher) {
    this.pointer = pointer;
    this.matcher = matcher;
  }

  public static <T> EntityStatementJwsContentMatcher<T> jwsPayloadAt(
      String jsonPointer, Matcher<T> matcher) {
    return new EntityStatementJwsContentMatcher<>(JsonPointer.compile(jsonPointer), matcher);
  }

  @Override
  public boolean matches(Object actual) {
    if (!(actual instanceof String s)) {
      return false;
    }

    return getValue(s).map(matcher::matches).orElse(false);
  }

  private Optional<T> getValue(String wireJws) {

    try {
      var in = JWSObject.parse(wireJws);

      var om = new ObjectMapper();
      var tree = om.readTree(in.getPayload().toBytes());

      var node = tree.at(pointer);
      var value = om.treeToValue(node, new TypeReference<T>() {});
      return Optional.of(value);
    } catch (MismatchedInputException e) {
      return Optional.empty();
    } catch (ParseException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("JWS payload JSON pointer '%s' ".formatted(pointer));
    matcher.describeTo(description);
  }

  @Override
  public void describeMismatch(Object item, Description description) {
    if (!(item instanceof String s)) {
      description
          .appendText("not of type ")
          .appendValue(String.class.getName())
          .appendText(" but was ")
          .appendValue(item.getClass().getName());
      return;
    }
    getValue(s)
        .ifPresentOrElse(
            actual -> {
              matcher.describeMismatch(actual, description);
            },
            () -> {
              description.appendText("value not found at '%s'".formatted(pointer));
            });
  }
}
