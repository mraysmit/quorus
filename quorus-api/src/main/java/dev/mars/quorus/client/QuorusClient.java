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

package dev.mars.quorus.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.mars.quorus.api.dto.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

/**
 * Java client SDK for Quorus File Transfer API.
 * Provides a simple interface for interacting with the Quorus REST API.
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 1.0
 */
public class QuorusClient {

    private final String baseUrl;
    private final String authHeader;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public QuorusClient(String baseUrl, String username, String password) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public TransferJobResponseDto createTransfer(String sourceUri, String destinationPath) throws QuorusClientException {
        return createTransfer(sourceUri, destinationPath, null);
    }

    public TransferJobResponseDto createTransfer(String sourceUri, String destinationPath, String description) 
            throws QuorusClientException {
        TransferRequestDto request = new TransferRequestDto();
        request.setSourceUri(sourceUri);
        request.setDestinationPath(destinationPath);
        request.setDescription(description);

        try {
            String requestBody = objectMapper.writeValueAsString(request);
            
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/transfers"))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 201) {
                return objectMapper.readValue(response.body(), TransferJobResponseDto.class);
            } else {
                throw new QuorusClientException("Failed to create transfer: HTTP " + response.statusCode() + 
                                              " - " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new QuorusClientException("Failed to create transfer", e);
        }
    }

    public TransferJobResponseDto getTransferStatus(String jobId) throws QuorusClientException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/transfers/" + jobId))
                    .header("Authorization", authHeader)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), TransferJobResponseDto.class);
            } else if (response.statusCode() == 404) {
                throw new QuorusClientException("Transfer job not found: " + jobId);
            } else {
                throw new QuorusClientException("Failed to get transfer status: HTTP " + response.statusCode() + 
                                              " - " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new QuorusClientException("Failed to get transfer status", e);
        }
    }

    public String cancelTransfer(String jobId) throws QuorusClientException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/transfers/" + jobId))
                    .header("Authorization", authHeader)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                MessageResponse messageResponse = objectMapper.readValue(response.body(), MessageResponse.class);
                return messageResponse.getMessage();
            } else if (response.statusCode() == 404) {
                throw new QuorusClientException("Transfer job not found: " + jobId);
            } else if (response.statusCode() == 409) {
                throw new QuorusClientException("Transfer cannot be cancelled: " + jobId);
            } else {
                throw new QuorusClientException("Failed to cancel transfer: HTTP " + response.statusCode() + 
                                              " - " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new QuorusClientException("Failed to cancel transfer", e);
        }
    }

    public int getActiveTransferCount() throws QuorusClientException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/transfers/count"))
                    .header("Authorization", authHeader)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                CountResponse countResponse = objectMapper.readValue(response.body(), CountResponse.class);
                return countResponse.getCount();
            } else {
                throw new QuorusClientException("Failed to get active transfer count: HTTP " + response.statusCode() + 
                                              " - " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new QuorusClientException("Failed to get active transfer count", e);
        }
    }

    public String getServiceInfo() throws QuorusClientException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/info"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return response.body();
            } else {
                throw new QuorusClientException("Failed to get service info: HTTP " + response.statusCode() + 
                                              " - " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new QuorusClientException("Failed to get service info", e);
        }
    }

    public CompletableFuture<TransferJobResponseDto> createTransferAsync(String sourceUri, String destinationPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return createTransfer(sourceUri, destinationPath);
            } catch (QuorusClientException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Close the client and release resources.
     */
    public void close() {
        // HttpClient doesn't need explicit closing in Java 11+
    }
}
