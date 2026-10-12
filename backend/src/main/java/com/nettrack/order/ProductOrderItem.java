package com.nettrack.order;

import jakarta.persistence.*;

// One line on the order, e.g. "add 1 x Fiber 1 Gig at site SITE-77".
@Entity
@Table(name = "product_order_items")
public class ProductOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pk;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private ProductOrder order;

    @Column(name = "item_id", nullable = false)
    private String itemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemAction action;

    @Column(name = "product_offering_id", nullable = false)
    private String productOfferingId;

    @Column(name = "product_offering_name")
    private String productOfferingName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "place_id")
    private String placeId;

    protected ProductOrderItem() {}

    public ProductOrderItem(String itemId, ItemAction action, String productOfferingId,
                            String productOfferingName, Integer quantity, String placeId) {
        this.itemId = itemId;
        update(action, productOfferingId, productOfferingName, quantity, placeId);
    }

    public void update(ItemAction action, String productOfferingId, String productOfferingName,
                       Integer quantity, String placeId) {
        this.action = action;
        this.productOfferingId = productOfferingId;
        this.productOfferingName = productOfferingName;
        this.quantity = quantity == null ? 1 : quantity;
        this.placeId = placeId;
    }

    void setOrder(ProductOrder order) { this.order = order; }

    public String getItemId() { return itemId; }
    public ItemAction getAction() { return action; }
    public String getProductOfferingId() { return productOfferingId; }
    public String getProductOfferingName() { return productOfferingName; }
    public Integer getQuantity() { return quantity; }
    public String getPlaceId() { return placeId; }
}
