/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core.io.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.IntPredicate;

import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Default implementation of the {@link DataBuffer} interface that uses a {@link
 * ByteBuffer} internally, with separate read and write positions. Constructed
 * using the {@link DefaultDataBufferFactory}.
 *
 * @author Arjen Poutsma
 * @since 5.0
 * @see DefaultDataBufferFactory
 */
public class DefaultDataBuffer implements DataBuffer {

	private final DefaultDataBufferFactory dataBufferFactory;

	private ByteBuffer byteBuffer;

	private int readPosition;

	private int writePosition;


	/**
	 * Create a new {@code DefaultDataBuffer} based on the given
	 * {@code ByteBuffer}. Both reading and writing position of this buffer are
	 * based on the current {@linkplain
	 * ByteBuffer#position() position} of the given buffer.
	 * @param byteBuffer the buffer to base this buffer on
	 */
	DefaultDataBuffer(ByteBuffer byteBuffer, DefaultDataBufferFactory dataBufferFactory) {
		this(byteBuffer, byteBuffer.position(), byteBuffer.position(), dataBufferFactory);
	}

	DefaultDataBuffer(ByteBuffer byteBuffer, int readPosition, int writePosition,
			DefaultDataBufferFactory dataBufferFactory) {

		Assert.notNull(byteBuffer, "'byteBuffer' must not be null");
		Assert.isTrue(readPosition >= 0, "'readPosition' must be 0 or higher");
		Assert.isTrue(writePosition >= 0, "'writePosition' must be 0 or higher");
		Assert.isTrue(readPosition <= writePosition,
				"'readPosition' must be smaller than or equal to 'writePosition'");
		Assert.notNull(dataBufferFactory, "'dataBufferFactory' must not be null");

		this.byteBuffer = byteBuffer;
		this.readPosition = readPosition;
		this.writePosition = writePosition;
		this.dataBufferFactory = dataBufferFactory;
	}


	/**
	 * Directly exposes the native {@code ByteBuffer} that this buffer is based on.
	 * @return the wrapped byte buffer
	 */
	public ByteBuffer getNativeBuffer() {
		return this.byteBuffer;
	}

	@Override
	public DefaultDataBufferFactory factory() {
		return this.dataBufferFactory;
	}

	@Override
	public int indexOf(IntPredicate predicate, int fromIndex) {
		Assert.notNull(predicate, "'predicate' must not be null");

		if (fromIndex < 0) {
			fromIndex = 0;
		}
		else if (fromIndex >= this.writePosition) {
			return -1;
		}
		for (int i = fromIndex; i < this.writePosition; i++) {
			byte b = this.byteBuffer.get(i);
			if (predicate.test(b)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public int lastIndexOf(IntPredicate predicate, int fromIndex) {
		Assert.notNull(predicate, "'predicate' must not be null");
		int i = Math.min(fromIndex, this.writePosition - 1);
		for (; i >= 0; i--) {
			byte b = this.byteBuffer.get(i);
			if (predicate.test(b)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public int readableByteCount() {
		return this.writePosition - this.readPosition;
	}

	@Override
	public byte read() {
		return readInternal(ByteBuffer::get);
	}

	@Override
	public DefaultDataBuffer read(byte[] destination) {
		Assert.notNull(destination, "'destination' must not be null");
		readInternal(b -> b.get(destination));
		return this;
	}

	@Override
	public DefaultDataBuffer read(byte[] destination, int offset, int length) {
		Assert.notNull(destination, "'destination' must not be null");
		readInternal(b -> b.get(destination, offset, length));
		return this;
	}

	/**
	 * Internal read method that keeps track of the {@link #readPosition} before and after
	 * applying the given function on {@link #byteBuffer}.
	 */
	private <T> T readInternal(Function<ByteBuffer, T> function) {
		// Explicit cast for compatibility with covariant return type on JDK 9's ByteBuffer
		((Buffer) this.byteBuffer).position(this.readPosition);
		try {
			return function.apply(this.byteBuffer);
		}
		finally {
			this.readPosition = this.byteBuffer.position();
		}
	}

	@Override
	public DefaultDataBuffer write(byte b) {
		ensureExtraCapacity(1);
		writeInternal(buffer -> buffer.put(b));
		return this;
	}

	@Override
	public DefaultDataBuffer write(byte[] source) {
		Assert.notNull(source, "'source' must not be null");
		ensureExtraCapacity(source.length);
		writeInternal(buffer -> buffer.put(source));
		return this;
	}

	@Override
	public DefaultDataBuffer write(byte[] source, int offset, int length) {
		Assert.notNull(source, "'source' must not be null");
		ensureExtraCapacity(length);
		writeInternal(buffer -> buffer.put(source, offset, length));
		return this;
	}

	@Override
	public DataBuffer write(DataBuffer... buffers) {
		if (!ObjectUtils.isEmpty(buffers)) {
			ByteBuffer[] byteBuffers =
					Arrays.stream(buffers).map(DataBuffer::asByteBuffer)
							.toArray(ByteBuffer[]::new);
			write(byteBuffers);
		}
		return this;
	}

	@Override
	public DefaultDataBuffer write(ByteBuffer... byteBuffers) {
		Assert.notEmpty(byteBuffers, "'byteBuffers' must not be empty");
		int extraCapacity = Arrays.stream(byteBuffers).mapToInt(ByteBuffer::remaining).sum();
		ensureExtraCapacity(extraCapacity);
		Arrays.stream(byteBuffers)
				.forEach(byteBuffer -> writeInternal(buffer -> buffer.put(byteBuffer)));
		return this;
	}

	/**
	 * Internal write method that keeps track of the {@link #writePosition} before and
	 * after applying the given function on {@link #byteBuffer}.
	 */
	private <T> T writeInternal(Function<ByteBuffer, T> function) {
		// Explicit cast for compatibility with covariant return type on JDK 9's ByteBuffer
		((Buffer) this.byteBuffer).position(this.writePosition);
		try {
			return function.apply(this.byteBuffer);
		}
		finally {
			this.writePosition = this.byteBuffer.position();
		}
	}

	@Override
	public DataBuffer slice(int index, int length) {
		int oldPosition = this.byteBuffer.position();
		// Explicit access via Buffer base type for compatibility
		// with covariant return type on JDK 9's ByteBuffer...
		Buffer buffer = this.byteBuffer;
		try {
			buffer.position(index);
			ByteBuffer slice = this.byteBuffer.slice();
			// Explicit cast for compatibility with covariant return type on JDK 9's ByteBuffer
			((Buffer) slice).limit(length);
			return new SlicedDefaultDataBuffer(slice, 0, length, this.dataBufferFactory);
		}
		finally {
			buffer.position(oldPosition);
		}
	}

	@Override
	public ByteBuffer asByteBuffer() {
		ByteBuffer duplicate = this.byteBuffer.duplicate();
		// Explicit access via Buffer base type for compatibility
		// with covariant return type on JDK 9's ByteBuffer...
		Buffer buffer = duplicate;
		buffer.position(this.readPosition);
		buffer.limit(this.writePosition);
		return duplicate;
	}

	@Override
	public InputStream asInputStream() {
		return new DefaultDataBufferInputStream();
	}

	@Override
	public OutputStream asOutputStream() {
		return new DefaultDataBufferOutputStream();
	}

	private void ensureExtraCapacity(int extraCapacity) {
		int neededCapacity = this.writePosition + extraCapacity;
		if (neededCapacity > this.byteBuffer.capacity()) {
			grow(neededCapacity);
		}
	}

	void grow(int minCapacity) {
		ByteBuffer oldBuffer = this.byteBuffer;
		ByteBuffer newBuffer =
				(oldBuffer.isDirect() ? ByteBuffer.allocateDirect(minCapacity) :
						ByteBuffer.allocate(minCapacity));

		// Explicit cast for compatibility with covariant return type on JDK 9's ByteBuffer
		final int remaining = readableByteCount();
		((Buffer) oldBuffer).position(this.readPosition).limit(this.writePosition);
		newBuffer.put(oldBuffer);

		this.byteBuffer = newBuffer;
		this.readPosition = 0;
		this.writePosition = remaining;
		oldBuffer.clear();
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof DefaultDataBuffer)) {
			return false;
		}
		DefaultDataBuffer other = (DefaultDataBuffer) obj;
		return (this.readPosition == other.readPosition &&
				this.writePosition == other.writePosition &&
				this.byteBuffer.equals(other.byteBuffer));
	}

	@Override
	public int hashCode() {
		return this.byteBuffer.hashCode();
	}

	@Override
	public String toString() {
		return this.byteBuffer.toString();
	}


	private class DefaultDataBufferInputStream extends InputStream {

		@Override
		public int available() throws IOException {
			return readableByteCount();
		}

		@Override
		public int read() {
			return readInternal(
					buffer -> readableByteCount() > 0 ? buffer.get() & 0xFF : -1);
		}

		@Override
		public int read(byte[] bytes, int off, int len) throws IOException {
			return readInternal(buffer -> {
				int count = readableByteCount();
				if (count > 0) {
					int minLen = Math.min(len, count);
					buffer.get(bytes, off, minLen);
					return minLen;
				}
				else {
					return -1;
				}
			});
		}
	}


	private class DefaultDataBufferOutputStream extends OutputStream {

		@Override
		public void write(int b) throws IOException {
			ensureExtraCapacity(1);
			writeInternal(buffer -> buffer.put((byte) b));
		}

		@Override
		public void write(byte[] bytes, int off, int len) throws IOException {
			ensureExtraCapacity(len);
			writeInternal(buffer -> buffer.put(bytes, off, len));
		}
	}


	private static class SlicedDefaultDataBuffer extends DefaultDataBuffer {

		SlicedDefaultDataBuffer(ByteBuffer byteBuffer, int readPosition,
				int writePosition, DefaultDataBufferFactory dataBufferFactory) {
			super(byteBuffer, readPosition, writePosition, dataBufferFactory);
		}

		@Override
		void grow(int minCapacity) {
			throw new UnsupportedOperationException(
					"Growing the capacity of a sliced buffer is not supported");
		}
	}

}
