class CargoTransportation:
    def __init__(self, company_name, price_per_ton, total_tons):
        self._company_name = company_name
        self._price_per_ton = price_per_ton
        self._total_tons = total_tons

    @property
    def company_name(self):
        return self._company_name

    @company_name.setter
    def company_name(self, name):
        self._company_name = name

    @property
    def total_tons(self):
        return self._total_tons

    @total_tons.setter
    def total_tons(self, tons):
        if tons >= 0:
            self._total_tons = tons
        else:
            raise ValueError("Масса груза не может быть отрицательной")

    @property
    def price_per_ton(self):
        raise AttributeError("Невозможно прочитать это свойство")

    @price_per_ton.setter
    def price_per_ton(self, price):
        if price > 0:
            self._price_per_ton = price
        else:
            raise ValueError("Цена за тонну должна быть больше нуля")

    def calculate_total_revenue(self):
        return self._price_per_ton * self._total_tons

    def print_info(self):
        print(f"Фирма: {self._company_name}")
        print(f"Общая выручка: {self.calculate_total_revenue()} руб.")


cargo = CargoTransportation("Транспортная Компания", 5000.0, 120.5)

cargo.print_info()

cargo.price_per_ton = 5500.0

cargo.total_tons = 130.0

cargo.print_info()
