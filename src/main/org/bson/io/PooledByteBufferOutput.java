/**
 * Copyright (c) 2008 - 2012 10gen, Inc. <http://10gen.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.bson.io;

import org.bson.util.BufferPool;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

public class PooledByteBufferOutput extends OutputBuffer {

    private final BufferPool<ByteBuffer> pool;
    private final List<ByteBuffer> bufferList = new ArrayList<ByteBuffer>();
    private int curBufferIndex = 0;
    private int position = 0;

    public PooledByteBufferOutput(BufferPool<ByteBuffer> pool) {
        this.pool = pool;
    }

    @Override
    public void write(final byte[] b, final int offset, final int len) {
        int currentOffset = offset;
        int remainingLen = len;
        while (remainingLen > 0) {
            ByteBuffer buf = getCurrentByteBuffer();
            int bytesToPutInCurrentBuffer = Math.min(buf.remaining(), remainingLen);
            buf.put(b, currentOffset, bytesToPutInCurrentBuffer);
            remainingLen -= bytesToPutInCurrentBuffer;
            currentOffset += bytesToPutInCurrentBuffer;
        }
        position += len;
    }

    @Override
    public void write(final int b) {
        getCurrentByteBuffer().put((byte) b);
        position++;
    }

    private ByteBuffer getCurrentByteBuffer() {
        ByteBuffer curByteBuffer = getByteBufferAtIndex(curBufferIndex);
        if (curByteBuffer.hasRemaining()) {
            return curByteBuffer;
        }

        curBufferIndex++;
        return getByteBufferAtIndex(curBufferIndex);
    }

    private ByteBuffer getByteBufferAtIndex(final int index) {
        if (bufferList.size() < index + 1) {
            bufferList.add(pool.get(1024 << index));
        }
        return bufferList.get(index);
    }

    @Override
    public int getPosition() {
        return position;
    }

    @Override
    public void writeInt(final int pos, final int x) {
        // TODO: ditch this method
        throw new UnsupportedOperationException();
    }

    /**
     * Backpatches the size of a document or message by writing the size into the four bytes starting at
     * getPosition() - size.
     *
     * @param size the size of the document or message
     */
    @Override
    public void backpatchSize(final int size) {
        backpatchSizeWithOffset(size, 0);
    }

    @Override
    protected void backpatchSize(final int size, final int additionalOffset) {
        backpatchSizeWithOffset(size, additionalOffset);
    }


    @Override
    public void setPosition(final int position) {
        // TODO: remove this method from the API
        throw new UnsupportedOperationException();
    }

    @Override
    public void seekEnd() {
        // TODO: remove the method
        throw new UnsupportedOperationException();
    }

    @Override
    public void seekStart() {
        // TODO: remove the method
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        return position;
    }

    @Override
    public int pipe(final OutputStream out) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void pipe(final SocketChannel socketChannel) throws IOException {
        for (ByteBuffer cur : bufferList) {
            cur.flip();
        }

        for (long bytesRead = 0; bytesRead < size();) {
            bytesRead += socketChannel.write(bufferList.toArray(new ByteBuffer[bufferList.size()]), 0, bufferList.size());
        }
    }

    @Override
    public void close() {
        for (ByteBuffer cur : bufferList) {
            pool.done(cur);
        }
        bufferList.clear();
    }

    // TODO: go backwards instead of forwards?  Probably doesn't matter with power of 2
    private void backpatchSizeWithOffset(int size, int additionalOffset) {
        int backpatchPosition = position - size - additionalOffset;
        int backpatchPositionInBuffer = backpatchPosition;
        int bufferIndex = 0;
        int bufferSize = 1024;
        int startPositionOfBuffer = 0;
        while (startPositionOfBuffer > backpatchPosition) {
            bufferIndex++;
            startPositionOfBuffer += bufferSize;
            backpatchPositionInBuffer -= bufferSize;
            bufferSize <<= 1;
        }

        // TODO: deal with buffer boundary
        ByteBuffer startBackpatchBuffer = getByteBufferAtIndex(bufferIndex);
        if (startBackpatchBuffer.capacity() < backpatchPositionInBuffer + 4) {
            throw new IllegalStateException("TODO: fix this");
        }
        startBackpatchBuffer.putInt(backpatchPositionInBuffer, size);
    }

}
