package vn.vshop.modules.order.model;

import java.util.List;
import java.util.Optional;

/**
 * Banks a buyer can receive a refund in. {@code bin} is the six-digit identification number the
 * interbank transfer needs (NAPAS). The list is deliberately short (the largest banks) and must be
 * checked against the payment provider's current list before real payouts are switched on.
 */
public final class VietnamBanks {
    public record Bank(String code, String name, String bin) {}

    private static final List<Bank> ALL =
            List.of(
                    new Bank("VCB", "Vietcombank", "970436"),
                    new Bank("CTG", "VietinBank", "970415"),
                    new Bank("BIDV", "BIDV", "970418"),
                    new Bank("AGR", "Agribank", "970405"),
                    new Bank("TCB", "Techcombank", "970407"),
                    new Bank("MB", "MB Bank", "970422"),
                    new Bank("ACB", "ACB", "970416"),
                    new Bank("VPB", "VPBank", "970432"),
                    new Bank("TPB", "TPBank", "970423"),
                    new Bank("STB", "Sacombank", "970403"),
                    new Bank("SHB", "SHB", "970443"),
                    new Bank("HDB", "HDBank", "970437"),
                    new Bank("VIB", "VIB", "970441"),
                    new Bank("OCB", "OCB", "970448"),
                    new Bank("MSB", "MSB", "970426"),
                    new Bank("SSB", "SeABank", "970440"),
                    new Bank("EIB", "Eximbank", "970431"),
                    new Bank("LPB", "LPBank", "970449"));

    private VietnamBanks() {}

    public static List<Bank> all() {
        return ALL;
    }

    public static Optional<Bank> byCode(String code) {
        return ALL.stream().filter(b -> b.code().equalsIgnoreCase(code)).findFirst();
    }
}
