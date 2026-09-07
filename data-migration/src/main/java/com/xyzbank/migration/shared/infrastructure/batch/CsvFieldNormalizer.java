package com.xyzbank.migration.shared.infrastructure.batch;

import com.xyzbank.migration.shared.domain.DomainError;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CsvFieldNormalizer {

    private CsvFieldNormalizer() {
    }

    public static String text(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static Double amount(String raw) {
        String compact = compactNumber(raw);
        if (compact == null) {
            return null;
        }
        try {
            return scaleAmount(new BigDecimal(toCanonicalDecimal(compact)).doubleValue());
        } catch (NumberFormatException exception) {
            throw DomainError.validation("Invalid amount format: " + raw);
        }
    }

    public static Double scaleAmount(Double amount) {
        if (amount == null) {
            return null;
        }
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static Integer integer(String raw) {
        String compact = compactNumber(raw);
        if (compact == null) {
            return null;
        }
        try {
            return Integer.valueOf(compact);
        } catch (NumberFormatException exception) {
            throw DomainError.validation("Invalid integer format: " + raw);
        }
    }

    private static String compactNumber(String raw) {
        String text = text(raw);
        if (text == null) {
            return null;
        }
        return text.replace(" ", "").replace("\u00A0", "");
    }

    // Convention: if both separators appear, the rightmost is the decimal mark.
    // Comma alone with <=2 digits after is decimal (1500,50); more digits means thousands (1,500).
    // Dot alone keeps Java/BigDecimal rules (1.500 => 1.50), so prefer explicit decimals in CSV.
    private static String toCanonicalDecimal(String compact) {
        int lastDot = compact.lastIndexOf('.');
        int lastComma = compact.lastIndexOf(',');
        if (lastDot >= 0 && lastComma >= 0) {
            if (lastComma > lastDot) {
                return compact.replace(".", "").replace(',', '.');
            }
            return compact.replace(",", "");
        }
        if (lastComma >= 0) {
            int digitsAfterComma = compact.length() - lastComma - 1;
            if (digitsAfterComma <= 2) {
                return compact.replace(',', '.');
            }
            return compact.replace(",", "");
        }
        return compact;
    }
}
