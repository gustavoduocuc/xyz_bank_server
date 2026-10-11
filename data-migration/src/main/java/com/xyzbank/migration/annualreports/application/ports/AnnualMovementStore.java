package com.xyzbank.migration.annualreports.application.ports;

import com.xyzbank.migration.annualreports.domain.AnnualMovement;

import java.util.List;

public interface AnnualMovementStore {

    void write(List<AnnualMovement> movements);
}
