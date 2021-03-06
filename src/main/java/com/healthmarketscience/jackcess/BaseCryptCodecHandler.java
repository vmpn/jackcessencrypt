/*
Copyright (c) 2010 Vladimir Berezniker

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
USA
*/

package com.healthmarketscience.jackcess;


import java.io.IOException;
import java.nio.ByteBuffer;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.engines.RC4Engine;
import org.bouncycastle.crypto.params.KeyParameter;


/**
 * Base CodecHandler support for RC4 encryption based CodecHandlers.
 *
 * @author Vladimir Berezniker
 */
public abstract class BaseCryptCodecHandler implements CodecHandler
{
  public static final boolean CIPHER_DECRYPT_MODE = false;
  public static final boolean CIPHER_ENCRYPT_MODE = true;

  private final PageChannel _channel;
  private RC4Engine _engine;
  private TempBufferHolder _encodeBuf;

  protected BaseCryptCodecHandler(PageChannel channel) {
    _channel = channel;
  }

  protected final RC4Engine getEngine()
  {
    if(_engine == null) {
      _engine = new RC4Engine();
    }
    return _engine;
  }

  protected ByteBuffer getTempEncodeBuffer() {
    if(_encodeBuf == null) {
      _encodeBuf = TempBufferHolder.newHolder(TempBufferHolder.Type.SOFT, true);
    }
    return _encodeBuf.getPageBuffer(_channel);
  }

  /**
   * Decodes the page in the given buffer (in place) using RC4 decryption with
   * the given params.
   *
   * @param buffer encoded page buffer
   * @param params RC4 decryption parameters
   */
  protected void decodePage(ByteBuffer buffer, KeyParameter params) {
    RC4Engine engine = getEngine();

    engine.init(CIPHER_DECRYPT_MODE, params);

    byte[] array = buffer.array();
    engine.processBytes(array, 0, array.length, array, 0);
  }

  public ByteBuffer encodePage(ByteBuffer buffer, int pageNumber, 
                               int pageOffset) {
    throw new UnsupportedOperationException(
        "Encryption is currently not supported");
  }

  /**
   * Encodes the page in the given buffer to a new buffer using RC4 decryption
   * with the given params.
   *
   * @param buffer decoded page buffer
   * @param pageOffset offset within the page at which to start encoding the
   *                   page data
   * @param params RC4 encryption parameters
   */
  protected ByteBuffer encodePage(ByteBuffer buffer, int pageOffset,
                                  KeyParameter params) {
    RC4Engine engine = getEngine();

    engine.init(CIPHER_ENCRYPT_MODE, params);

    int limit = buffer.limit();
    ByteBuffer encodeBuf = getTempEncodeBuffer();
    encodeBuf.clear();
    byte[] inArray = buffer.array();
    // note, we always start encoding at offset 0 so that we apply the cipher
    // to the correct part of the stream.  however, we can stop when we get to
    // the limit.
    engine.processBytes(inArray, 0, limit, encodeBuf.array(), 0);
    return encodeBuf;
  }

  /**
   * Reads and returns the header page (page 0) from the given pageChannel.
   */
  protected static ByteBuffer readHeaderPage(PageChannel pageChannel)
    throws IOException
  {
    ByteBuffer buffer = pageChannel.createPageBuffer();
    pageChannel.readPage(buffer, 0);
    return buffer;
  }

  /**
   * Returns a copy of the given key with the bytes of the given pageNumber
   * applied at the given offset using XOR.
   */
  public static byte[] applyPageNumber(byte[] key, int offset, 
                                          int pageNumber)
  {
    byte[] tmp = ByteUtil.copyOf(key, key.length);
    ByteBuffer bb = wrap(tmp);
    bb.position(offset);
    bb.putInt(pageNumber);

    for(int i = offset; i < (offset + 4); ++i) {
      tmp[i] ^= key[i];
    }

    return tmp;
  }

  /**
   * Hashes the given bytes using the given digest and returns the result.
   */
  public static byte[] hash(Digest digest, byte[] bytes) {
    return hash(digest, bytes, null, 0);
  }

  public static byte[] hash(Digest digest, byte[] bytes1, byte[] bytes2) {
    return hash(digest, bytes1, bytes2, 0);
  }

  /**
   * Hashes the given bytes using the given digest and returns the hash fixed
   * to the given length.
   */
  public static byte[] hash(Digest digest, byte[] bytes, int resultLen) {
    return hash(digest, bytes, null, resultLen);
  }

  public static byte[] hash(Digest digest, byte[] bytes1, byte[] bytes2,
                            int resultLen) {
    digest.reset();

    digest.update(bytes1, 0, bytes1.length);

    if(bytes2 != null) {
      digest.update(bytes2, 0, bytes2.length);
    }

    // Get digest value
    byte[] digestBytes = new byte[digest.getDigestSize()];
    digest.doFinal(digestBytes, 0);    

    // adjust to desired length
    if(resultLen > 0) {
      digestBytes = fixToLength(digestBytes, resultLen);
    }
    
    return digestBytes;
  }

  /**
   * @return a byte array of the given length, truncating or padding the given
   * byte array as necessary.
   */
  public static byte[] fixToLength(byte[] bytes, int len) {
    if(bytes.length != len) {
      bytes = ByteUtil.copyOf(bytes, len);
    } 
    return bytes;
  }

  /**
   * @return a new ByteBuffer wrapping the given bytes with the appropriate
   *         byte order
   */
  public static ByteBuffer wrap(byte[] bytes) {
    return ByteBuffer.wrap(bytes).order(PageChannel.DEFAULT_BYTE_ORDER);
  }

  /**
   * @return {@code true} if the given bytes are all 0, {@code false}
   *         otherwise
   */
  protected static boolean isBlankKey(byte[] key) {
    for (byte byteVal : key) {
      if (byteVal != 0) {
        return false;
      }
    }
    return true;
  }  

}
