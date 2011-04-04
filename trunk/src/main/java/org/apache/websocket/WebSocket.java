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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.io.Reader;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;

/**
 * A connected WebSocket.
 */
public abstract class WebSocket<T extends SelectableChannel & GatheringByteChannel & ScatteringByteChannel>
    extends AbstractSelectableChannel implements Closeable {

  public enum State {
    OPEN, CLOSING, CLOSED;
  }

  /**
   * Lock held to serialize operations on a single WebSocket instance.
   */
  protected final Object lock = new Object[0];

  /*
   * TODO: is there some way we can register selectors for new messages? We
   * would have to share SelectionKeys across the messages.
   */

  protected final T channel;

  private boolean cleanShutdown = false;

  private State state = State.OPEN;

  protected final ReadableWebSocketMessage readMessage;

  protected final WritableWebSocketMessage writeMessage;

  private boolean readInProgress = false;

  private boolean writeInProgress = false;

  /**
   * Create a WebSocket on a channel.
   * 
   * @param channel
   */
  protected WebSocket(T channel) {
    super(channel.provider());
    this.channel = channel;
    readMessage = createReadableMessage();
    writeMessage = createWritableMessage();
  }

  /**
   * Begin reading a frame from the WebSocket
   * 
   * @param blocking
   * @return true if the first byte of a message to be returned to the user was
   *         read
   * @throws IOException
   */
  protected abstract boolean beginRead(boolean blocking) throws IOException;

  /**
   * Read a message from this WebSocket. Blocks until at least the first byte
   * has been read. The returned message must be completely read before another
   * call to beginReadMessage.
   * 
   * @return a ReadableWebSocketMessage to access the incoming message, or null
   *         if there is no message available in non-blocking mode
   * @throws IOException if the previous message was not completely read
   */
  public final ReadableWebSocketMessage beginReceiveMessage()
      throws IOException {
    return beginReceiveMessage(isBlocking());
  }

  /**
   * Read a message from this WebSocket. Blocks until at least the first byte
   * has been read. The returned message must be completely read before another
   * call to beginReadMessage.
   * 
   * @param blocking true if this receive should be performed in a blocking
   *          manner
   * @return a ReadableWebSocketMessage to access the incoming message, or null
   *         if there is no message available in non-blocking mode
   * @throws IOException if the previous message was not completely read
   */
  protected ReadableWebSocketMessage beginReceiveMessage(boolean blocking)
      throws IOException {
    synchronized (lock) {
      if (readInProgress) {
        throw new WebSocketException("Only one read may be in progress at "
            + "a time");
      }
      readInProgress = true;
      if (beginRead(blocking)) {
        return readMessage;
      }
      readInProgress = false;
      return null;
    }
  }

  /**
   * Start sending a message on this WebSocket. Only one message may be in
   * progress at a given time.
   * 
   * @return a WritableWebSocketMessage to use to write the message, or null if
   *         the underlying channel is not writable in non-blocking mode
   * @throws IOException if a second message is started before the first one is
   *           finished, or if an IO error occurs.
   */
  public final WritableWebSocketMessage beginSendMessage() throws IOException {
    return beginSendMessage(isBlocking());
  }

  /**
   * Start sending a message on this WebSocket. Only one message may be in
   * progress at a given time.
   * 
   * @param blocking true if this receive should be performed in a blocking
   *          manner
   * @return a WritableWebSocketMessage to use to write the message, or null if
   *         the underlying channel is not writable in non-blocking mode
   * @throws IOException if a second message is started before the first one is
   *           finished, or if an IO error occurs.
   */
  protected WritableWebSocketMessage beginSendMessage(boolean blocking)
      throws IOException {
    synchronized (lock) {
      if (writeInProgress) {
        throw new WebSocketException("Only one write may be in progress at "
            + "a time");
      }
      writeInProgress = true;
      if (beginWrite(blocking)) {
        return writeMessage;
      }
      return null;
    }
  }

  /**
   * Begin sending a message.
   * 
   * @param blocking
   * @return true if the message was able to be started
   */
  protected abstract boolean beginWrite(boolean blocking);

  /**
   * Create the singleton ReadableWebSocketMessage for this connection.
   * 
   * @return a {@link ReadableWebSocketMessage} instance
   */
  protected abstract ReadableWebSocketMessage createReadableMessage();

  /**
   * Create the singleton WritableWebSocketMessage for this connection.
   * 
   * @return a {@link WritableWebSocketMessage} instance
   */
  protected abstract WritableWebSocketMessage createWritableMessage();

  /**
   * Finish reading a message.
   */
  void endRead() {
    synchronized (lock) {
      readInProgress = false;
    }
  }

  /**
   * Finish sending a message.
   */
  void endWrite() {
    synchronized (lock) {
      writeInProgress = false;
    }
  }

  /**
   * @return the current state of this WebSocket.
   */
  public final State getState() {
    synchronized (lock) {
      return state;
    }
  }

  @Override
  protected void implCloseSelectableChannel() throws IOException {
    synchronized (lock) {
      if (state == State.CLOSING) {
        state = State.CLOSED;
        channel.close();
        return;
      }
      state = State.CLOSING;
      sendClose();
      // TODO: how do we know when to close the underlying channel if we never
      // get a response?
    }
  }

  @Override
  protected void implConfigureBlocking(boolean block) throws IOException {
    // TODO: what to do with multiplexed channels, where we might have some
    // logical connections blocking and others not? Document the problem away,
    // force all to be non-blocking if any are, or what?
    channel.configureBlocking(block);
  }

  /**
   * Read a binary message, blocking until all fragments have been received.
   * 
   * @return bytes of the binary message
   * @throws IOException
   */
  public byte[] receiveBinary() throws IOException {
    ReadableWebSocketMessage msg = beginReceiveMessage();
    // TODO: handle synchronization
    byte[] binaryMessage = msg.readBinary();
    readInProgress = false;
    return binaryMessage;
  }

  /**
   * Read a binary message into an InputStream. Message fragments will be
   * available in the InputStream as soon as they are received.
   * 
   * @return an InputStream instance for the binary message to be received
   * @throws IOException
   */
  public InputStream receiveBinaryStream() throws IOException {
    ReadableWebSocketMessage msg = beginReceiveMessage();
    // TODO: handle readInProgress flag
    return msg.readBinaryStream();
  }

  /**
   * Called by subclasses when an explicit close frame was received or if the
   * underlying channel is closed.
   * 
   * @param explicitClose true if an explicit close frame was seen
   * @throws IOException
   */
  protected void receivedClose(boolean explicitClose) throws IOException {
    synchronized (lock) {
      cleanShutdown = explicitClose;
      if (state == State.OPEN && explicitClose) {
        sendClose();
      }
      channel.close();
    }
  }

  /**
   * Read a text message, blocking until all fragments have been received.
   * 
   * @return a String containing the text message
   * @throws IOException
   */
  public String receiveText() throws IOException {
    ReadableWebSocketMessage msg = beginReceiveMessage();
    // TODO: handle synchronization
    String textMessage = msg.readText();
    readInProgress = false;
    return textMessage;
  }

  /**
   * Read a text message into a Reader. Message fragments will be available in
   * the Reader as soon as they are received.
   * 
   * @return a Reader instance for the text message to be received
   * @throws IOException
   */
  public Reader receiveTextStream() throws IOException {
    ReadableWebSocketMessage msg = beginReceiveMessage();
    // TODO: handle readInProgress flag
    return msg.readTextStream();
  }

  /**
   * Write a binary message, blocking until it can be completed.
   * 
   * @param bytes
   * @throws IOException
   */
  public void sendBinary(byte[] bytes) throws IOException {
    WritableWebSocketMessage msg = beginSendMessage(true);
    // TODO: handle synchronization
    msg.writeBinary(bytes);
    writeInProgress = false;
  }

  /**
   * Send a binary message written on an OutputStream. The message is sent when
   * the stream is closed, though fragments may be sent earlier.
   * 
   * @return an OutputStream instance for the binary message being sent
   * @throws IOException
   */
  public OutputStream sendBinaryStream() throws IOException {
    WritableWebSocketMessage msg = beginSendMessage(true);
    // TODO: handle writeInProgress flag
    return msg.writeBinaryStream();
  }

  /**
   * Send a close message on the WebSocket.
   */
  protected abstract void sendClose();

  /**
   * Write a text message, blocking until it can be completed.
   * 
   * @param text
   * @throws IOException
   */
  public void sendText(String text) throws IOException {
    WritableWebSocketMessage msg = beginSendMessage(true);
    // TODO: handle synchronization
    msg.writeText(text);
    writeInProgress = false;
  }

  /**
   * Send a text message written on a PrintWriter. The message is sent when the
   * writer is closed, though fragments may be sent earlier.
   * 
   * @return a PrintWriter instance for the text message being sent
   * @throws IOException
   */
  public PrintWriter sendTextStream() throws IOException {
    WritableWebSocketMessage msg = beginSendMessage(true);
    // TODO: handle writeInProgress flag
    return msg.writeTextStream();
  }

  @Override
  public final int validOps() {
    return SelectionKey.OP_READ | SelectionKey.OP_WRITE;
  }

  /**
   * @return true if the WebSocket connection has been shutdown cleanly.
   */
  public boolean wasCleanShutdown() {
    return cleanShutdown;
  }
}
