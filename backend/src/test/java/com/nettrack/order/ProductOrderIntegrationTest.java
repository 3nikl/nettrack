package com.nettrack.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// INTEGRATION TEST: the whole app + Flyway + Hibernate + a real Postgres (in Docker).
// This is the test that caught the LazyInitializationException the mocked unit tests could not see.
@SpringBootTest(properties = "app.outbox.poll-ms=600000")
@AutoConfigureMockMvc
@Testcontainers
class ProductOrderIntegrationTest {

    @Container
    @ServiceConnection // Spring points the datasource at this container automatically
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final String BASE = "/tmf-api/productOrderingManagement/v4";
    static final String BODY = """
            {"externalId":"IT-PO-1",
             "relatedParty":[{"id":"PARTNER-IT","role":"Partner"}],
             "productOrderItem":[{"id":"1","action":"add","quantity":1,
                                  "productOffering":{"id":"FIBER-1G"}}]}
            """;

    @Autowired MockMvc mvc;
    @Autowired ProductOrderRepository orders;
    @Autowired OrderEventRepository events;

    @Test
    void retryReturnsOriginalOrderAndGetWorksOutsideTransaction() throws Exception {
        var first = mvc.perform(post(BASE + "/productOrder").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var id = com.jayway.jsonpath.JsonPath.read(first, "$.id").toString();

        mvc.perform(post(BASE + "/productOrder").contentType(MediaType.APPLICATION_JSON).content(BODY))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(id))
           .andExpect(jsonPath("$.productOrderItem[0].productOffering.id").value("FIBER-1G"));

        mvc.perform(get(BASE + "/productOrder/" + id))
           .andExpect(status().isOk())
           .andExpect(header().string("ETag", "\"0\""));

        mvc.perform(get(BASE + "/productOrder").param("state", "acknowledged"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].productOrderItem").isArray());

        assertThat(orders.count()).isEqualTo(1);
        assertThat(events.findByOrderIdOrderByIdAsc(id)).hasSize(1); // one create event, not two
    }
}
