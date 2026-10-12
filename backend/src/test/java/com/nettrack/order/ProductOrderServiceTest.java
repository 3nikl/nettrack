package com.nettrack.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nettrack.common.PreconditionFailedException;
import com.nettrack.order.TmfDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductOrderServiceTest {

    private ProductOrderRepository orders;
    private OrderEventRepository events;
    private ProductOrderService service;

    @BeforeEach
    void setUp() {
        orders = mock(ProductOrderRepository.class);
        events = mock(OrderEventRepository.class);
        service = new ProductOrderService(orders, events, new ObjectMapper().findAndRegisterModules());
        when(orders.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    static ProductOrderItemDto item(String id, int qty) {
        return new ProductOrderItemDto(id, ItemAction.add, qty, new ProductOfferingRef("FIBER-1G", "Fiber 1 Gig"),
                new PlaceRef("SITE-77", "installationAddress", "GeographicSite"));
    }

    static ProductOrderCreate createReq(String externalId) {
        return new ProductOrderCreate(externalId, "New fiber for branch office", "B2B", null,
                List.of(new RelatedParty("PARTNER-ACME", "Partner")), List.of(item("1", 1)));
    }

    static ProductOrder existingOrder(ProductOrderState state) {
        var o = new ProductOrder("PO-1", "PARTNER-ACME", "desc", "B2B", null);
        o.addItem(new ProductOrderItem("1", ItemAction.add, "FIBER-1G", "Fiber 1 Gig", 1, "SITE-77"));
        if (state != ProductOrderState.acknowledged) o.moveTo(state);
        return o;
    }

    @Test
    void newOrderIsAcknowledgedAndEmitsCreateEvent() {
        when(orders.findByPartnerIdAndExternalId("PARTNER-ACME", "PO-1")).thenReturn(Optional.empty());

        var result = service.create(createReq("PO-1"));

        assertThat(result.created()).isTrue();
        assertThat(result.order().getState()).isEqualTo(ProductOrderState.acknowledged);
        assertThat(result.order().getItems()).hasSize(1);
        verify(events).save(argThat(e -> e.getEventType().equals("ProductOrderCreateEvent")));
    }

    @Test
    void retryWithSameExternalIdReturnsOriginalOrder() {
        var original = existingOrder(ProductOrderState.acknowledged);
        when(orders.findByPartnerIdAndExternalId("PARTNER-ACME", "PO-1")).thenReturn(Optional.of(original));

        var result = service.create(createReq("PO-1"));

        assertThat(result.created()).isFalse();
        assertThat(result.order()).isSameAs(original);
        verify(orders, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void orderWithoutPartnerIsRejected() {
        var req = new ProductOrderCreate("PO-2", null, null, null,
                List.of(new RelatedParty("C-9", "Customer")), List.of(item("1", 1)));
        assertThatThrownBy(() -> service.create(req)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicateItemIdsAreRejected() {
        var req = new ProductOrderCreate("PO-3", null, null, null,
                List.of(new RelatedParty("PARTNER-ACME", "Partner")), List.of(item("1", 1), item("1", 2)));
        when(orders.findByPartnerIdAndExternalId(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(req)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void amendmentUpdatesExistingItemInPlaceAndAddsNewOne() {
        var order = existingOrder(ProductOrderState.inProgress);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        service.update(order.getId(), new ProductOrderUpdate(null, null, List.of(item("1", 3), item("2", 1)), null), null);

        assertThat(order.getItems()).extracting(ProductOrderItem::getItemId).containsExactly("1", "2");
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(3);
        verify(events).save(argThat(e -> e.getEventType().equals("ProductOrderAttributeValueChangeEvent")));
    }

    @Test
    void amendmentOmittingAnItemRemovesIt() {
        var order = existingOrder(ProductOrderState.acknowledged);
        order.addItem(new ProductOrderItem("2", ItemAction.add, "STATIC-IP", "Static IP", 1, null));
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        service.update(order.getId(), new ProductOrderUpdate(null, null, List.of(item("2", 1)), null), null);

        assertThat(order.getItems()).extracting(ProductOrderItem::getItemId).containsExactly("2");
    }

    @Test
    void completedOrderCannotBeAmended() {
        var order = existingOrder(ProductOrderState.inProgress);
        order.moveTo(ProductOrderState.completed);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.update(order.getId(),
                new ProductOrderUpdate("change", null, null, null), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("can no longer be amended");
    }

    @Test
    void staleIfMatchIsRejected() {
        var order = existingOrder(ProductOrderState.acknowledged); // version is null until saved; client sends 5
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.update(order.getId(),
                new ProductOrderUpdate("change", null, null, null), 5L))
                .isInstanceOf(PreconditionFailedException.class);
    }

    @Test
    void illegalStateTransitionIsRejected() {
        var order = existingOrder(ProductOrderState.acknowledged);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.update(order.getId(),
                new ProductOrderUpdate(null, null, null, ProductOrderState.completed), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot move from acknowledged to completed");
    }

    @Test
    void cancelInProgressOrder() {
        var order = existingOrder(ProductOrderState.inProgress);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        var cancelled = service.cancel(order.getId());

        assertThat(cancelled.getState()).isEqualTo(ProductOrderState.cancelled);
    }

    @Test
    void stateMachineFinalStatesGoNowhere() {
        for (var s : List.of(ProductOrderState.completed, ProductOrderState.cancelled, ProductOrderState.rejected)) {
            for (var t : ProductOrderState.values()) {
                assertThat(s.canMoveTo(t)).as(s + " -> " + t).isFalse();
            }
        }
    }
}
