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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * Represents a single WebSocket message, which may not be complete.
 */
public abstract class WritableWebSocketMessage extends WebSocketMessage {

  protected WritableWebSocketMessage() {
    super();
  }

  /**
   * Write a binary message, blocking until it can be completed.
   * 
   * @param bytes
   * @throws IOException
   */
  public void writeBinary(byte[] bytes) throws IOException {
    if (writeFrame(WebSocketMessage.OPCODE_BINARY, ByteBuffer.wrap(bytes),
        true, true) != bytes.length) {
      throw new IOException("IO error writing message");
    }
    endWrite();
  }

  /**
   * Send a binary message written on an OutputStream. The message is sent when
   * the stream is closed, though fragments may be sent earlier.
   * 
   * @return an OutputStream instance for the binary message being sent
   * @throws IOException
   */
  public OutputStream writeBinaryStream() throws IOException {
    return writeStream(WebSocketMessage.OPCODE_BINARY);
  }

  /**
   * Write a text message, blocking until it can be completed.
   * 
   * @param text
   * @throws IOException
   */
  public void writeText(String text) throws IOException {
    byte[] bytes = text.getBytes(UTF8);
    writeFrame(WebSocketMessage.OPCODE_TEXT, ByteBuffer.wrap(bytes), true, true);
    endWrite();
  }

  /**
   * Send a text message written on a PrintWriter. The message is sent when the
   * writer is closed, though fragments may be sent earlier.
   * 
   * @return a PrintWriter instance for the text message beng sent
   * @throws IOException
   */
  public PrintWriter writeTextStream() throws IOException {
    return new PrintWriter(new OutputStreamWriter(
        writeStream(WebSocketMessage.OPCODE_TEXT), UTF8));
  }

  protected abstract void endWrite();

  /**
   * Write a buffer, in either blocking or non-blocking mode.
   * 
   * See {@link WritableByteChannel#write(ByteBuffer)} for more details.
   * 
   * @param opcode opcode to use for this frame
   * @param buf buffer to write
   * @param blocking true if the write should block.
   * @param fin true if final fragment of this message
   * @return the number of payload bytes written, or -1 on EOF
   * @throws IOException
   */
  protected abstract int writeFrame(int opcode, ByteBuffer buf,
      boolean blocking, boolean fin) throws IOException;

  private OutputStream writeStream(final int opcode) {
    final ByteBuffer buf = ByteBuffer.allocate(BUF_SIZE);
    buf.clear();
    return new OutputStream() {
      private int currentOpcode = opcode;

      @Override
      public void close() throws IOException {
        emptyBuffer(false);
        super.close();
        endWrite();
      }

      @Override
      public void flush() throws IOException {
        if (buf.position() != 0) {
          emptyBuffer(true);
        }
      }

      @Override
      public void write(byte[] bytes, int ofs, int len) throws IOException {
        while (len > 0) {
          int n = Math.min(len, buf.remaining());
          buf.put(bytes, ofs, n);
          if (n < len) {
            emptyBuffer(true);
          }
          ofs += n;
          len -= n;
        }
      }

      @Override
      public void write(int b) throws IOException {
        if (!buf.hasRemaining()) {
          emptyBuffer(true);
        }
        buf.put((byte) b);
      }

      private void emptyBuffer(boolean more) throws IOException {
        buf.flip();
        WritableWebSocketMessage.this
            .writeFrame(currentOpcode, buf, true, !more);
        // TODO: what if the write is incomplete?
        buf.clear();
        currentOpcode = OPCODE_CONTINUATION;
      }
    };
  }
}
