package com.xyzbank.migration.annualreports.application.ports;

import com.xyzbank.migration.annualreports.domain.AnnualMovement;

import java.util.ArrayList;
import java.util.List;

public class InMemoryAnnualMovementStore implements AnnualMovementStore {

    private final List<AnnualMovement> written = new ArrayList<>();

    @Override
    public synchronized void write(List<AnnualMovement> movements) {
        written.addAll(movements);
    }

    public synchronized List<AnnualMovement> written() {
        return List.copyOf(written);
    }
}
