package com.nettrack.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;

// JSON shapes that follow the TM Forum TMF622 v4 resource model (a practical subset).
// TMF conventions: camelCase fields, "@type" says what kind of resource it is,
// "href" is the resource's own URL, references to other resources are {id, name, @referredType}.
public final class TmfDtos {
    private TmfDtos() {}

    public static final String BASE_PATH = "/tmf-api/productOrderingManagement/v4";

    public record ProductOfferingRef(@NotBlank String id, String name) {}

    // Where the service is delivered. In TMF this references a TMF674 GeographicSite / GeographicAddress.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlaceRef(@NotBlank String id, String role,
                           @JsonProperty("@referredType") String referredType) {}

    public record RelatedParty(@NotBlank String id, @NotBlank String role) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProductOrderItemDto(
            @NotBlank @Size(max = 32) String id,
            @NotNull ItemAction action,
            @Min(1) @Max(1000) Integer quantity,
            @NotNull @Valid ProductOfferingRef productOffering,
            @Valid PlaceRef place) {}

    // POST body ("ProductOrder_Create" in the TMF spec)
    public record ProductOrderCreate(
            @NotBlank @Size(max = 100) String externalId,
            String description,
            @Size(max = 64) String category,
            OffsetDateTime requestedCompletionDate,
            @NotEmpty @Valid List<RelatedParty> relatedParty,
            @NotEmpty @Valid List<ProductOrderItemDto> productOrderItem) {}

    // PATCH body ("ProductOrder_Update"): every field optional, only what's sent changes
    public record ProductOrderUpdate(
            String description,
            OffsetDateTime requestedCompletionDate,
            @Valid List<ProductOrderItemDto> productOrderItem,
            ProductOrderState state) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProductOrderResponse(
            String id, String href, String externalId, String description, String category,
            ProductOrderState state, OffsetDateTime orderDate, OffsetDateTime requestedCompletionDate,
            OffsetDateTime completionDate, List<RelatedParty> relatedParty,
            List<ProductOrderItemDto> productOrderItem,
            @JsonProperty("@type") String type) {

        public static ProductOrderResponse from(ProductOrder o) {
            var items = o.getItems().stream().map(i -> new ProductOrderItemDto(
                    i.getItemId(), i.getAction(), i.getQuantity(),
                    new ProductOfferingRef(i.getProductOfferingId(), i.getProductOfferingName()),
                    i.getPlaceId() == null ? null : new PlaceRef(i.getPlaceId(), "installationAddress", "GeographicSite")
            )).toList();
            return new ProductOrderResponse(o.getId(), BASE_PATH + "/productOrder/" + o.getId(),
                    o.getExternalId(), o.getDescription(), o.getCategory(), o.getState(), o.getOrderDate(),
                    o.getRequestedCompletionDate(), o.getCompletionDate(),
                    List.of(new RelatedParty(o.getPartnerId(), "Partner")), items, "ProductOrder");
        }
    }

    public record OrderRef(@NotBlank String id) {}

    // TMF622 models cancellation as its own resource: POST /cancelProductOrder
    public record CancelProductOrderCreate(@NotNull @Valid OrderRef productOrder, String cancellationReason) {}

    public record CancelProductOrderResponse(
            String id, String state, OrderRef productOrder, String cancellationReason,
            OffsetDateTime effectiveCancellationDate, @JsonProperty("@type") String type) {}
}
