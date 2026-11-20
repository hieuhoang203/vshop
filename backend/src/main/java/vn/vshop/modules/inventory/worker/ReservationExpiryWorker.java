package vn.vshop.modules.inventory.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.vshop.modules.inventory.service.ReservationService;

/** Safety net: gives back stock of HELD reservations whose deadline has passed. */
@Component
@ConditionalOnProperty(name = "vshop.worker.enabled", havingValue = "true")
public class ReservationExpiryWorker {
    private final ReservationService reservations;

    public ReservationExpiryWorker(ReservationService reservations) {
        this.reservations = reservations;
    }

    @Scheduled(fixedDelayString = "${vshop.worker.reservation-expiry-ms:30000}")
    public void expire() {
        reservations.expireDue(100);
    }
}
