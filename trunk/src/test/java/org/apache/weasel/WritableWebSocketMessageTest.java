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
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.apache.weasel.WebSocketMessage;
import org.apache.weasel.WritableWebSocketMessage;

import junit.framework.TestCase;

/**
 * Test the methods implemented on {@link WritableWebSocketMessage}.
 */
public class WritableWebSocketMessageTest extends TestCase {

  private static class MockMessage extends WritableWebSocketMessage {

    private byte[] lastMessage = null;
    private int lastOpcode = -1;
    private boolean closed = false;

    MockMessage() {
    }

    public boolean getClosed() {
      boolean save = closed;
      closed = false;
      return save;
    }

    public byte[] getLastMessage() {
      byte[] msg = lastMessage;
      lastMessage = null;
      return msg;
    }

    int getLastOpcode() {
      int opcode = lastOpcode;
      lastOpcode = -1;
      return opcode;
    }

    @Override
    protected int writeFrame(int opcode, ByteBuffer buf, boolean blocking,
        boolean more) throws IOException {
      lastOpcode = opcode;
      lastMessage = new byte[buf.remaining()];
      buf.get(lastMessage);
      return lastMessage.length;
    }

    @Override
    protected void endWrite() {
      closed = true;
    }
  }

  private static final Charset UTF8 = Charset.forName("UTF-8");

  public void testWriteBinary() throws IOException {
    byte[] expected = new byte[] { (byte) 1, (byte) 2, (byte) 3, (byte) 4 };
    MockMessage msg = new MockMessage();
    assertFalse(msg.getClosed());
    msg.writeBinary(expected);
    assertTrue(msg.getClosed());
    assertEquals(WebSocketMessage.OPCODE_BINARY, msg.getLastOpcode());
    assertTrue(Arrays.equals(expected, msg.getLastMessage()));
  }

  public void testWriteBinaryStream() throws IOException {
    byte[] expected = new byte[] { (byte) 1, (byte) 2, (byte) 3, (byte) 4 };
    MockMessage msg = new MockMessage();
    OutputStream stream = msg.writeBinaryStream();
    stream.write(expected);
    // make sure the message hasn't finished before we close
    assertFalse(msg.getClosed());
    assertEquals(-1, msg.getLastOpcode());
    stream.close();
    assertTrue(msg.getClosed());
    assertEquals(WebSocketMessage.OPCODE_BINARY, msg.getLastOpcode());
    assertTrue(Arrays.equals(expected, msg.getLastMessage()));
  }

  public void testWriteBinaryStreamByByte() throws IOException {
    byte[] expected = new byte[] { (byte) 1, (byte) 2, (byte) 3, (byte) 4 };
    MockMessage msg = new MockMessage();
    OutputStream stream = msg.writeBinaryStream();
    for (int i = 0; i < expected.length; ++i) {
      stream.write(expected[i]);
      // make sure the message hasn't finished before we close
      assertFalse(msg.getClosed());
      assertEquals(-1, msg.getLastOpcode());
    }
    stream.close();
    assertTrue(msg.getClosed());
    assertEquals(WebSocketMessage.OPCODE_BINARY, msg.getLastOpcode());
    assertTrue(Arrays.equals(expected, msg.getLastMessage()));
  }

  public void testWriteText() throws IOException {
    String expected = "Hello";
    MockMessage msg = new MockMessage();
    msg.writeText(expected);
    assertTrue(msg.getClosed());
    assertEquals(WebSocketMessage.OPCODE_TEXT, msg.getLastOpcode());
    assertTrue(Arrays.equals(expected.getBytes(UTF8), msg.getLastMessage()));
  }

  public void testWriteTextStream() throws IOException {
    String expected = "Hello";
    MockMessage msg = new MockMessage();
    PrintWriter pw = msg.writeTextStream();
    pw.print(expected);
    // make sure the message hasn't finished before we close
    assertFalse(msg.getClosed());
    assertEquals(-1, msg.getLastOpcode());
    pw.close();
    assertTrue(msg.getClosed());
    assertEquals(WebSocketMessage.OPCODE_TEXT, msg.getLastOpcode());
    assertTrue(Arrays.equals(expected.getBytes(UTF8), msg.getLastMessage()));
  }
}
