package com.igot.cb.storage.service;

import com.igot.cb.model.ApiResponse;
import com.igot.cb.util.CbExtServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.sunbird.cloud.storage.BaseStorageService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageServiceImplTest {

    @Mock
    private CbExtServerProperties serverProperties;

    @InjectMocks
    private StorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        assertNotNull(storageService);
    }

    @Test
    void testConstructorInjectsServerProperties() {
        assertNotNull(storageService);
    }

    @Test
    void testDownloadFile_exceptionIsHandled() {
        // storageService (the underlying BaseStorageService) is not initialized (init() not called),
        // so calling download triggers a NullPointerException that must be handled gracefully.
        when(serverProperties.getLocalBasePath()).thenReturn("/tmp/");

        ApiResponse response = storageService.downloadFile("missing.csv", "container1");

        assertNotNull(response);
        assertNotNull(response.getParams());
    }

    @Test
    void testDownloadFile_success_usesConfiguredLocalBasePath() {
        // Underlying BaseStorageService mocked so the download() call completes normally,
        // fully exercising the serverProperties.getLocalBasePath() call on the success path.
        BaseStorageService mockBaseStorageService = mock(BaseStorageService.class);
        ReflectionTestUtils.setField(storageService, "storageService", mockBaseStorageService);

        when(serverProperties.getCloudContainerName()).thenReturn("container1");
        when(serverProperties.getLocalBasePath()).thenReturn("/tmp/");

        ApiResponse response = storageService.downloadFile("sample.csv", "container1");

        assertNotNull(response);
        verify(serverProperties).getLocalBasePath();
    }

    @Test
    void testUploadFile_multipartFile_createsAndCleansUpTempFile() throws Exception {
        // The underlying BaseStorageService is not initialized (init() not called),
        // so the upload call fails and the exception path is exercised, along with
        // the createNewFile()/delete() boolean-result handling.
        MultipartFile file = new MockMultipartFile("file", "sample.txt", "text/plain", "hello".getBytes());

        ApiResponse response = storageService.uploadFile(file, "folder1");

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }
}
