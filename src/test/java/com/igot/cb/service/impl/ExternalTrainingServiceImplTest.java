package com.igot.cb.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.igot.cb.util.CbExtServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class ExternalTrainingServiceImplTest {

    @Mock
    private CbExtServerProperties serverConfig;

    private ExternalTrainingServiceImpl externalTrainingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        externalTrainingService = new ExternalTrainingServiceImpl();
        ReflectionTestUtils.setField(externalTrainingService, "serverConfig", serverConfig);
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
}
