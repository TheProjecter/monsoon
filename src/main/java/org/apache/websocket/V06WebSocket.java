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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.util.Random;

/**
 * Implementation of WebSocket framing for draft -03 and later.
 */
public class V06WebSocket<T extends SelectableChannel & GatheringByteChannel & ScatteringByteChannel>
    extends WebSocket<T> {

  private class InboundMessage extends ReadableWebSocketMessage {

    private InboundState state = InboundState.BEFORE_MESSAGE;

    private long length;

    private boolean fin;

    private byte[] mask;

    private int maskPosition;

    /**
     * Number of bytes remaining in the current state.
     * 
     * Special meanings:
     * <ul>
     * <li>state == IN_HEADER_LEN -1 means we haven't read the first length byte
     */
    private long bytesRemaining;

    private boolean firstFrame;

    /**
     * Create a new inbound message.
     */
    protected InboundMessage() {
      super(-1);
      mask = new byte[4];
    }

    @Override
    protected void endRead() {
      V06WebSocket.this.endRead();
    }

    @Override
    protected int read(ByteBuffer buf, boolean blocking) throws IOException {
      int totRead = 0;
      do {
        int n = readOneFrame(buf, blocking);
        totRead += n;
      } while (state == InboundState.BETWEEN_FRAMES);
      if (state == InboundState.BEFORE_MESSAGE) {
        endRead();
      }
      return totRead;
    }

    /**
     * Read a single byte if available.
     * 
     * @return read byte (0-255) or -1 if no byte available
     * @throws IOException
     */
    private int getByte() throws IOException {
      if (!readBuffer.hasRemaining()) {
        readBuffer.clear();
        int n = channel.read(readBuffer);
        readBuffer.flip();
        if (n == 0) {
          return -1;
        }
      }
      return readBuffer.get() & 255;
    }

    /**
     * Read bytes into a ByteBuffer.
     * 
     * @param buf
     * @return number of bytes read
     * @throws IOException
     */
    private int getBytes(ByteBuffer buf) throws IOException {
      int totRead = 0;
      if (readBuffer.hasRemaining()) {
        int n = Math.min(buf.remaining(), readBuffer.remaining());
        int savedLimit = limitBuffer(readBuffer, n);
        buf.put(readBuffer);
        restoreBuffer(readBuffer, savedLimit);
        totRead += n;
      }
      if (buf.hasRemaining()) {
        int n = channel.read(buf);
        totRead += n;
      }
      return totRead;
    }

    /**
     * Make sure the buffer doesn't have too much space available.
     * 
     * @param buf
     * @param maxRemaining
     * @return save state to be passed to
     *         {@link #restoreBuffer(ByteBuffer, int)}
     */
    private int limitBuffer(ByteBuffer buf, int maxRemaining) {
      if (buf.remaining() > maxRemaining) {
        int saveLimit = buf.limit();
        buf.limit(buf.position() + maxRemaining);
        return saveLimit;
      }
      return -1;
    }

    private int readOneFrame(ByteBuffer buf, boolean blocking)
        throws IOException {
      // TODO: handle blocking
      if (!readToNextPayload()) {
        return 0;
      }
      // make sure we don't read past the payload
      int clippedBytes = (int) Math.min(bytesRemaining, 1 << 30);
      int saved = limitBuffer(buf, clippedBytes);
      int n = getBytes(buf);
      restoreBuffer(buf, saved);
      buf.flip();
      bytesRemaining -= n;
      if (isClient) {
        for (int i = buf.position(); i < buf.limit(); i++) {
          buf.put(i, (byte) (buf.get(i) ^ mask[maskPosition]));
          maskPosition = (maskPosition + 1) % 4;
        }
      }
      if (bytesRemaining == 0) {
        state = fin ? InboundState.BEFORE_MESSAGE : InboundState.BETWEEN_FRAMES;
      }
      return n;
    }

    protected boolean hasOpcode() {
      switch (state) {
        case IN_HEADER_LENGTH:
        case IN_HEADER_EXTENDED_LENGTH:
        case IN_PAYLOAD:
          return true;
        default:
          return false;
      }
    }

    /**
     * Read until the next payload byte for this message, processing any
     * headers/etc.
     * 
     * @return true if everything has been read up to the next payload byte
     * @throws IOException
     */
    private boolean readToNextPayload() throws IOException {
      int b;
      while (state != InboundState.IN_PAYLOAD) {
        switch (state) {
          case BEFORE_MESSAGE:
          case BETWEEN_FRAMES:
            firstFrame = (state == InboundState.BEFORE_MESSAGE);
            if (isClient) {
              bytesRemaining = 4;
              state = InboundState.IN_MASK;
            } else {
              state = InboundState.IN_OPCODE;
            }
            break;
          case IN_MASK:
            while (bytesRemaining > 0) {
              b = getByte();
              if (b < 0) {
                return false;
              }
              mask[(int) (mask.length - bytesRemaining)] = (byte) b;
              bytesRemaining--;
            }
            state = InboundState.IN_OPCODE;
            maskPosition = 0;
            break;
          case IN_OPCODE:
            b = getByte();
            if (b < 0) {
              return false;
            }
            if (isClient) {
              b = b ^ mask[maskPosition];
              maskPosition = (maskPosition + 1) % 4;
            }
            fin = (b & FIN) != 0;
            int thisOpcode = (b & OPCODE_MASK);
            if (firstFrame) {
              opcode = thisOpcode;
            } else if (thisOpcode != OPCODE_CONTINUATION) {
              throw new WebSocketException("Expected continuation opcode, got "
                  + thisOpcode);
            }
            state = InboundState.IN_HEADER_LENGTH;
            break;
          case IN_HEADER_LENGTH:
            b = getByte();
            if (b < 0) {
              return false;
            }
            if (isClient) {
              b = b ^ mask[maskPosition];
              maskPosition = (maskPosition + 1) % 4;
            }
            int val = b & 127;
            switch (val) {
              case LENGTH_16:
                bytesRemaining = 2;
                length = 0;
                state = InboundState.IN_HEADER_EXTENDED_LENGTH;
                break;
              case LENGTH_63:
                bytesRemaining = 8;
                length = 0;
                state = InboundState.IN_HEADER_EXTENDED_LENGTH;
                break;
              default:
                state = InboundState.IN_PAYLOAD;
                bytesRemaining = val;
                break;
            }
            break;
          case IN_HEADER_EXTENDED_LENGTH:
            while (bytesRemaining > 0) {
              b = getByte();
              if (b < 0) {
                return false;
              }
              if (isClient) {
                b = b ^ mask[maskPosition];
                maskPosition = (maskPosition + 1) % 4;
              }
              bytesRemaining--;
              length = (length << 8) | (b & 255);
            }
            bytesRemaining = length;
            state = InboundState.IN_PAYLOAD;
            break;
        }
      }
      return true;
    }

    /**
     * Restore the limit of a buffer previously limited with
     * {@link #limitBuffer(ByteBuffer, int)}.
     * 
     * @param buf
     * @param savedLimit return value from previous call to
     *          {@link #limitBuffer(ByteBuffer, int)}
     */
    private void restoreBuffer(ByteBuffer buf, int savedLimit) {
      if (savedLimit >= 0) {
        buf.limit(savedLimit);
      }
    }
  }

  private enum InboundState {
    /**
     * Before the first frame of a message.
     */
    BEFORE_MESSAGE,

    /**
     * Between frames of one message.
     */
    BETWEEN_FRAMES,

    /**
     * Reading the mask.
     */
    IN_MASK,

    /**
     * Reading the opcode.
     */
    IN_OPCODE,

    /**
     * In the header length.
     */
    IN_HEADER_LENGTH,

    /**
     * In the extended length field.
     */
    IN_HEADER_EXTENDED_LENGTH,

    /**
     * Reading the payload.
     */
    IN_PAYLOAD,
  }

  private class OutboundMessage extends WritableWebSocketMessage {

    private ByteBuffer headerBuf = ByteBuffer.allocate(MAX_HEADER_SIZE);
    private final ByteBuffer[] buffers = new ByteBuffer[] { headerBuf, null };

    /**
     * TODO: document me.
     */
    public OutboundMessage() {
      // TODO Auto-generated constructor stub
    }

    @Override
    protected int writeFrame(int opcode, ByteBuffer buf, boolean blocking,
        boolean fin) throws IOException {
      // TODO: handle the non-blocking case
      // TODO: position buf past MAX_HEADER_SIZE and build the header before
      // the payload to avoid gathering writes to the channel.
      int n = buf.remaining();
      buildHeader(opcode, n, fin);
      if (isClient) {
        byte[] mask = generateMask();
        int maskPosition = 0;
        // copy to avoid trashing the caller's buffer
        ByteBuffer copy = ByteBuffer.allocate(buf.remaining());
        copy.put(buf);
        copy.flip();
        buffers[1] = copy;
        for (int k = 0; k < 2; k++) {
          for (int i = buffers[k].position(); i < buffers[k].limit(); i++) {
            buffers[k].put(i, (byte) (buffers[k].get(i) ^ mask[maskPosition]));
            maskPosition = (maskPosition + 1) % 4;
          }
        }
        channel.write(ByteBuffer.wrap(mask));
      } else {
        buffers[1] = buf;
      }
      int totbytes = -buffers[0].remaining(); // subtract header length
      totbytes += channel.write(buffers);
      return totbytes;
    }

    /**
     * TODO: document me
     * 
     * @param opcode
     * @param length
     * @param fin
     */
    private void buildHeader(int opcode, long length, boolean fin) {
      headerBuf.clear();
      byte firstByte = (byte) ((fin ? 0x80 : 0x00) | (opcode & 15));
      headerBuf.put(firstByte);
      if (length < 126) {
        headerBuf.put((byte) (length & 127));
      } else if (length < 65536) {
        headerBuf.put(LENGTH_16);
        headerBuf.putShort((short) length);
      } else {
        headerBuf.put(LENGTH_63);
        headerBuf.putLong(length);
      }
      headerBuf.flip();
    }

    @Override
    protected void endWrite() {
      V06WebSocket.this.endWrite();
    }
  }

  protected static final byte LENGTH_16 = (byte) 126;
  protected static final byte LENGTH_63 = (byte) 127;

  protected static final int FIN = 0x80;

  protected static final int OPCODE_MASK = 0x0f;

  protected static final int LENGTH_MASK = 0x7f;

  protected static final int MAX_HEADER_SIZE = 10;

  private static final int BUF_SIZE = 32768;

  private ByteBuffer readBuffer = ByteBuffer.allocate(BUF_SIZE);

  private boolean isClient;

  private Random randomGenerator = new Random(System.currentTimeMillis());

  /**
   * Only instantiable by a WebSocketHandshake
   * 
   * @param channel
   * @param isClient specifies if this is the client or server side of the
   *          websocket in order to apply masking/unmasking correctly
   */
  V06WebSocket(T channel, boolean isClient) {
    super(channel);
    this.isClient = isClient;
    readBuffer.clear();
    readBuffer.flip();
  }

  @Override
  protected boolean beginRead(boolean blocking) throws IOException {
    @SuppressWarnings("unchecked")
    InboundMessage inboundMessage = (InboundMessage) readMessage;
    inboundMessage.readToNextPayload();
    return inboundMessage.hasOpcode();
  }

  @Override
  protected boolean beginWrite(boolean blocking) {
    if (!channel.isOpen())
      return false;
    return true;
  }

  @Override
  protected ReadableWebSocketMessage createReadableMessage() {
    return new InboundMessage();
  }

  @Override
  protected WritableWebSocketMessage createWritableMessage() {
    return new OutboundMessage();
  }

  @Override
  protected void sendClose() {
    // TODO Auto-generated method stub

  }

  private byte[] generateMask() {
    byte[] mask = new byte[4];
    randomGenerator.nextBytes(mask);
    return mask;
  }
}
