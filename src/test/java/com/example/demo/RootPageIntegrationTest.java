package com.example.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RootPageIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void 루트_경로는_시작_페이지로_연결된다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void 시작_페이지가_가리키는_화면과_정적_파일이_모두_존재한다() throws Exception {
        for (String page : new String[] {"static/index.html", "static/ledger.html", "static/dashboard.html"}) {
            assertThat(new ClassPathResource(page).exists()).as(page).isTrue();
        }
    }
}
