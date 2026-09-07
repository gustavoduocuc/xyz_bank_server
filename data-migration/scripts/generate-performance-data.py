#!/usr/bin/env python3
"""Generate large synthetic CSVs under data/performance/ for batch performance demos."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "data" / "performance"

TRANSACTION_ROWS = 50_000
INTEREST_ROWS = 10_000
ANNUAL_ROWS = 20_000


def write_transactions(path: Path) -> None:
    lines = ["id,fecha,monto,tipo"]
    for index in range(1, TRANSACTION_ROWS + 1):
        day = (index % 28) + 1
        amount = 100 + (index % 5000)
        tipo = "debito" if index % 2 == 0 else "credito"
        if index % 200 == 0:
            lines.append(f"{index},2024-01-{day:02d},-10,{tipo}")
        elif index % 250 == 0:
            lines.append(f"{index},2024-01-{day:02d},,{tipo}")
        elif index % 300 == 0:
            lines.append(f"{index},2024-01-{day:02d},{amount},invalid")
        elif index % 400 == 0:
            lines.append(f"{index},2024/01/{day:02d},{amount},{tipo}")
        else:
            lines.append(f"{index},2024-01-{day:02d},{amount},{tipo}")
        if index % 500 == 0:
            previous = lines[-2]
            lines.append(previous)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_interests(path: Path) -> None:
    lines = ["cuenta_id,nombre,saldo,edad,tipo"]
    types = ["ahorro", "prestamo", "hipoteca"]
    for index in range(1, INTEREST_ROWS + 1):
        account_id = 1000 + index
        name = f"Customer {index}"
        balance = 1000 + (index % 9000)
        age = 18 + (index % 70)
        tipo = types[index % 3]
        if index % 150 == 0:
            lines.append(f"{account_id},{name},,{age},{tipo}")
        elif index % 175 == 0:
            lines.append(f"{account_id},{name},{balance},,{tipo}")
        elif index % 200 == 0:
            lines.append(f"{account_id},{name},{balance},{age},invalid")
        else:
            lines.append(f"{account_id},{name},{balance},{age},{tipo}")
        if index % 300 == 0:
            lines.append(f"{account_id - 1},Duplicate {index},{balance},{age},{tipo}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_annual(path: Path) -> None:
    lines = ["cuenta_id,fecha,transaccion,monto,descripcion"]
    types = ["deposito", "retiro", "compra"]
    for index in range(1, ANNUAL_ROWS + 1):
        account_id = 1000 + (index % 2000)
        month = (index % 12) + 1
        day = (index % 28) + 1
        tipo = types[index % 3]
        amount = 100 + (index % 3000)
        if tipo != "deposito":
            amount = -amount
        if index % 180 == 0:
            lines.append(f"{account_id},2024-{month:02d}-{day:02d},deposito,0,Zero deposit")
        elif index % 220 == 0:
            lines.append(f"{account_id},2024/{month:02d}/{day:02d},{tipo},{amount},Slash date")
        else:
            lines.append(
                f"{account_id},2024-{month:02d}-{day:02d},{tipo},{amount},Movement {index}"
            )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    write_transactions(OUTPUT / "transacciones.csv")
    write_interests(OUTPUT / "intereses.csv")
    write_annual(OUTPUT / "cuentas_anuales.csv")
    print(f"Generated performance CSVs in {OUTPUT}")
    print(f"  transacciones.csv: {TRANSACTION_ROWS}+ rows")
    print(f"  intereses.csv: {INTEREST_ROWS}+ rows")
    print(f"  cuentas_anuales.csv: {ANNUAL_ROWS}+ rows")


if __name__ == "__main__":
    main()
