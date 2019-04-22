/*
 * ====================================================================
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
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.core5.http.examples;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.bootstrap.AsyncServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.BasicRequestConsumer;
import org.apache.hc.core5.http.nio.BasicResponseProducer;
import org.apache.hc.core5.http.nio.entity.FileEntityProducer;
import org.apache.hc.core5.http.nio.entity.NoopEntityConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpDateGenerator;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.TimeValue;

/**
 * Example of asynchronous embedded HTTP/1.1 file server.
 */
public class AsyncFileServerExample {

    /** Example command line args: {@code "c:\temp" 8080} */
    public static void main(final String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Please specify document root directory");
            System.exit(1);
        }
        // Document root directory
        final File docRoot = new File(args[0]);
        int port = 8080;
        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }

        final IOReactorConfig config = IOReactorConfig.custom()
                .setSoTimeout(15, TimeUnit.SECONDS)
                .setTcpNoDelay(true)
                .build();

        final HttpAsyncServer server = AsyncServerBootstrap.bootstrap()
                .setIOReactorConfig(config)
                .register("*", new AsyncServerRequestHandler<Message<HttpRequest, Void>>() {

                    @Override
                    public AsyncRequestConsumer<Message<HttpRequest, Void>> prepare(
                            final HttpRequest request,
                            final EntityDetails entityDetails,
                            final HttpContext context) throws HttpException {
                        return new BasicRequestConsumer<>(entityDetails != null ? new NoopEntityConsumer() : null);
                    }

                    @Override
                    public void handle(
                            final Message<HttpRequest, Void> message,
                            final ResponseTrigger responseTrigger,
                            final HttpContext context) throws HttpException, IOException {
                        final HttpRequest request = message.getHead();
                        final URI requestUri;
                        try {
                            requestUri = request.getUri();
                        } catch (final URISyntaxException ex) {
                            throw new ProtocolException(ex.getMessage(), ex);
                        }
                        final String path = requestUri.getPath();
                        final File file = new File(docRoot, path);
                        if (!file.exists()) {

                            final String msg = "File " + file.getPath() + " not found";
                            println(msg);
                            responseTrigger.submitResponse(new BasicResponseProducer(
                                    HttpStatus.SC_NOT_FOUND,
                                    "<html><body><h1>" + msg + "</h1></body></html>",
                                    ContentType.TEXT_HTML), context);

                        } else if (!file.canRead() || file.isDirectory()) {

                            final String msg = "Cannot read file " + file.getPath();
                            println(msg);
                            responseTrigger.submitResponse(new BasicResponseProducer(
                                    HttpStatus.SC_FORBIDDEN,
                                    "<html><body><h1>" + msg + "</h1></body></html>",
                                    ContentType.TEXT_HTML), context);

                        } else {

                            final ContentType contentType;
                            final String filename = file.getName().toLowerCase(Locale.ROOT);
                            if (filename.endsWith(".txt")) {
                                contentType = ContentType.TEXT_PLAIN;
                            } else if (filename.endsWith(".html") || filename.endsWith(".htm")) {
                                contentType = ContentType.TEXT_HTML;
                            } else if (filename.endsWith(".xml")) {
                                contentType = ContentType.TEXT_XML;
                            } else {
                                contentType = ContentType.DEFAULT_BINARY;
                            }

                            final HttpCoreContext coreContext = HttpCoreContext.adapt(context);
                            final EndpointDetails endpoint = coreContext.getEndpointDetails();

                            println(endpoint + " | serving file " + file.getPath());

                            responseTrigger.submitResponse(new BasicResponseProducer(
                                    HttpStatus.SC_OK, new FileEntityProducer(file, contentType)), context);
                        }
                    }

                })
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                println("HTTP server shutting down");
                server.close(CloseMode.GRACEFUL);
            }
        });

        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(port));
        final ListenerEndpoint listenerEndpoint = future.get();
        println("Listening on " + listenerEndpoint.getAddress());
        server.awaitShutdown(TimeValue.MAX_VALUE);
    }

    static final void println(final String msg) {
        System.out.println(HttpDateGenerator.INSTANCE.getCurrentDate() + " | " + msg);
    }

}