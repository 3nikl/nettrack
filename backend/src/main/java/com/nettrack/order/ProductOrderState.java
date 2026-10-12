package com.nettrack.order;

import java.util.Set;

// TMF622 order lifecycle. Constant names match the TMF spec exactly ("inProgress", not IN_PROGRESS)
// so JSON, query params, and the database all use the same values with no mapping layer.
// (Simplified: TMF also defines assessingCancellation/pendingCancellation; we cancel directly.)
public enum ProductOrderState {
    acknowledged, rejected, pending, held, inProgress, cancelled, completed, failed, partial;

    public boolean canMoveTo(ProductOrderState target) {
        return switch (this) {
            case acknowledged -> Set.of(inProgress, rejected, cancelled).contains(target);
            case inProgress   -> Set.of(completed, failed, partial, held, pending, cancelled).contains(target);
            case pending, held -> Set.of(inProgress, cancelled).contains(target);
            default -> false; // rejected, cancelled, completed, failed, partial are final
        };
    }

    // Order amendment: partners may change an order until it reaches a final state.
    // A real system often has an earlier "point of no return" (e.g. a technician is already dispatched).
    public boolean isAmendable() {
        return this == acknowledged || this == pending || this == held || this == inProgress;
    }
}
