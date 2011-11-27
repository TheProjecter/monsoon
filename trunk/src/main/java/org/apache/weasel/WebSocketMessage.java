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

import java.nio.charset.Charset;

/**
 * Represents a single WebSocket message, which may not be complete.
 */
public abstract class WebSocketMessage {

  // WebSocket opcodes - ints are used instead of an enum for extensibility.

  /**
   * Indicates the message is not in progress.
   */
  public static final int OPCODE_NO_MESSAGE = -1;

  // TODO: these opcodes are in the wrong place, as they vary between protocol
  // versions.
  public static final int OPCODE_CONTINUATION = 0; 
  public static final int OPCODE_CLOSE = 1; 
  public static final int OPCODE_PING = 2; 
  public static final int OPCODE_PONG = 3; 
  public static final int OPCODE_TEXT = 4; 
  public static final int OPCODE_BINARY = 5; 
  public static final int OPCODE_MUX = 14;

  protected static final int BUF_SIZE = 32768;
  protected static final Charset UTF8 = Charset.forName("UTF-8");

  protected int opcode;

  protected WebSocketMessage() {
    this.opcode = OPCODE_NO_MESSAGE;
  }

  /**
   * @return the opcode for this message.
   */
  public final int getOpcode() {
    return opcode;
  }

  public final boolean isBinary() {
    return opcode == OPCODE_BINARY;
  }

  public final boolean isControl() {
    switch (opcode) {
      case OPCODE_CLOSE:
      case OPCODE_PING:
      case OPCODE_PONG:
        return true;
      default:
        return false;
    }
  }

  public final boolean isText() {
    return opcode == OPCODE_TEXT;
  }

  public final void beginMessage(int opcode) {
    this.opcode = opcode;
  }
}
