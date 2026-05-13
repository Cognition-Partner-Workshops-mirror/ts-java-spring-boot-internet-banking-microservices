# 🚗 Ride Sharing Application

A full-featured ride-sharing platform built with **Java 21** and **Spring Boot 3.2**, similar to Ola and Uber. This application provides ride booking, driver matching, real-time ride tracking, fare calculation, payment processing, and a rating system.

## Features

### Core Features
- **User Management** — Rider & Driver registration, JWT-based authentication, profile management
- **Ride Booking** — Request rides with pickup/drop-off locations, vehicle type selection
- **Driver Matching** — Haversine-based nearby driver search within configurable radius
- **Ride Lifecycle** — Full status tracking: Requested → Accepted → En Route → Arrived → In Progress → Completed
- **OTP Verification** — Secure ride start with 4-digit OTP verification
- **Fare Calculation** — Distance-based pricing with vehicle type multipliers and surge pricing support
- **Payment Processing** — Simulated payment with multiple methods (Cash, Card, UPI, Wallet)
- **Rating System** — Mutual ratings (1-5 stars) between riders and drivers after ride completion

### Technical Features
- JWT-based stateless authentication with Spring Security
- Role-based access control (RIDER, DRIVER, ADMIN)
- OpenAPI 3.0 / Swagger UI documentation
- H2 in-memory database (dev) / MySQL support (production)
- Bean validation on all request DTOs
- Global exception handling with consistent API responses
- Sample data initialization for development

## Tech Stack

| Component       | Technology                    |
|----------------|-------------------------------|
| Language        | Java 21                       |
| Framework       | Spring Boot 3.2.5             |
| Security        | Spring Security + JWT (jjwt)  |
| Database        | H2 (dev) / MySQL (prod)       |
| ORM             | Spring Data JPA / Hibernate   |
| API Docs        | SpringDoc OpenAPI 3.0         |
| Build Tool      | Maven                         |
| Testing         | JUnit 5 + MockMvc             |

## Getting Started

### Prerequisites
- Java 21+
- Maven 3.8+

### Build & Run

```bash
# Clone the repository
git clone <repo-url>
cd ride-sharing-app

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The application starts at **http://localhost:8080**

### Access Points
| Resource        | URL                                    |
|----------------|----------------------------------------|
| Swagger UI      | http://localhost:8080/swagger-ui.html  |
| API Docs (JSON) | http://localhost:8080/api-docs         |
| H2 Console      | http://localhost:8080/h2-console       |
| Health Check    | http://localhost:8080/actuator/health  |

### H2 Database Console
- JDBC URL: `jdbc:h2:mem:ridesharingdb`
- Username: `sa`
- Password: *(empty)*

## API Endpoints

### Authentication (Public)
| Method | Endpoint              | Description                    |
|--------|-----------------------|--------------------------------|
| POST   | `/api/auth/register`  | Register a new user            |
| POST   | `/api/auth/login`     | Login and get JWT token        |

### Users (Authenticated)
| Method | Endpoint              | Description                    |
|--------|-----------------------|--------------------------------|
| GET    | `/api/users/me`       | Get current user profile       |
| GET    | `/api/users/{userId}` | Get user profile by ID         |

### Rides
| Method | Endpoint                  | Description                        |
|--------|---------------------------|------------------------------------|
| POST   | `/api/rides/estimate`     | Get fare estimate (Public)         |
| POST   | `/api/rides/book`         | Book a new ride (Rider)            |
| PUT    | `/api/rides/{id}/cancel`  | Cancel a ride                      |
| GET    | `/api/rides/{id}`         | Get ride details                   |
| GET    | `/api/rides/my-rides`     | Get rider's ride history           |

### Drivers
| Method | Endpoint                              | Description                      |
|--------|---------------------------------------|----------------------------------|
| POST   | `/api/drivers/vehicle`                | Register vehicle                 |
| PUT    | `/api/drivers/location`               | Update GPS location              |
| PUT    | `/api/drivers/go-online`              | Go online for rides              |
| PUT    | `/api/drivers/go-offline`             | Go offline                       |
| GET    | `/api/drivers/available-rides`        | View available ride requests     |
| PUT    | `/api/drivers/rides/{id}/accept`      | Accept a ride                    |
| PUT    | `/api/drivers/rides/{id}/en-route`    | Mark en route to pickup          |
| PUT    | `/api/drivers/rides/{id}/arrived`     | Mark arrived at pickup           |
| PUT    | `/api/drivers/rides/{id}/start?otp=`  | Start ride (OTP required)        |
| PUT    | `/api/drivers/rides/{id}/complete`    | Complete the ride                |
| GET    | `/api/drivers/rides`                  | Get driver's ride history        |
| GET    | `/api/drivers/nearby`                 | Find nearby available drivers    |

### Payments
| Method | Endpoint                   | Description                      |
|--------|----------------------------|----------------------------------|
| POST   | `/api/payments/pay`        | Process payment for a ride       |
| GET    | `/api/payments/ride/{id}`  | Get payment for a ride           |
| GET    | `/api/payments/my-payments`| Get rider's payment history      |
| GET    | `/api/payments/my-earnings`| Get driver's earnings history    |

### Ratings
| Method | Endpoint                          | Description                    |
|--------|-----------------------------------|--------------------------------|
| POST   | `/api/ratings`                    | Submit a ride rating           |
| GET    | `/api/ratings/user/{userId}`      | Get user's ratings             |
| GET    | `/api/ratings/user/{userId}/average` | Get user's average rating   |
| GET    | `/api/ratings/ride/{rideId}`      | Get ratings for a ride         |

## Sample Data

The application initializes with sample data for testing:

### Test Accounts
| Email                   | Password      | Role   |
|------------------------|---------------|--------|
| rider1@example.com     | password123   | RIDER  |
| rider2@example.com     | password123   | RIDER  |
| driver1@example.com    | password123   | DRIVER |
| driver2@example.com    | password123   | DRIVER |
| driver3@example.com    | password123   | DRIVER |
| admin@ridesharing.com  | admin123      | ADMIN  |

### Sample Ride Flow
1. **Login** as a rider → Get JWT token
2. **Get fare estimate** → See price before booking
3. **Book a ride** → Ride status changes to REQUESTED
4. **Login** as a driver → Accept the ride
5. **Driver en route** → DRIVER_EN_ROUTE
6. **Driver arrived** → ARRIVED
7. **Start ride** with OTP → IN_PROGRESS
8. **Complete ride** → COMPLETED (actual fare calculated)
9. **Process payment** → Choose payment method
10. **Submit ratings** → Both parties rate each other

## Vehicle Types & Pricing

| Type    | Multiplier | Description              |
|---------|-----------|--------------------------|
| BIKE    | 0.6x      | Most affordable option   |
| AUTO    | 0.8x      | Budget-friendly rickshaw |
| MINI    | 1.0x      | Standard economy car     |
| SEDAN   | 1.3x      | Comfortable sedan        |
| SUV     | 1.6x      | Spacious SUV             |
| PREMIUM | 2.0x      | Premium/luxury vehicle   |

## Configuration

Key settings in `application.yml`:
```yaml
app:
  ride:
    base-fare: 50.0           # Base fare
    per-km-rate: 12.0         # Rate per kilometer
    per-minute-rate: 2.0      # Rate per minute
    surge-multiplier: 1.0     # Default surge (1.0 = no surge)
    max-surge-multiplier: 3.0 # Max surge cap
    search-radius-km: 5.0     # Driver search radius
```

## Running with MySQL (Production)

```bash
# Start MySQL and create database
mysql -u root -p -e "CREATE DATABASE ridesharingdb;"

# Run with MySQL profile
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

## License

This project is licensed under the MIT License.
