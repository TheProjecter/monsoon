/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.    
 */
package org.apache.weasel;

import org.jetty.util.Utf8StringBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Represents a single WebSocket message, which may not be complete.
 */
public abstract class ReadableWebSocketMessage extends WebSocketMessage {

  protected ReadableWebSocketMessage() {
    super();
  }

  protected ReadableWebSocketMessage(int opcode) {
    super();
    beginMessage(opcode);
  }

  // protected abstract void processInitialBuffer(ByteBuffer buf)
  // throws IOException;

  public final int read(ByteBuffer buf) throws IOException {
    return read(buf, true);
  }

  /**
   * Reads the binary content of a message, blocking until all of it is
   * available.
   * 
   * @return a byte array containing the binary content of the message
   * @throws IOException if the message is not a binary message or an IO error
   *           occurs while reading it
   */
  public byte[] readBinary() throws IOException {
    failIfOpcodeNot(OPCODE_BINARY);
    ArrayList<ByteBuffer> buffers = new ArrayList<ByteBuffer>();
    int totlen = 0;
    while (true) {
      ByteBuffer buf = ByteBuffer.allocate(BUF_SIZE);
      buf.clear();
      int n = read(buf, true);
      if (n <= 0) {
        break;
      }
      totlen += n;
      buf.flip();
      buffers.add(buf);
    }
    endRead();
    byte[] bytes = new byte[totlen];
    int ofs = 0;
    for (ByteBuffer buf : buffers) {
      int n = buf.remaining();
      buf.get(bytes, ofs, n);
      ofs += n;
    }
    return bytes;
  }

  public InputStream readBinaryStream() throws IOException {
    return readStream(OPCODE_BINARY);
  }

  /**
   * Reads the text content of the message, blocking until all of it is
   * available.
   * 
   * @return a String containing the text content of the message
   * @throws IOException if the message is not a text message or an IO error
   *           occurs while reading it
   */
  public String readText() throws IOException {
    failIfOpcodeNot(OPCODE_TEXT);
    Utf8StringBuilder utf8 = new Utf8StringBuilder();
    ByteBuffer buf = ByteBuffer.allocate(BUF_SIZE);
    buf.clear();
    int totn = 0;
    int n;
    while ((n = read(buf, true)) > 0) {
      utf8.append(buf.array(), buf.arrayOffset(), n);
      totn += n;
    }
    if (n < 0 && totn == 0) {
      throw new IOException("Error while reading the message");
    }
    endRead();
    return utf8.toString();
  }

  /**
   * Called when a read is complete.
   */
  protected abstract void endRead();

  public Reader readTextStream() throws IOException {
    return new InputStreamReader(readStream(OPCODE_TEXT), UTF8);
  }

  protected abstract int read(ByteBuffer buf, boolean blocking)
      throws IOException;

  /**
   * Throws an IOException if the opcode of this message doesn't match the
   * expected value.
   * 
   * @param expectedOpcode
   * @throws IOException
   */
  private void failIfOpcodeNot(int expectedOpcode) throws IOException {
    if (getOpcode() != expectedOpcode) {
      throw new IOException("Requested opcode type of " + expectedOpcode
          + ", but found " + getOpcode());
    }
  }

  private InputStream readStream(int expectedOpcode) throws IOException {
    failIfOpcodeNot(expectedOpcode);
    final ByteBuffer buf = ByteBuffer.allocate(BUF_SIZE);
    buf.clear();
    read(buf, true);
    buf.flip();
    return new InputStream() {
      @Override
      public void close() throws IOException {
        super.close();
        endRead();
      }

      @Override
      public int read() throws IOException {
        while (!buf.hasRemaining()) {
          buf.clear();
          int n = ReadableWebSocketMessage.this.read(buf, true);
          if (n <= 0) {
            return -1;
          }
        }
        return buf.get();
      }

      @Override
      public int read(byte[] bytes, int ofs, int len) throws IOException {
        int totread = 0;
        while (len > 0) {
          int n = Math.min(len, buf.remaining());
          buf.get(bytes, ofs, n);
          ofs += n;
          len -= n;
          totread += n;
          if (len > 0) {
            buf.clear();
            ReadableWebSocketMessage.this.read(buf, true);
            buf.flip();
            if (!buf.hasRemaining()) {
              break;
            }
          }
        }
        return totread;
      }
    };
  }
}
