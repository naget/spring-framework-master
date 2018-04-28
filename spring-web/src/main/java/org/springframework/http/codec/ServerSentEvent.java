/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.http.codec;

import java.time.Duration;

import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.lang.Nullable;

/**
 * Representation for a Server-Sent Event for use with Spring's reactive Web support.
 * {@code Flux<ServerSentEvent>} or {@code Observable<ServerSentEvent>} is the
 * reactive equivalent to Spring MVC's {@code SseEmitter}.
 *
 * @param <T> the type of data that this event contains
 *
 * @author Sebastien Deleuze
 * @author Arjen Poutsma
 * @since 5.0
 * @see ServerSentEventHttpMessageWriter
 * @see <a href="https://www.w3.org/TR/eventsource/">Server-Sent Events W3C recommendation</a>
 */
public class ServerSentEvent<T> {

    private final String id;

    private final String event;

    private final T data;

    private final Duration retry;

    private final String comment;


    private ServerSentEvent(String id, String event, T data, Duration retry, String comment) {
        this.id = id;
        this.event = event;
        this.data = data;
        this.retry = retry;
        this.comment = comment;
    }


    /**
     * Return the {@code id} field of this event, if available.
     */
    @Nullable
    public String id() {
        return this.id;
    }

    /**
     * Return the {@code event} field of this event, if available.
     */
	@Nullable
    public String event() {
        return this.event;
    }

    /**
     * Return the {@code data} field of this event, if available.
     */
	@Nullable
    public T data() {
        return this.data;
    }

    /**
     * Return the {@code retry} field of this event, if available.
     */
	@Nullable
    public Duration retry() {
        return this.retry;
    }

    /**
     * Return the comment of this event, if available.
     */
	@Nullable
    public String comment() {
        return this.comment;
    }


    @Override
    public String toString() {
        return ("ServerSentEvent [id = '" + this.id + '\'' + ", event='" + this.event + '\'' +
                ", data=" + this.data + ", retry=" + this.retry + ", comment='" + this.comment + '\'' + ']');
    }


	/**
	 * Return a builder for a {@code SseEvent}.
	 * @param <T> the type of data that this event contains
	 * @return the builder
	 */
	public static <T> Builder<T> builder() {
		return new BuilderImpl<>();
	}

	/**
	 * Return a builder for a {@code SseEvent}, populated with the give {@linkplain #data() data}.
	 * @param <T> the type of data that this event contains
	 * @return the builder
	 */
	public static <T> Builder<T> builder(T data) {
		return new BuilderImpl<>(data);
	}


    /**
     * A mutable builder for a {@code SseEvent}.
     *
     * @param <T> the type of data that this event contains
     */
    public interface Builder<T> {

        /**
         * Set the value of the {@code id} field.
         *
         * @param id the value of the id field
         * @return {@code this} builder
         */
        Builder<T> id(String id);

        /**
         * Set the value of the {@code event} field.
         *
         * @param event the value of the event field
         * @return {@code this} builder
         */
        Builder<T> event(String event);

        /**
         * Set the value of the {@code data} field. If the {@code data} argument is a multi-line {@code String}, it
         * will be turned into multiple {@code data} field lines as defined in Server-Sent Events
         * W3C recommendation. If {@code data} is not a String, it will be
         * {@linkplain Jackson2JsonEncoder encoded} into JSON.
         *
         * @param data the value of the data field
         * @return {@code this} builder
         */
        Builder<T> data(T data);

        /**
         * Set the value of the {@code retry} field.
         *
         * @param retry the value of the retry field
         * @return {@code this} builder
         */
        Builder<T> retry(Duration retry);

        /**
         * Set SSE comment. If a multi-line comment is provided, it will be turned into multiple
         * SSE comment lines as defined in Server-Sent Events W3C
         * recommendation.
         *
         * @param comment the comment to set
         * @return {@code this} builder
         */
        Builder<T> comment(String comment);

        /**
         * Builds the event.
         *
         * @return the built event
         */
        ServerSentEvent<T> build();

    }

    private static class BuilderImpl<T> implements Builder<T> {

        private T data;

        private String id;

        private String event;

        private Duration retry;

        private String comment;

	    public BuilderImpl() {
	    }

	    public BuilderImpl(T data) {
		    this.data = data;
	    }

	    @Override
        public Builder<T> id(String id) {
            this.id = id;
            return this;
        }

        @Override
        public Builder<T> event(String event) {
            this.event = event;
            return this;
        }

        @Override
        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }

        @Override
        public Builder<T> retry(Duration retry) {
            this.retry = retry;
            return this;
        }

        @Override
        public Builder<T> comment(String comment) {
            this.comment = comment;
            return this;
        }

        @Override
        public ServerSentEvent<T> build() {
            return new ServerSentEvent<T>(this.id, this.event, this.data, this.retry, this.comment);
        }
    }

}
