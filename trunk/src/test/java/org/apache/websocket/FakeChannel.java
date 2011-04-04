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
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.SelectorProvider;

/**
 * A fake channel used for testing, which allows the caller to supply the
 * data to be returned from reads and pick up written data.
 */
public class FakeChannel extends AbstractSelectableChannel implements
    GatheringByteChannel, ScatteringByteChannel {

  private static final int BUF_SIZE = 32768;

  private ByteBuffer readBuffer = ByteBuffer.allocate(BUF_SIZE);

  private ByteBuffer writeBuffer = ByteBuffer.allocate(BUF_SIZE);

  /**
   * Create a FakeChannel.
   */
  public FakeChannel() {
    super(SelectorProvider.provider());
  }

  /**
   * @return the readBuffer.
   */
  public ByteBuffer getReadBuffer() {
    return readBuffer;
  }
  /**
   * @return the writeBuffer.
   */
  public ByteBuffer getWriteBuffer() {
    return writeBuffer;
  }

  public int read(ByteBuffer dst) throws IOException {
    return (int) read(new ByteBuffer[] { dst }, 0, 1);
  }

  public long read(ByteBuffer[] dsts) throws IOException {
    return write(dsts, 0, dsts.length);
  }

  public long read(ByteBuffer[] dsts, int offset, int length)
      throws IOException {
    if (offset + length > dsts.length) {
      throw new ArrayIndexOutOfBoundsException("offset=" + offset + ", length="
          + length + " out of bounds, dsts.length=" + dsts.length);
    }
    long totRead = 0;
    while (length > 0 && readBuffer.hasRemaining()) {
      while (length > 0 && !dsts[offset].hasRemaining()) {
        ++offset;
        --length;
      }
      if (length <= 0) {
        break;
      }
      byte b = readBuffer.get();
      dsts[offset].put(b);
      ++totRead;
    }
    return totRead;
  }

  @Override
  public int validOps() {
    return SelectionKey.OP_READ | SelectionKey.OP_WRITE;
  }

  public int write(ByteBuffer src) throws IOException {
    return (int) write(new ByteBuffer[] { src }, 0, 1);
  }

  public long write(ByteBuffer[] srcs) throws IOException {
    return write(srcs, 0, srcs.length);
  }

  public long write(ByteBuffer[] srcs, int offset, int length)
      throws IOException {
    if (offset + length > srcs.length) {
      throw new ArrayIndexOutOfBoundsException("offset=" + offset + ", length="
          + length + " out of bounds, dsts.length=" + srcs.length);
    }
    long totWritten = 0;
    while (length > 0 && writeBuffer.hasRemaining()) {
      while (length > 0 && !srcs[offset].hasRemaining()) {
        ++offset;
        --length;
      }
      if (length <= 0) {
        break;
      }
      byte b = srcs[offset].get();
      writeBuffer.put(b);
      ++totWritten;
    }
    return totWritten;
  }

  @Override
  protected void implCloseSelectableChannel() throws IOException {
    // TODO Auto-generated method stub
  }

  @Override
  protected void implConfigureBlocking(boolean block) throws IOException {
    // TODO Auto-generated method stub
  }
}
