package com.jonathansoriano.enterprisedevgroupproject.config;

import com.jonathansoriano.enterprisedevgroupproject.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "campusbridge.monitoring.enabled=true",
        "campusbridge.monitoring.password=disposable-test-password",
        "management.metrics.enable.all=true"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class MonitoringSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void scrapeRequiresItsOwnCredential() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").with(httpBasic("prometheus", "incorrect")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").with(jwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/prometheus")
                        .with(httpBasic("prometheus", "disposable-test-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }

    @Test
    void scrapeCredentialCannotReadStudentDataOrOtherActuatorEndpoints() throws Exception {
        for (String path : new String[]{"/api/schools", "/student", "/actuator/metrics", "/actuator/info"}) {
            mockMvc.perform(get(path).with(httpBasic("prometheus", "disposable-test-password")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/actuator/prometheus")
                        .with(httpBasic("prometheus", "disposable-test-password")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousHealthRemainsAvailableWithoutDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }
}
