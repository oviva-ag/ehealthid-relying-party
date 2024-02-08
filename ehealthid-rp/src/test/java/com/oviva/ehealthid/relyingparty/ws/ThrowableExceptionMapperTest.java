package com.oviva.ehealthid.relyingparty.ws;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith({MockitoExtension.class})
class ThrowableExceptionMapperTest {

  private static final URI REQUEST_URI = URI.create("https://example.com/my/request");
  @Mock UriInfo uriInfo;
  @Mock Request request;
  @Mock HttpHeaders headers;
  @Spy Logger logger = LoggerFactory.getLogger(ThrowableExceptionMapper.class);
  @InjectMocks ThrowableExceptionMapper mapper = new ThrowableExceptionMapper();

  @Test
  void toResponse() {

    when(uriInfo.getRequestUri()).thenReturn(REQUEST_URI);

    doReturn("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")
        .when(headers)
        .getHeaderString("traceparent");
    doReturn(
            "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko)")
        .when(headers)
        .getHeaderString("user-agent");

    // when
    var res = mapper.toResponse(new IllegalArgumentException());

    // then
    assertEquals(500, res.getStatus());
  }

  @Test
  void toResponse_propagateWebApplicationException() {

    when(uriInfo.getRequestUri()).thenReturn(REQUEST_URI);

    var ex = new NotFoundException();

    // when
    var res = mapper.toResponse(ex);

    // then
    assertEquals(404, res.getStatus());
  }

  @Test
  void toResponse_isLogged() {

    when(uriInfo.getRequestUri()).thenReturn(REQUEST_URI);

    // when
    mapper.toResponse(new UnsupportedOperationException());

    // then
    verify(logger).atError();
  }

  @Test
  void toResponse_withBody() {

    when(uriInfo.getRequestUri()).thenReturn(REQUEST_URI);

    when(headers.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.WILDCARD_TYPE));

    // when
    var res = mapper.toResponse(new IllegalArgumentException());

    // then
    assertEquals(500, res.getStatus());
    assertEquals(MediaType.TEXT_HTML_TYPE, res.getMediaType());
    assertNotNull(res.getEntity());
  }
}
