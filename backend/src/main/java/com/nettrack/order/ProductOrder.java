package com.nettrack.order;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product_orders")
public class ProductOrder {

    @Id
    private String id;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "partner_id", nullable = false)
    private String partnerId;

    private String description;
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductOrderState state;

    @Column(name = "order_date", nullable = false, updatable = false)
    private OffsetDateTime orderDate;

    @Column(name = "requested_completion_date")
    private OffsetDateTime requestedCompletionDate;

    @Column(name = "completion_date")
    private OffsetDateTime completionDate;

    @Column(name = "last_update", nullable = false)
    private OffsetDateTime lastUpdate;

    // Optimistic locking: Hibernate adds "WHERE version = ?" to every UPDATE and bumps it.
    // If two amendments race, the second one fails instead of silently overwriting the first.
    @Version
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("itemId")
    private List<ProductOrderItem> items = new ArrayList<>();

    protected ProductOrder() {}

    public ProductOrder(String externalId, String partnerId, String description, String category,
                        OffsetDateTime requestedCompletionDate) {
        this.id = UUID.randomUUID().toString();
        this.externalId = externalId;
        this.partnerId = partnerId;
        this.description = description;
        this.category = category;
        this.requestedCompletionDate = requestedCompletionDate;
        this.state = ProductOrderState.acknowledged; // TMF: a newly accepted order starts "acknowledged"
        this.orderDate = this.lastUpdate = OffsetDateTime.now();
    }

    public void addItem(ProductOrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    public void removeItem(ProductOrderItem item) {
        items.remove(item);
    }

    public void moveTo(ProductOrderState target) {
        this.state = target;
        if (target == ProductOrderState.completed) {
            this.completionDate = OffsetDateTime.now();
        }
        touch();
    }

    // Always changes a column, so the @Version increments even when only child items changed
    public void touch() {
        this.lastUpdate = OffsetDateTime.now();
    }

    public String getId() { return id; }
    public String getExternalId() { return externalId; }
    public String getPartnerId() { return partnerId; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public ProductOrderState getState() { return state; }
    public OffsetDateTime getOrderDate() { return orderDate; }
    public OffsetDateTime getRequestedCompletionDate() { return requestedCompletionDate; }
    public OffsetDateTime getCompletionDate() { return completionDate; }
    public OffsetDateTime getLastUpdate() { return lastUpdate; }
    public Long getVersion() { return version; }
    public List<ProductOrderItem> getItems() { return items; }

    public void setDescription(String description) { this.description = description; }
    public void setRequestedCompletionDate(OffsetDateTime d) { this.requestedCompletionDate = d; }
}
