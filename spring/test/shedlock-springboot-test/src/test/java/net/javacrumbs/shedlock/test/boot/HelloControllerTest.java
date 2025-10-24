package net.javacrumbs.shedlock.test.boot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HelloControllerTest {

    private final MockMvc mockMvc =
            MockMvcBuilders.standaloneSetup(new HelloController()).build();

    @Test
    void shouldCallController() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }
}
