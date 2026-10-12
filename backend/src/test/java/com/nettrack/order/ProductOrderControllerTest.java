package com.nettrack.order;

import com.nettrack.common.PreconditionFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductOrderController.class)
class ProductOrderControllerTest {

    static final String BASE = "/tmf-api/productOrderingManagement/v4";
    static final String CREATE_BODY = """
            {"externalId":"PO-1",
             "relatedParty":[{"id":"PARTNER-ACME","role":"Partner"}],
             "productOrderItem":[{"id":"1","action":"add","quantity":1,
                                  "productOffering":{"id":"FIBER-1G","name":"Fiber 1 Gig"},
                                  "place":{"id":"SITE-77","@referredType":"GeographicSite"}}]}
            """;

    @Autowired MockMvc mvc;
    @MockBean ProductOrderService service;

    static ProductOrder order() {
        var o = new ProductOrder("PO-1", "PARTNER-ACME", null, null, null);
        o.addItem(new ProductOrderItem("1", ItemAction.add, "FIBER-1G", "Fiber 1 Gig", 1, "SITE-77"));
        return o;
    }

    @Test
    void createReturns201WithLocationAndTmfShape() throws Exception {
        var o = order();
        when(service.create(any())).thenReturn(new ProductOrderService.CreateResult(o, true));

        mvc.perform(post(BASE + "/productOrder").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
           .andExpect(status().isCreated())
           .andExpect(header().string("Location", BASE + "/productOrder/" + o.getId()))
           .andExpect(jsonPath("$['@type']").value("ProductOrder"))
           .andExpect(jsonPath("$.state").value("acknowledged"))
           .andExpect(jsonPath("$.productOrderItem[0].place['@referredType']").value("GeographicSite"));
    }

    @Test
    void retryReturns200() throws Exception {
        when(service.create(any())).thenReturn(new ProductOrderService.CreateResult(order(), false));
        mvc.perform(post(BASE + "/productOrder").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
           .andExpect(status().isOk());
    }

    @Test
    void orderWithoutItemsIsRejected() throws Exception {
        mvc.perform(post(BASE + "/productOrder").contentType(MediaType.APPLICATION_JSON).content("""
                {"externalId":"PO-1","relatedParty":[{"id":"P","role":"Partner"}],"productOrderItem":[]}
                """))
           .andExpect(status().isBadRequest());
    }

    @Test
    void unknownStateValueIsRejected() throws Exception {
        mvc.perform(get(BASE + "/productOrder").param("state", "shipped"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void amendingFinishedOrderReturns409() throws Exception {
        when(service.update(eq("abc"), any(), isNull()))
                .thenThrow(new IllegalStateException("ProductOrder abc is completed and can no longer be amended"));
        mvc.perform(patch(BASE + "/productOrder/abc").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"x\"}"))
           .andExpect(status().isConflict());
    }

    @Test
    void staleIfMatchReturns412() throws Exception {
        when(service.update(eq("abc"), any(), eq(3L))).thenThrow(new PreconditionFailedException("changed"));
        mvc.perform(patch(BASE + "/productOrder/abc").header("If-Match", "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"x\"}"))
           .andExpect(status().isPreconditionFailed());
    }
}
