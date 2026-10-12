package com.nettrack.order;

import com.nettrack.order.TmfDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// TMF622 Product Ordering Management API v4 (subset).
@RestController
@RequestMapping(TmfDtos.BASE_PATH)
public class ProductOrderController {

    private final ProductOrderService service;

    public ProductOrderController(ProductOrderService service) {
        this.service = service;
    }

    @GetMapping("/productOrder")
    public List<ProductOrderResponse> list(@RequestParam(required = false) ProductOrderState state) {
        return service.list(state).stream().map(ProductOrderResponse::from).toList();
    }

    @GetMapping("/productOrder/{id}")
    public ResponseEntity<ProductOrderResponse> get(@PathVariable String id) {
        var order = service.get(id);
        return ResponseEntity.ok().eTag(etag(order)).body(ProductOrderResponse.from(order));
    }

    // 201 + Location for a new order; 200 with the original order when a partner retries
    @PostMapping("/productOrder")
    public ResponseEntity<ProductOrderResponse> create(@Valid @RequestBody ProductOrderCreate req) {
        var result = service.create(req);
        var body = ProductOrderResponse.from(result.order());
        var builder = result.created()
                ? ResponseEntity.created(URI.create(body.href()))
                : ResponseEntity.status(HttpStatus.OK);
        return builder.eTag(etag(result.order())).body(body);
    }

    // Amendment and/or state change. If-Match: "<version>" makes the update conditional (412 if stale).
    @PatchMapping("/productOrder/{id}")
    public ResponseEntity<ProductOrderResponse> update(@PathVariable String id,
                                                       @Valid @RequestBody ProductOrderUpdate req,
                                                       @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        var order = service.update(id, req, parseVersion(ifMatch));
        return ResponseEntity.ok().eTag(etag(order)).body(ProductOrderResponse.from(order));
    }

    @PostMapping("/cancelProductOrder")
    @ResponseStatus(HttpStatus.CREATED)
    public CancelProductOrderResponse cancel(@Valid @RequestBody CancelProductOrderCreate req) {
        var order = service.cancel(req.productOrder().id());
        return new CancelProductOrderResponse(UUID.randomUUID().toString(), "done",
                new OrderRef(order.getId()), req.cancellationReason(), OffsetDateTime.now(), "CancelProductOrder");
    }

    private static String etag(ProductOrder o) {
        return "\"" + o.getVersion() + "\"";
    }

    private static Long parseVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) return null;
        try {
            return Long.valueOf(ifMatch.replace("W/", "").replace("\"", "").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("If-Match must be an ETag returned by this API");
        }
    }
}
