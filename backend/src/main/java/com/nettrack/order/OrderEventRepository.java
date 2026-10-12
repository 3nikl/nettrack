package com.nettrack.order;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
    List<OrderEvent> findTop100ByPublishedAtIsNullOrderByIdAsc();
    List<OrderEvent> findByOrderIdOrderByIdAsc(String orderId);
}
