package com.nettrack.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nettrack.common.NotFoundException;
import com.nettrack.common.PreconditionFailedException;
import com.nettrack.order.TmfDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductOrderService {

    private final ProductOrderRepository orders;
    private final OrderEventRepository events;
    private final ObjectMapper json;

    public ProductOrderService(ProductOrderRepository orders, OrderEventRepository events, ObjectMapper json) {
        this.orders = orders;
        this.events = events;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<ProductOrder> list(ProductOrderState state) {
        return state == null ? orders.findAllByOrderByOrderDateDesc() : orders.findByStateOrderByOrderDateDesc(state);
    }

    @Transactional(readOnly = true)
    public ProductOrder get(String id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("ProductOrder " + id + " not found"));
    }

    // SAFE RETRIES: partners retry on timeouts. (partnerId, externalId) identifies "the same order",
    // so a retry returns the original order instead of creating a duplicate.
    // The UNIQUE constraint in V2 catches the rare case of two retries arriving at the same instant.
    @Transactional
    public CreateResult create(ProductOrderCreate req) {
        String partnerId = req.relatedParty().stream()
                .filter(p -> "Partner".equalsIgnoreCase(p.role()))
                .map(RelatedParty::id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("relatedParty with role 'Partner' is required"));

        var existing = orders.findByPartnerIdAndExternalId(partnerId, req.externalId());
        if (existing.isPresent()) {
            return new CreateResult(existing.get(), false);
        }

        requireUniqueItemIds(req.productOrderItem());
        var order = new ProductOrder(req.externalId(), partnerId, req.description(), req.category(),
                req.requestedCompletionDate());
        req.productOrderItem().forEach(i -> order.addItem(toEntity(i)));

        var saved = orders.saveAndFlush(order);
        record(saved, "ProductOrderCreateEvent");
        return new CreateResult(saved, true);
    }

    // ORDER AMENDMENT (PATCH). expectedVersion comes from the client's If-Match header:
    // "only apply my change if the order is still the version I looked at".
    @Transactional
    public ProductOrder update(String id, ProductOrderUpdate req, Long expectedVersion) {
        var order = get(id);
        if (expectedVersion != null && !expectedVersion.equals(order.getVersion())) {
            throw new PreconditionFailedException("ProductOrder " + id + " has changed (version "
                    + order.getVersion() + "); re-read it and retry");
        }

        boolean amendsAttributes = req.description() != null || req.requestedCompletionDate() != null
                || req.productOrderItem() != null;
        if (!amendsAttributes && req.state() == null) {
            throw new IllegalArgumentException("Nothing to update");
        }

        if (amendsAttributes) {
            if (!order.getState().isAmendable()) {
                throw new IllegalStateException("ProductOrder " + id + " is " + order.getState()
                        + " and can no longer be amended");
            }
            if (req.description() != null) order.setDescription(req.description());
            if (req.requestedCompletionDate() != null) order.setRequestedCompletionDate(req.requestedCompletionDate());
            if (req.productOrderItem() != null) mergeItems(order, req.productOrderItem());
            order.touch();
            record(order, "ProductOrderAttributeValueChangeEvent");
        }

        if (req.state() != null && req.state() != order.getState()) {
            transition(order, req.state());
        }
        return orders.saveAndFlush(order);
    }

    @Transactional
    public ProductOrder cancel(String id) {
        var order = get(id);
        transition(order, ProductOrderState.cancelled);
        return orders.saveAndFlush(order);
    }

    private void transition(ProductOrder order, ProductOrderState target) {
        if (!order.getState().canMoveTo(target)) {
            throw new IllegalStateException("ProductOrder " + order.getId() + " cannot move from "
                    + order.getState() + " to " + target);
        }
        order.moveTo(target);
        record(order, "ProductOrderStateChangeEvent");
    }

    // Update items IN PLACE by item id instead of "delete all, insert all".
    // Hibernate runs INSERTs before DELETEs in a flush, so replace-all would hit the
    // UNIQUE (order_id, item_id) constraint. Merging also keeps item history stable.
    private void mergeItems(ProductOrder order, List<ProductOrderItemDto> incoming) {
        requireUniqueItemIds(incoming);
        Map<String, ProductOrderItem> current = order.getItems().stream()
                .collect(Collectors.toMap(ProductOrderItem::getItemId, Function.identity()));
        Set<String> keep = new HashSet<>();
        for (var dto : incoming) {
            keep.add(dto.id());
            var item = current.get(dto.id());
            if (item == null) {
                order.addItem(toEntity(dto));
            } else {
                item.update(dto.action(), dto.productOffering().id(), dto.productOffering().name(),
                        dto.quantity(), dto.place() == null ? null : dto.place().id());
            }
        }
        current.values().stream().filter(i -> !keep.contains(i.getItemId())).toList()
                .forEach(order::removeItem);
    }

    private static void requireUniqueItemIds(List<ProductOrderItemDto> items) {
        var ids = new HashSet<String>();
        for (var i : items) {
            if (!ids.add(i.id())) throw new IllegalArgumentException("Duplicate productOrderItem id " + i.id());
        }
    }

    private static ProductOrderItem toEntity(ProductOrderItemDto i) {
        return new ProductOrderItem(i.id(), i.action(), i.productOffering().id(), i.productOffering().name(),
                i.quantity(), i.place() == null ? null : i.place().id());
    }

    // Written in the same transaction as the order change (transactional outbox).
    private void record(ProductOrder order, String eventType) {
        try {
            var body = Map.of(
                    "eventType", eventType,
                    "eventTime", OffsetDateTime.now().toString(),
                    "event", Map.of("productOrder", ProductOrderResponse.from(order)));
            events.save(new OrderEvent(order.getId(), eventType, json.writeValueAsString(body)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + eventType, e);
        }
    }

    public record CreateResult(ProductOrder order, boolean created) {}
}
