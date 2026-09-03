/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Production HashiCorp Vault KV v2 provider using an externally supplied workload token. */
public final class VaultKvV2SecretProvider implements SecretProvider {
    private final URI vaultAddress;
    private final Supplier<char[]> tokenSupplier;
    private final VaultTransport transport;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VaultKvV2SecretProvider(URI vaultAddress, Supplier<char[]> tokenSupplier, VaultTransport transport) {
        this.vaultAddress = requireHttps(vaultAddress);
        this.tokenSupplier = Objects.requireNonNull(tokenSupplier, "tokenSupplier");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    public static VaultKvV2SecretProvider usingHttpClient(URI address, Supplier<char[]> tokenSupplier,
                                                           Duration timeout) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        return new VaultKvV2SecretProvider(address, tokenSupplier, (uri, headers) -> {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(timeout).GET();
            headers.forEach(request::header);
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new VaultResponse(response.statusCode(), response.body());
        });
    }

    @Override public String providerId() { return "VAULT_KV_V2"; }

    @Override
    public SecretLease resolve(SecretReference reference) throws Exception {
        if (!providerId().equalsIgnoreCase(reference.provider())) {
            throw new IllegalArgumentException("Secret reference provider does not match Vault KV v2");
        }
        if (!reference.usableAt(Instant.now())) {
            throw new IllegalStateException("Secret reference is revoked or expired");
        }
        if (!reference.path().matches("[A-Za-z0-9][A-Za-z0-9._/-]*") || reference.path().contains("..")) {
            throw new IllegalArgumentException("Vault path contains unsupported characters");
        }
        URI requestUri = URI.create(stripTrailingSlash(vaultAddress.toString()) + "/v1/" + reference.path()
                + "?version=" + reference.version());
        char[] token = Objects.requireNonNull(tokenSupplier.get(), "Vault token unavailable");
        try {
            VaultResponse response = transport.get(requestUri, Map.of("X-Vault-Token", new String(token)));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Vault secret resolution failed with status " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode value = root.path("data").path("data").path(reference.key());
            if (!value.isTextual()) throw new IllegalStateException("Vault response did not contain the requested field");
            return new SecretLease(reference.secretReferenceId(), value.textValue().toCharArray(),
                    reference.expiresAt());
        } finally {
            Arrays.fill(token, '\0');
        }
    }

    private static URI requireHttps(URI value) {
        Objects.requireNonNull(value, "vaultAddress");
        if (!"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null
                || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null) {
            throw new IllegalArgumentException("Vault address must be a credential-free HTTPS origin");
        }
        return value;
    }
    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @FunctionalInterface
    public interface VaultTransport {
        VaultResponse get(URI uri, Map<String, String> headers) throws Exception;
    }
    public record VaultResponse(int statusCode, String body) { }
}
