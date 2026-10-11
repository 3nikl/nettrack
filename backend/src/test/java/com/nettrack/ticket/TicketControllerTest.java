package com.nettrack.ticket;

import com.nettrack.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// SLICE TEST: starts ONLY the web layer (controller + JSON + validation + error handler).
// MockMvc sends fake HTTP requests — no real server, no database.
@WebMvcTest(TicketController.class)
class TicketControllerTest {

    @Autowired MockMvc mvc;
    @MockBean TicketService service;

    @Test
    void postReturns201ForNewTicket() throws Exception {
        var t = new Ticket("ALM-7", "Tower down", null, "NJ-MIDDLETOWN", Severity.HIGH);
        when(service.create(any())).thenReturn(new TicketService.CreateResult(t, true));

        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content("""
                {"alarmId":"ALM-7","title":"Tower down","region":"NJ-MIDDLETOWN","severity":"HIGH"}
                """))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.title").value("Tower down"))
           .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void postReturns200ForDuplicateAlarm() throws Exception {
        var t = new Ticket("ALM-7", "Tower down", null, "NJ-MIDDLETOWN", Severity.HIGH);
        when(service.create(any())).thenReturn(new TicketService.CreateResult(t, false));

        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content("""
                {"alarmId":"ALM-7","title":"Tower down","region":"NJ-MIDDLETOWN","severity":"HIGH"}
                """))
           .andExpect(status().isOk());
    }

    @Test
    void missingTitleReturns400() throws Exception {
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content("""
                {"region":"NJ","severity":"LOW"}
                """))
           .andExpect(status().isBadRequest());
    }

    @Test
    void unknownTicketReturns404() throws Exception {
        when(service.get(42L)).thenThrow(new NotFoundException("Ticket 42 not found"));
        mvc.perform(get("/api/tickets/42")).andExpect(status().isNotFound());
    }
}
