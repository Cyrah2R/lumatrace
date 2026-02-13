package org.lumatrace.cloud;

import org.junit.jupiter.api.Test;
import org.lumatrace.cloud.dto.PhotoRegistrationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

// Importaciones estáticas para MockMvc
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest
@AutoConfigureMockMvc
class RegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testFullRegistrationFlow() throws Exception {
        System.out.println("🌐 TEST: Testing API /register endpoint...");

        PhotoRegistrationRequest request = new PhotoRegistrationRequest(
                "junit-user",
                "hash-123",
                "Test-Device",
                40.0,
                -3.0
        );

        String jsonRequest = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/photos/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoId", notNullValue()))
                .andExpect(jsonPath("$.watermarkSeed", notNullValue())) // Antes decia $.seed
                .andExpect(jsonPath("$.canonicalHash", notNullValue())); // Antes decia $.hash

        System.out.println("✅ TEST PASSED: Registration API works.");
    }

    @Test
    void testVerificationWithInvalidImage() throws Exception {
        System.out.println("🌐 TEST: Testing API /verify with fake image...");

        // 1. Crear una imagen falsa (byte array vacio/corrupto)
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "test.jpg",
                "image/jpeg",
                new byte[100]
        );

        // 2. Llamar a /verify
        mockMvc.perform(multipart("/api/v1/photos/verify")
                        .file(image)
                        .param("userId", "user-1")
                        .param("photoId", "photo-1"))
                .andExpect(status().is4xxClientError());

        System.out.println("✅ TEST PASSED: Verify API handles bad input gracefully.");
    }
}