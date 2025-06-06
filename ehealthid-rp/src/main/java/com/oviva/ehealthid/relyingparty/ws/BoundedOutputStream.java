package com.oviva.ehealthid.relyingparty.ws;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class BoundedOutputStream extends FilterOutputStream {

  private final int limit;
  private int writtenBytes = 0;

  public BoundedOutputStream(OutputStream out, int limit) {
    super(out);
    this.limit = limit;
  }

  @Override
  public void write(int b) throws IOException {
    if (writtenBytes < limit || limit == -1) {
      super.write(b);
      writtenBytes++;
    }
  }

  @Override
  public void write(byte[] b) throws IOException {
    write(b, 0, b.length);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (limit == -1) {
      super.write(b, off, len);
      writtenBytes += len;
    } else {
      int count = Math.min(len, limit - writtenBytes);
      if (count > 0) {
        super.write(b, off, count);
        writtenBytes += count;
      }
    }
  }
}
