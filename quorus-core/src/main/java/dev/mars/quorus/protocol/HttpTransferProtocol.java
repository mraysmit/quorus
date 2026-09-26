package dev.mars.quorus.protocol;

/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import dev.mars.quorus.connection.RuntimeCredential;
import dev.mars.quorus.connection.ServiceConnection;
import dev.mars.quorus.connection.TlsPeerPolicy;
import dev.mars.quorus.core.TransferDirection;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.core.TransferResult;
import dev.mars.quorus.core.TransferStatus;
import dev.mars.quorus.core.exceptions.TransferException;
import dev.mars.quorus.storage.ChecksumCalculator;
import dev.mars.quorus.transfer.ProgressTracker;
import dev.mars.quorus.transfer.TransferContext;
import dev.mars.quorus.util.SensitiveDataRedactor;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static dev.mars.quorus.core.exceptions.QuorusErrorCode.QUORUS_1201;
import static dev.mars.quorus.core.exceptions.QuorusErrorCode.QUORUS_1202;
import static dev.mars.quorus.core.exceptions.QuorusErrorCode.QUORUS_1203;
import static dev.mars.quorus.core.exceptions.QuorusErrorCode.QUORUS_1204;
import static dev.mars.quorus.core.exceptions.QuorusErrorCode.QUORUS_1205;
import static dev.mars.quorus.core.exceptions.QuorusErrorCode.QUORUS_1206;
import static dev.mars.quorus.core.exceptions.QuorusErrorCode.QUORUS_1208;
import static dev.mars.quorus.core.exceptions.QuorusErrorCode.QUORUS_1209;
import static dev.mars.quorus.core.exceptions.QuorusErrorCode.QUORUS_1211;
import static dev.mars.quorus.core.exceptions.QuorusErrorCode.QUORUS_1213;
import static dev.mars.quorus.core.exceptions.QuorusErrorCode.QUORUS_1214;
import static dev.mars.quorus.core.exceptions.QuorusErrorCode.QUORUS_1215;
import static dev.mars.quorus.core.exceptions.QuorusErrorCode.QUORUS_1216;

/**
 * HTTP and HTTPS transfer adapter: blocking, streaming, built on Apache HttpClient 5
 * (ADR-0012 decision RT-Q5; plan item RT-03b).
 *
 * <p>{@link #transfer} is a plain blocking call, meant to run on a virtual thread. Downloads use GET
 * and uploads use PUT. File bytes stream through a fixed buffer and are never held whole in memory
 * (closing {@code ARCH-09}). The checksum is computed incrementally.
 *
 * <h2>Governed and ungoverned transfers</h2>
 * A transfer is <em>governed</em> when the request carries a {@link RuntimeCredential}, meaning the
 * agent has authorized it against a service connection. The two modes deliberately differ:
 * <table>
 *   <caption>Behaviour by mode</caption>
 *   <tr><th></th><th>Governed</th><th>Ungoverned</th></tr>
 *   <tr><td>Address</td><td>Only the agent-approved resolved addresses
 *       ({@link RuntimeCredential#approvedResolvedAddresses()}), through a per-transfer DNS resolver.
 *       No approved addresses means normal DNS</td><td>Normal DNS</td></tr>
 *   <tr><td>SNI, {@code Host}, hostname verification</td><td colspan="2">Always the hostname in the
 *       request URI, because the URI keeps the service hostname while the resolver chooses the socket
 *       address</td></tr>
 *   <tr><td>Trust</td><td>PKIX by the base trust, then the approved-CA restriction and leaf pins
 *       ({@link TlsPeerPolicy#governedTrustManager})</td><td>PKIX by the base trust</td></tr>
 *   <tr><td>TLS versions</td><td>From the credential's minimum TLS version</td><td>Platform default</td></tr>
 *   <tr><td>Redirects</td><td>Never followed; a redirect fails the transfer</td><td>Followed</td></tr>
 *   <tr><td>Authorization</td><td>{@code Bearer} or {@code Basic} from the credential; the secret copy is
 *       wiped after use</td><td>None</td></tr>
 * </table>
 *
 * <p>Why this client: the JDK {@code java.net.http.HttpClient} cannot connect to a pinned address while
 * sending the service hostname as {@code Host} and verifying the certificate against it (measured on
 * JDK 27, see ADR-0012). Here the per-client {@link DnsResolver} makes the approved address the only
 * reachable one, while the request URI keeps the hostname, so JSSE endpoint identification and the
 * client's RFC 6125 hostname verifier ({@link HostnameVerificationPolicy#BOTH}) both check the
 * certificate against the service hostname.
 *
 * <h2>Trust anchors</h2>
 * The base trust defaults to the JVM default trust store. A base trust can be injected for tests;
 * making production trust anchors configurable is register item SEC-07.
 *
 * <h2>Files, cancellation and abort</h2>
 * A download writes to {@code <destination>.tmp} and moves it onto the destination only after the
 * whole body has arrived and the checksum matched. On any failure the partial file is deleted, and no
 * destination file is created. Between buffers the adapter checks {@link TransferContext#shouldContinue()}
 * and the thread's interrupt status, so cancelled transfers stop promptly. Socket reads are not
 * interruptible, so a stalled server is bounded by the socket timeout. {@link #abort()} closes every
 * in-flight connection of this adapter immediately.
 */
public class HttpTransferProtocol implements TransferProtocol {
    private static final Logger logger = LoggerFactory.getLogger(HttpTransferProtocol.class);

    private static final Timeout CONNECT_TIMEOUT = Timeout.ofSeconds(30);
    private static final Timeout SOCKET_TIMEOUT = Timeout.ofSeconds(60);
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024 * 1024; // 10GB
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final String USER_AGENT = "Quorus/1.0";

    private final X509TrustManager baseTrust;
    private final Set<CloseableHttpClient> inFlight = ConcurrentHashMap.newKeySet();

    /** Creates an adapter that validates TLS peers against the JVM default trust store. */
    public HttpTransferProtocol() {
        this(null);
    }

    /**
     * Creates an adapter with explicit base trust anchors.
     *
     * @param baseTrust trust anchors for PKIX validation, applied before any governed CA restriction or
     *                  leaf pin; {@code null} means the JVM default trust store
     */
    public HttpTransferProtocol(X509TrustManager baseTrust) {
        this.baseTrust = baseTrust;
        logger.info("HttpTransferProtocol initialized: connectTimeout={}, socketTimeout={}, maxFileSize={} bytes",
                CONNECT_TIMEOUT, SOCKET_TIMEOUT, MAX_FILE_SIZE);
    }

    @Override
    public String getProtocolName() {
        return "http";
    }

    @Override
    public boolean canHandle(TransferRequest request) {
        if (request == null || request.getSourceUri() == null) {
            logger.debug("canHandle: request or sourceUri is null");
            return false;
        }
        TransferDirection direction = request.getDirection();
        if (direction == TransferDirection.DOWNLOAD) {
            return isHttp(request.getSourceUri());
        }
        if (direction == TransferDirection.UPLOAD) {
            URI destinationUri = request.getDestinationUri();
            return destinationUri != null && isHttp(destinationUri);
        }
        logger.debug("canHandle: unsupported direction={}", direction);
        return false;
    }

    /**
     * Performs the transfer on the calling thread and returns when it has completed.
     *
     * @throws TransferException if the request is invalid, the connection or TLS policy fails, the server
     *                           answers with a non-success status (including any redirect of a governed
     *                           transfer), the checksum does not match, or the transfer is cancelled
     */
    @Override
    public TransferResult transfer(TransferRequest request, TransferContext context) throws TransferException {
        Instant startTime = Instant.now();
        ProgressTracker progressTracker = new ProgressTracker(context.getJobId());
        progressTracker.start();
        try {
            validateRequest(request);
        } catch (TransferException e) {
            logger.error("[{}] HTTP request validation failed: jobId={}, error={}", QUORUS_1201.code(),
                    context.getJobId(), e.getMessage());
            throw e;
        }
        return request.getDirection() == TransferDirection.UPLOAD
                ? upload(request, context, startTime, progressTracker)
                : download(request, context, startTime, progressTracker);
    }

    @Override
    public boolean supportsResume() {
        return false;
    }

    @Override
    public boolean supportsPause() {
        return true;
    }

    @Override
    public long getMaxFileSize() {
        return MAX_FILE_SIZE;
    }

    /** Immediately closes every in-flight connection of this adapter; their transfers fail. */
    @Override
    public void abort() {
        logger.debug("HTTP abort: closing {} in-flight client(s)", inFlight.size());
        for (CloseableHttpClient client : inFlight) {
            client.close(CloseMode.IMMEDIATE);
        }
    }

    // ------------------------------------------------------------------ download

    private TransferResult download(TransferRequest request, TransferContext context, Instant startTime,
                                    ProgressTracker progressTracker) throws TransferException {
        Path destination = request.getDestinationPath();
        Path temp = destination.resolveSibling(destination.getFileName() + ".tmp");
        URI source = request.getSourceUri();
        RuntimeCredential credential = request.getRuntimeCredential();
        logger.info("Starting HTTP download from {}", SensitiveDataRedactor.redact(source.toString()));
        try {
            Files.createDirectories(destination.getParent());
        } catch (IOException e) {
            logger.error("[{}] HTTP destination directory creation failed: jobId={}, error={}", QUORUS_1202.code(),
                    context.getJobId(), e.getMessage());
            throw new TransferException(context.getJobId(), "Failed to create directory", e);
        }

        ClassicHttpRequest get = ClassicRequestBuilder.get(source).build();
        applyAuthorization(get, credential);
        try (CloseableHttpClient client = clientFor(credential, source)) {
            String checksum = execute(client, get, context, response -> {
                requireStatus(response, context, 200);
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    logger.error("[{}] HTTP download failed: empty response body", QUORUS_1204.code());
                    throw new TransferException(context.getJobId(), "Empty response body");
                }
                long length = entity.getContentLength();
                if (length >= 0) {
                    progressTracker.setTotalBytes(length);
                    context.getJob().setTotalBytes(length);
                }
                try (InputStream in = entity.getContent(); OutputStream out = Files.newOutputStream(temp)) {
                    return copy(in, out, context, progressTracker);
                }
            });
            verifyChecksum(request, context, checksum, QUORUS_1205.code());
            moveIntoPlace(temp, destination);
            long bytes = Files.size(destination);
            context.getJob().complete(checksum);
            logger.info("HTTP download completed: jobId={}, bytesTransferred={}, checksum={}",
                    context.getJobId(), bytes, checksum);
            return completed(context, startTime, bytes, checksum);
        } catch (TransferException e) {
            deleteQuietly(temp);
            throw e;
        } catch (IOException e) {
            deleteQuietly(temp);
            logger.error("[{}] HTTP download failed: jobId={}, error={}", QUORUS_1206.code(), context.getJobId(),
                    e.getMessage());
            throw new TransferException(context.getJobId(), "HTTP download failed: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ upload

    private TransferResult upload(TransferRequest request, TransferContext context, Instant startTime,
                                  ProgressTracker progressTracker) throws TransferException {
        Path source = Path.of(request.getSourceUri());
        if (!Files.isRegularFile(source)) {
            logger.error("[{}] HTTP upload source file not found: {}", QUORUS_1208.code(), source);
            throw new TransferException(context.getJobId(), "Source file not found: " + source);
        }
        URI destination = request.getDestinationUri();
        RuntimeCredential credential = request.getRuntimeCredential();
        logger.info("Starting HTTP upload to {}", SensitiveDataRedactor.redact(destination.toString()));
        try {
            long bytes = Files.size(source);
            progressTracker.setTotalBytes(bytes);
            context.getJob().setTotalBytes(bytes);
            String checksum = checksumOf(source);
            verifyChecksum(request, context, checksum, QUORUS_1209.code());

            ClassicHttpRequest put = ClassicRequestBuilder.put(destination)
                    .setEntity(new FileEntity(source.toFile(), ContentType.APPLICATION_OCTET_STREAM))
                    .build();
            applyAuthorization(put, credential);
            try (CloseableHttpClient client = clientFor(credential, destination)) {
                execute(client, put, context, response -> {
                    requireStatus(response, context, 200, 201, 204);
                    return null;
                });
            }
            progressTracker.updateProgress(bytes);
            context.getJob().updateProgress(bytes);
            context.getJob().complete(checksum);
            logger.info("HTTP upload completed: jobId={}, bytesTransferred={}", context.getJobId(), bytes);
            return completed(context, startTime, bytes, checksum);
        } catch (IOException e) {
            logger.error("[{}] HTTP upload failed: jobId={}, error={}", QUORUS_1211.code(), context.getJobId(),
                    e.getMessage());
            throw new TransferException(context.getJobId(), "HTTP upload failed: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ client construction

    /**
     * Builds the client for one transfer. Governed transfers get the approved-address resolver, the
     * governed trust manager, the credential's TLS versions and no redirect handling.
     */
    private CloseableHttpClient clientFor(RuntimeCredential credential, URI target) throws TransferException {
        boolean governed = credential != null;
        SSLContext sslContext = sslContext(governed
                ? TlsPeerPolicy.governedTrustManager(baseTrust, credential.approvedCaIds(), credential.tlsPeerFingerprints())
                : baseTrust);
        ClientTlsStrategyBuilder tls = ClientTlsStrategyBuilder.create()
                .setSslContext(sslContext)
                .setHostVerificationPolicy(HostnameVerificationPolicy.BOTH);
        if (governed) {
            tls.setTlsVersions(TlsPeerPolicy.enabledProtocols(credential.minimumTlsVersion()));
        }
        var connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(tls.buildClassic())
                .setDnsResolver(governed ? approvedAddressResolver(credential, target) : SystemDefaultDnsResolver.INSTANCE)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(CONNECT_TIMEOUT)
                        .setSocketTimeout(SOCKET_TIMEOUT)
                        .build())
                .build();
        var builder = HttpClients.custom()
                .setConnectionManager(connections)
                .setUserAgent(USER_AGENT)
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(SOCKET_TIMEOUT).build());
        if (governed) {
            builder.disableRedirectHandling();
        }
        return builder.build();
    }

    private static SSLContext sslContext(X509TrustManager trust) throws TransferException {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trust == null ? null : new TrustManager[]{trust}, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new TransferException("http", "Unable to initialise TLS", e);
        }
    }

    /**
     * Resolves only the service hostname, and only to the agent-approved addresses. With no approved
     * addresses the hostname resolves normally. Any other hostname cannot be resolved, which also rules
     * out a redirect to another host.
     */
    private static DnsResolver approvedAddressResolver(RuntimeCredential credential, URI target) {
        List<String> approved = credential.approvedResolvedAddresses();
        if (approved.isEmpty()) {
            return SystemDefaultDnsResolver.INSTANCE;
        }
        String serviceHost = target.getHost();
        return new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                if (!serviceHost.equalsIgnoreCase(host)) {
                    throw new UnknownHostException("host is not the governed service endpoint: " + host);
                }
                InetAddress[] addresses = new InetAddress[approved.size()];
                for (int i = 0; i < addresses.length; i++) {
                    addresses[i] = InetAddress.ofLiteral(approved.get(i));
                }
                return addresses;
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
    }

    private static void applyAuthorization(ClassicHttpRequest request, RuntimeCredential credential) {
        if (credential == null) {
            return;
        }
        char[] secret = credential.copySecret();
        try {
            if (credential.authenticationType() == ServiceConnection.AuthenticationType.BEARER) {
                request.addHeader("Authorization", "Bearer " + new String(secret));
            } else if (credential.authenticationType() == ServiceConnection.AuthenticationType.BASIC
                    || credential.authenticationType() == ServiceConnection.AuthenticationType.PASSWORD) {
                String material = credential.identity() + ":" + new String(secret);
                request.addHeader("Authorization", "Basic "
                        + Base64.getEncoder().encodeToString(material.getBytes(StandardCharsets.UTF_8)));
            }
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    // ------------------------------------------------------------------ execution helpers

    /** Response handler that may fail with a {@link TransferException}. */
    @FunctionalInterface
    private interface ResponseHandler<T> {
        T handle(ClassicHttpResponse response) throws IOException, TransferException;
    }

    /**
     * Executes the request with this adapter's abort registration. A {@link TransferException} thrown
     * inside the handler is unwrapped and rethrown as is.
     */
    private <T> T execute(CloseableHttpClient client, ClassicHttpRequest request, TransferContext context,
                          ResponseHandler<T> handler) throws IOException, TransferException {
        inFlight.add(client);
        try {
            return client.execute(request, response -> {
                try {
                    return handler.handle(response);
                } catch (TransferException e) {
                    throw new HandlerFailure(e);
                }
            });
        } catch (HandlerFailure e) {
            throw e.transferException;
        } finally {
            inFlight.remove(client);
        }
    }

    /** Carries a {@link TransferException} out of the client's response handler. */
    private static final class HandlerFailure extends IOException {
        private final TransferException transferException;

        private HandlerFailure(TransferException transferException) {
            super(transferException.getMessage(), transferException);
            this.transferException = transferException;
        }
    }

    private static void requireStatus(ClassicHttpResponse response, TransferContext context, int... accepted)
            throws TransferException {
        int code = response.getCode();
        for (int ok : accepted) {
            if (code == ok) {
                return;
            }
        }
        logger.error("[{}] HTTP request failed: statusCode={}, reason={}", QUORUS_1203.code(), code,
                response.getReasonPhrase());
        throw new TransferException(context.getJobId(), "HTTP " + code + ": " + response.getReasonPhrase());
    }

    /** Streams {@code in} to {@code out}, updating progress and checking cancellation; returns the checksum. */
    private static String copy(InputStream in, OutputStream out, TransferContext context, ProgressTracker progress)
            throws IOException, TransferException {
        ChecksumCalculator checksum = new ChecksumCalculator();
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        for (int read; (read = in.read(buffer)) != -1; ) {
            if (!context.shouldContinue() || Thread.currentThread().isInterrupted()) {
                throw new TransferException(context.getJobId(), "HTTP transfer cancelled");
            }
            out.write(buffer, 0, read);
            checksum.update(buffer, 0, read);
            total += read;
            progress.updateProgress(total);
            context.getJob().updateProgress(total);
        }
        return checksum.getChecksum();
    }

    private static String checksumOf(Path file) throws IOException {
        ChecksumCalculator checksum = new ChecksumCalculator();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = Files.newInputStream(file)) {
            for (int read; (read = in.read(buffer)) != -1; ) {
                checksum.update(buffer, 0, read);
            }
        }
        return checksum.getChecksum();
    }

    private static void verifyChecksum(TransferRequest request, TransferContext context, String actual, String code)
            throws TransferException {
        String expected = request.getExpectedChecksum();
        if (expected != null && !expected.isEmpty() && !expected.equals(actual)) {
            logger.error("[{}] HTTP checksum mismatch: expected={}, actual={}", code, expected, actual);
            throw new TransferException(context.getJobId(),
                    "Checksum mismatch - expected: " + expected + ", actual: " + actual);
        }
    }

    private static void moveIntoPlace(Path temp, Path destination) throws IOException {
        try {
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.warn("Could not delete partial download {}: {}", file, e.getMessage());
        }
    }

    private static TransferResult completed(TransferContext context, Instant startTime, long bytes, String checksum) {
        return TransferResult.builder()
                .requestId(context.getJobId())
                .finalStatus(TransferStatus.COMPLETED)
                .bytesTransferred(bytes)
                .startTime(startTime)
                .endTime(Instant.now())
                .actualChecksum(checksum)
                .build();
    }

    // ------------------------------------------------------------------ validation

    private void validateRequest(TransferRequest request) throws TransferException {
        if (request.getSourceUri() == null) {
            logger.error("[{}] HTTP validation: source URI is null", QUORUS_1213.code());
            throw new TransferException(request.getRequestId(), "Source URI cannot be null");
        }
        TransferDirection direction = request.getDirection();
        if (direction == TransferDirection.DOWNLOAD && request.getDestinationPath() == null) {
            logger.error("[{}] HTTP validation: destination path is null for download", QUORUS_1214.code());
            throw new TransferException(request.getRequestId(), "Destination path cannot be null for download");
        }
        if (direction == TransferDirection.UPLOAD && request.getDestinationUri() == null) {
            logger.error("[{}] HTTP validation: destination URI is null for upload", QUORUS_1215.code());
            throw new TransferException(request.getRequestId(), "Destination URI cannot be null for upload");
        }
        if (!canHandle(request)) {
            logger.error("[{}] HTTP validation: protocol cannot handle this request", QUORUS_1216.code());
            throw new TransferException(request.getRequestId(), "HTTP protocol cannot handle this request");
        }
    }

    private static boolean isHttp(URI uri) {
        String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }
}
