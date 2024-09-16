#include <iostream>
#include <string>
#include <stdexcept>

using namespace std;


class CargoTransportation {
private:
    string companyName;  
    double pricePerTon;        
    double totalTons;          

public:
    CargoTransportation(const string& name, double price, double tons)
        : companyName(name), pricePerTon(price), totalTons(tons) {}

    string getCompanyName() const {
        return companyName;
    }

    void setCompanyName(const string& name) {
        companyName = name;
    }

    double getTotalTons() const {
        return totalTons;
    }

    void setTotalTons(double tons) {
        if (tons >= 0) {
            totalTons = tons;
        } else {
            throw invalid_argument("Масса груза не может быть отрицательной");
        }
    }

    void setPricePerTon(double price) {
        if (price > 0) {
            pricePerTon = price;
        } else {
            throw invalid_argument("Цена за тонну должна быть больше нуля");
        }
    }

    double calculateTotalRevenue() const {
        return pricePerTon * totalTons;
    }

    void printInfo() const {
        cout << "Фирма: " << companyName << endl;
        cout << "Общая выручка: " << calculateTotalRevenue() << " руб." << endl;
    }
};

int main() {
    CargoTransportation cargo("Транспортная Компания", 5000.0, 120.5);

    cargo.printInfo();

    cargo.setPricePerTon(5500.0);

    cargo.setTotalTons(130.0);

    cargo.printInfo();

    return 0;
}
