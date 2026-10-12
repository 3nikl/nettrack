package com.nettrack.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

// @EntityGraph loads the order AND its items in one query (a JOIN).
// Without it, items are "lazy": Hibernate fetches them only when touched, and if that happens after
// the transaction has ended (e.g. while the controller builds JSON) you get LazyInitializationException.
// It also avoids N+1 queries on the list endpoint (1 query for orders + 1 per order for items).
public interface ProductOrderRepository extends JpaRepository<ProductOrder, String> {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<ProductOrder> findById(String id);

    @EntityGraph(attributePaths = "items")
    Optional<ProductOrder> findByPartnerIdAndExternalId(String partnerId, String externalId);

    @EntityGraph(attributePaths = "items")
    List<ProductOrder> findByStateOrderByOrderDateDesc(ProductOrderState state);

    @EntityGraph(attributePaths = "items")
    List<ProductOrder> findAllByOrderByOrderDateDesc();
}
