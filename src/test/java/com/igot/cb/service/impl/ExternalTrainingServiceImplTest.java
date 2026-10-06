package com.igot.cb.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.cassandra.CassandraOperation;
import com.igot.cb.model.ApiResponse;
import com.igot.cb.service.UserAndOrgServiceImpl;
import com.igot.cb.storage.service.StorageService;
import com.igot.cb.user.UserUtilityService;
import com.igot.cb.util.AccessTokenValidator;
import com.igot.cb.util.CbExtServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

class ExternalTrainingServiceImplTest {

    @Mock
    private CbExtServerProperties serverConfig;

    @Mock
    private StorageService storageService;

    @Mock
    private KafkaTemplate kafkaTemplate;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private UserAndOrgServiceImpl userAndOrgService;

    @Mock
    private UserUtilityService userUtilityService;

    private ExternalTrainingServiceImpl externalTrainingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        externalTrainingService = new ExternalTrainingServiceImpl(storageService, serverConfig, kafkaTemplate,
                cassandraOperation, accessTokenValidator, mapper, userAndOrgService);
    }

    @Test
    void testValidateCsvFile_NullFile() {
        assertEquals("File is empty or not provided.", externalTrainingService.validateCsvFile(null));
    }

    @Test
    void testValidateCsvFile_EmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv", new byte[0]);
        assertEquals("File is empty or not provided.", externalTrainingService.validateCsvFile(file));
    }

    @Test
    void testValidateCsvFile_BlankFileName() {
        MockMultipartFile file = new MockMultipartFile("file", "", "text/csv", "Email\n".getBytes());
        assertEquals("File name is invalid.", externalTrainingService.validateCsvFile(file));
    }

    @Test
    void testValidateCsvFile_InvalidExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "data.txt", "text/plain", "Email\n".getBytes());
        assertEquals("Invalid file type. Only CSV files are allowed.", externalTrainingService.validateCsvFile(file));
    }

    @Test
    void testValidateCsvFile_InvalidHeader() {
        when(serverConfig.getExternalTrainingBatchSize()).thenReturn(10);
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv", "Name\n".getBytes());
        assertEquals("Invalid CSV header. Expected header: Email", externalTrainingService.validateCsvFile(file));
    }

    @Test
    void testValidateCsvFile_OnlyHeader_NoDataRows() {
        when(serverConfig.getExternalTrainingBatchSize()).thenReturn(10);
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv", "Email\n".getBytes());
        assertEquals("CSV file contains no data rows.", externalTrainingService.validateCsvFile(file));
    }

    @Test
    void testValidateCsvFile_WithinBatchSize_Valid() {
        when(serverConfig.getExternalTrainingBatchSize()).thenReturn(10);
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv",
                "Email\na@test.com\nb@test.com\n".getBytes());
        assertEquals("", externalTrainingService.validateCsvFile(file));
    }

    @Test
    void testValidateCsvFile_ExceedsBatchSize() {
        when(serverConfig.getExternalTrainingBatchSize()).thenReturn(1);
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv",
                "Email\na@test.com\nb@test.com\n".getBytes());
        assertEquals("CSV file should not contain more than 1 rows.", externalTrainingService.validateCsvFile(file));
    }

    @Test
    void testDownloadFile_readsFromConfiguredLocalBasePath() throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir") + "/";
        String fileName = "download-" + UUID.randomUUID() + ".csv";
        Path tempFile = Path.of(tempDir, fileName);
        Files.write(tempFile, "Email\na@test.com\n".getBytes(StandardCharsets.UTF_8));
        try {
            when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class)))
                    .thenReturn("user123");
            when(serverConfig.getLocalBasePath()).thenReturn(tempDir);

            ResponseEntity<?> response = externalTrainingService.downloadFile(fileName, "token123");

            assertEquals(HttpStatus.OK, response.getStatusCode());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testDownloadFile_fileNotPresent_returnsInternalServerError() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString(), any(ApiResponse.class)))
                .thenReturn("user123");
        when(serverConfig.getLocalBasePath()).thenReturn(System.getProperty("java.io.tmpdir") + "/");

        ResponseEntity<?> response = externalTrainingService.downloadFile(
                "no-such-file-" + UUID.randomUUID() + ".csv", "token123");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testDownloadBulkUploadSampleFile_readsFromConfiguredLocalBasePath_andDeletesAfter() throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir") + "/";
        String fileName = "sample-" + UUID.randomUUID() + ".csv";
        Path tempFile = Path.of(tempDir, fileName);
        Files.write(tempFile, "Email\n".getBytes(StandardCharsets.UTF_8));

        when(serverConfig.getExternalTrainingUserBulkUploadSampleFileName()).thenReturn(fileName);
        when(serverConfig.getLocalBasePath()).thenReturn(tempDir);

        ResponseEntity<?> response = externalTrainingService.downloadBulkUploadSampleFile();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(Files.exists(tempFile), "Sample file should be deleted after being served");
    }

    @Test
    void testDownloadBulkUploadSampleFile_fileNotPresent_returnsInternalServerError() {
        String fileName = "missing-sample-" + UUID.randomUUID() + ".csv";
        when(serverConfig.getExternalTrainingUserBulkUploadSampleFileName()).thenReturn(fileName);
        when(serverConfig.getLocalBasePath()).thenReturn(System.getProperty("java.io.tmpdir") + "/");

        ResponseEntity<?> response = externalTrainingService.downloadBulkUploadSampleFile();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
}
