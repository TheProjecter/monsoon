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
package org.apache.websocket;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Test the methods implemented on ReadableWebSocketMessage.
 */
public class ReadableWebSocketMessageTest extends TestCase {
  
  // TODO: test NIO

  private static class MockMessage extends ReadableWebSocketMessage {

    private final byte[] dataToReturn;
    private int ofs;
    private boolean closed;

    MockMessage(int opcode, byte[] dataToReturn) {
      super(opcode);
      this.dataToReturn = dataToReturn;
      ofs = 0;
    }

    public boolean getClosed() {
      boolean save = closed;
      closed = false;
      return save;
    }

    @Override
    protected int read(ByteBuffer buf, boolean blocking) throws IOException {
      int n = dataToReturn.length - ofs;
      if (n == 0) {
        return -1;
      }
      int len = Math.min(n, buf.remaining());
      buf.put(dataToReturn, ofs, len);
      ofs += len;
      return len;
    }

    @Override
    protected void endRead() {
      closed = true;
    }
  }

  private static final Charset UTF8 = Charset.forName("UTF-8");

  public void testReadBinary() throws IOException {
    byte[] expected = new byte[] { (byte) 1, (byte) 2, (byte) 3, (byte) 4 };
    MockMessage msg = new MockMessage(WebSocketMessage.OPCODE_BINARY, expected);
    assertFalse(msg.getClosed());
    assertTrue(Arrays.equals(expected, msg.readBinary()));
    assertTrue(msg.getClosed());
  }

  public void testReadBinaryStream() throws IOException {
    byte[] expected = new byte[] { (byte) 1, (byte) 2, (byte) 3, (byte) 4 };
    MockMessage msg = new MockMessage(WebSocketMessage.OPCODE_BINARY, expected);
    InputStream str = msg.readBinaryStream();
    int n = expected.length;
    for (int i = 0; i < n; ++i) {
      assertFalse(msg.getClosed());
      int b = str.read();
      assertEquals(expected[i], b);
    }
    assertEquals(-1, str.read());
    str.close();
    assertTrue(msg.getClosed());
  }

  public void testReadText() throws IOException {
    String expected = "Hello";
    MockMessage msg = new MockMessage(WebSocketMessage.OPCODE_TEXT,
        expected.getBytes(UTF8));
    assertEquals(expected, msg.readText());
  }

  public void testReadTextStream() throws IOException {
    String expected = "Hello";
    MockMessage msg = new MockMessage(WebSocketMessage.OPCODE_TEXT,
        expected.getBytes(UTF8));
    Reader reader = msg.readTextStream();
    char[] buf = new char[20];
    int n = reader.read(buf);
    assertEquals(expected, String.valueOf(buf, 0, n));
  }
}
