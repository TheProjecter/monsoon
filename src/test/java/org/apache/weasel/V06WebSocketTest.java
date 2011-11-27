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

import junit.framework.TestCase;

import org.apache.weasel.ReadableWebSocketMessage;
import org.apache.weasel.V06WebSocket;
import org.apache.weasel.WebSocketMessage;
import org.apache.weasel.WritableWebSocketMessage;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;

/**
 * Test v06 WebSocket framing.
 */
public class V06WebSocketTest extends TestCase {

  private FakeChannel channel;
  private ByteBuffer readBuffer;
  private ByteBuffer writeBuffer;
  private V06WebSocket<FakeChannel> clientSocket;
  private V06WebSocket<FakeChannel> serverSocket;

  @Override
  protected void setUp() throws Exception {
    channel = new FakeChannel();
    readBuffer = channel.getReadBuffer();
    writeBuffer = channel.getWriteBuffer();
    clientSocket = new V06WebSocket<FakeChannel>(channel, true);
    serverSocket = new V06WebSocket<FakeChannel>(channel, false);
  }

  /**
   * Test method for
   * {@link org.apache.weasel.WebSocket#beginReceiveMessage()}.
   * 
   * @throws IOException
   */
  @Test
  public void testBeginReceiveMessageClient() throws IOException {
    readBuffer.clear();
    readBuffer.flip();
    ReadableWebSocketMessage msg = clientSocket.beginReceiveMessage();
    assertNull(msg);
    readBuffer.clear();
    // mask
    readBuffer.put((byte) 0x0);
    readBuffer.put((byte) 0x0);
    readBuffer.put((byte) 0x0);
    readBuffer.put((byte) 0x0);
    readBuffer.flip();
    // verify we don't return a message until we can get the opcode
    msg = clientSocket.beginReceiveMessage();
    assertNull(msg);
    readBuffer.clear();
    readBuffer.put((byte) (WebSocketMessage.OPCODE_TEXT | 0x80));
    readBuffer.flip();
    msg = clientSocket.beginReceiveMessage();
    assertNotNull(msg);
    assertTrue(msg.isText());
    ByteBuffer buf = ByteBuffer.allocate(1024);
    buf.clear();
    int n = msg.read(buf);
    assertEquals(0, n);
    readBuffer.clear();
    readBuffer.put((byte) 1);
    readBuffer.flip();
    n = msg.read(buf);
    assertEquals(0, n);
    readBuffer.clear();
    readBuffer.put((byte) 'A');
    readBuffer.put((byte) 0x55); // mask of next message
    readBuffer.put((byte) 0x55);
    readBuffer.put((byte) 0x55);
    readBuffer.put((byte) 0x55);
    readBuffer.put((byte) ((WebSocketMessage.OPCODE_TEXT | 0x80) ^ 0x55));
    readBuffer.flip();
    buf.clear();
    n = msg.read(buf);
    assertEquals(1, n);
    assertEquals('A', (char) buf.get(0));

    // read second message
    msg = clientSocket.beginReceiveMessage();
    assertNotNull(msg);
    assertTrue("Should be a text message", msg.isText());
    readBuffer.clear();
    readBuffer.put((byte) (2 ^ 0x55)); // length
    readBuffer.put((byte) ('A' ^ 0x55)); // payload
    readBuffer.flip();
    buf.clear();
    n = msg.read(buf);
    assertEquals(1, n);
    assertEquals('A', (char) buf.get(0));
    readBuffer.clear();
    readBuffer.put((byte) ('B' ^ 0x55)); // last byte of payload
    readBuffer.flip();
    buf.clear();
    n = msg.read(buf);
    assertEquals(1, n);
    assertEquals('B', (char) buf.get(0));
  }

  /**
   * Test method for
   * {@link org.apache.weasel.WebSocket#beginReceiveMessage()}.
   * 
   * @throws IOException
   */
  @Test
  public void testBeginReceiveMessageServer() throws IOException {
    readBuffer.clear();
    readBuffer.flip();
    ReadableWebSocketMessage msg = serverSocket.beginReceiveMessage();
    assertNull(msg);
    readBuffer.clear();
    // no mask
    readBuffer.flip();
    // verify we don't return a message until we can get the opcode
    msg = serverSocket.beginReceiveMessage();
    assertNull(msg);
    readBuffer.clear();
    readBuffer.put((byte) (WebSocketMessage.OPCODE_TEXT | 0x80));
    readBuffer.flip();
    msg = serverSocket.beginReceiveMessage();
    assertNotNull(msg);
    assertTrue(msg.isText());
    ByteBuffer buf = ByteBuffer.allocate(1024);
    buf.clear();
    int n = msg.read(buf);
    assertEquals(0, n);
    readBuffer.clear();
    readBuffer.put((byte) 1); // length
    readBuffer.flip();
    n = msg.read(buf);
    assertEquals(0, n);
    readBuffer.clear();
    readBuffer.put((byte) 'A'); // payload
    // no mask
    readBuffer.put((byte) (WebSocketMessage.OPCODE_TEXT | 0x80));
    readBuffer.flip();
    buf.clear();
    n = msg.read(buf);
    assertEquals(1, n);
    assertEquals('A', (char) buf.get(0));
    msg = serverSocket.beginReceiveMessage();
    assertNotNull(msg);
    assertTrue(msg.isText());
    readBuffer.clear();
    readBuffer.put((byte) 2); // length
    readBuffer.put((byte) 'A'); // payload
    readBuffer.flip();
    buf.clear();
    n = msg.read(buf);
    assertEquals(1, n);
    assertEquals('A', (char) buf.get(0));
    readBuffer.clear();
    readBuffer.put((byte) 'B'); // last byte of payload
    readBuffer.flip();
    buf.clear();
    n = msg.read(buf);
    assertEquals(1, n);
    assertEquals('B', (char) buf.get(0));
  }

  /**
   * Test method for {@link org.apache.weasel.WebSocket#beginSendMessage()}.
   * @throws IOException 
   */
  @Test
  public void testBeginSendMessageServer() throws IOException {
    assertEquals(0, writeBuffer.position());
    WritableWebSocketMessage msg = serverSocket.beginSendMessage();
    assertEquals(0, writeBuffer.position());
    byte[] bytes = new byte[] { 4, 2, 1 };
    ByteBuffer buf = ByteBuffer.wrap(bytes);
    final int BOGUS_OPCODE = 9;
    int n = msg.writeFrame(BOGUS_OPCODE, buf, true, false);
    assertEquals(bytes.length, n);
    writeBuffer.flip();
    byte b = writeBuffer.get();
    assertEquals(BOGUS_OPCODE, b);
    b = writeBuffer.get();
    assertEquals(3, b);
    for (byte expected : bytes) {
      b = writeBuffer.get();
      assertEquals(expected, b);
    }
    assertFalse(writeBuffer.hasRemaining());
  }

  /**
   * Test method for {@link org.apache.weasel.WebSocket#sendBinary(byte[])}.
   * @throws IOException 
   */
  @Test
  public void testSendBinaryClient() throws IOException {
    byte[] bytes = new byte[] { 5, 3, 1 };
    assertEquals(0, writeBuffer.position());
    clientSocket.sendBinary(bytes);
    writeBuffer.flip();
    byte[] mask = new byte[4];
    writeBuffer.get(mask);
    byte b = writeBuffer.get();
    assertEquals((byte) (WebSocketMessage.OPCODE_BINARY | 0x80) ^ mask[0], b);
    b = writeBuffer.get();
    assertEquals((byte) (bytes.length ^ mask[1]), b);
    int maskPosition = 2;
    for (byte expected : bytes) {
      b = writeBuffer.get();
      assertEquals((byte) (expected ^ mask[maskPosition]), b);
      maskPosition = (maskPosition + 1) % 4;
    }
    assertFalse(writeBuffer.hasRemaining());
  }

  /**
   * Test method for {@link org.apache.weasel.WebSocket#sendBinary(byte[])}.
   * @throws IOException 
   */
  @Test
  public void testSendBinaryServer() throws IOException {
    byte[] bytes = new byte[] { 5, 3, 1 };
    assertEquals(0, writeBuffer.position());
    serverSocket.sendBinary(bytes);
    writeBuffer.flip();
    byte b = writeBuffer.get();
    assertEquals((byte) (WebSocketMessage.OPCODE_BINARY | 0x80), b);
    b = writeBuffer.get();
    assertEquals((byte) bytes.length, b);
    for (byte expected : bytes) {
      b = writeBuffer.get();
      assertEquals(expected, b);
    }
    assertFalse(writeBuffer.hasRemaining());
  }

  /**
   * Test method for {@link org.apache.weasel.WebSocket#sendBinaryStream()}.
   * @throws IOException 
   */
  @Test
  public void testSendBinaryStreamServer() throws IOException {
    byte[] bytes = new byte[] { 5, 3, 1 };
    assertEquals(0, writeBuffer.position());
    OutputStream stream = serverSocket.sendBinaryStream();
    assertEquals(0, writeBuffer.position());
    stream.write(bytes);
    assertEquals(0, writeBuffer.position());
    stream.flush();
    writeBuffer.flip();
    byte b = writeBuffer.get();
    assertEquals((byte) WebSocketMessage.OPCODE_BINARY, b);
    b = writeBuffer.get();
    assertEquals((byte) bytes.length, b);
    for (byte expected : bytes) {
      b = writeBuffer.get();
      assertEquals(expected, b);
    }
    assertFalse(writeBuffer.hasRemaining());
    writeBuffer.clear();
    stream.close();
    writeBuffer.flip();
    b = writeBuffer.get();
    assertEquals((byte) (WebSocketMessage.OPCODE_CONTINUATION | 0x80), b);
    b = writeBuffer.get();
    assertEquals((byte) 0, b);
    assertFalse(writeBuffer.hasRemaining());
  }

  /**
   * Test method for
   * {@link org.apache.weasel.WebSocket#sendText(java.lang.String)}.
   * @throws IOException 
   */
  @Test
  public void testSendTextClient() throws IOException {
    String string = "Hello";
    byte[] bytes = string.getBytes(WebSocketMessage.UTF8);
    assertEquals(0, writeBuffer.position());
    clientSocket.sendText(string);
    writeBuffer.flip();
    byte[] mask = new byte[4];
    writeBuffer.get(mask);
    byte b = writeBuffer.get();
    assertEquals((byte) (WebSocketMessage.OPCODE_TEXT | 0x80) ^ mask[0], b);
    b = writeBuffer.get();
    assertEquals((byte) (bytes.length ^ mask[1]), b);
    int maskPosition = 2;
    for (byte expected : bytes) {
      b = writeBuffer.get();
      assertEquals((byte) (expected ^ mask[maskPosition]), b);
      maskPosition = (maskPosition + 1) % 4;
    }
    assertFalse(writeBuffer.hasRemaining());
  }

  /**
   * Test method for
   * {@link org.apache.weasel.WebSocket#sendText(java.lang.String)}.
   * @throws IOException 
   */
  @Test
  public void testSendTextServer() throws IOException {
    String string = "Hello";
    byte[] bytes = string.getBytes(WebSocketMessage.UTF8);
    assertEquals(0, writeBuffer.position());
    serverSocket.sendText(string);
    writeBuffer.flip();
    byte b = writeBuffer.get();
    assertEquals((byte) (WebSocketMessage.OPCODE_TEXT | 0x80), b);
    b = writeBuffer.get();
    assertEquals((byte) bytes.length, b);
    int maskPosition = 2;
    for (byte expected : bytes) {
      b = writeBuffer.get();
      assertEquals(expected, b);
    }
    assertFalse(writeBuffer.hasRemaining());
  }

  /**
   * Test method for {@link org.apache.weasel.WebSocket#sendTextStream()}.
   * @throws IOException 
   */
  @Test
  public void testSendTextStreamServer() throws IOException {
    assertEquals(0, writeBuffer.position());
    PrintWriter pw = serverSocket.sendTextStream();
    assertEquals(0, writeBuffer.position());
    String string = "Hello";
    byte[] bytes = string.getBytes(WebSocketMessage.UTF8);
    pw.append(string);
    assertEquals(0, writeBuffer.position());
    pw.flush();
    writeBuffer.flip();
    byte b = writeBuffer.get();
    assertEquals((byte) WebSocketMessage.OPCODE_TEXT, b);
    b = writeBuffer.get();
    assertEquals((byte) bytes.length, b);
    for (byte expected : bytes) {
      b = writeBuffer.get();
      assertEquals(expected, b);
    }
    assertFalse(writeBuffer.hasRemaining());
    writeBuffer.clear();
    pw.close();
    writeBuffer.flip();
    b = writeBuffer.get();
    assertEquals((byte) (WebSocketMessage.OPCODE_CONTINUATION | 0x80), b);
    b = writeBuffer.get();
    assertEquals((byte) 0, b);
    assertFalse(writeBuffer.hasRemaining());
  }
}
