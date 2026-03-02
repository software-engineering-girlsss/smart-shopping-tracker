# Smart Shopping Planner – Requirements & Backlog

---

# 1. Product Backlog – User Stories

## Epic 1: Price Optimization

### US-01 – Cheapest Store for Full List
**As a** user  
**I want** to find the store with the lowest total price for my entire shopping list  
**So that** I can minimize my grocery expenses  

**Acceptance Criteria:**
- User selects a shopping list
- System calculates total price per store
- Stores are ranked by total cost (ascending)
- Cheapest store is clearly highlighted

---

### US-02 – Cheapest Store for Subset
**As a** user  
**I want** to compare prices for a selected subset of products  
**So that** I can optimize partial purchases  

**Acceptance Criteria:**
- User selects specific products from a list
- System calculates total per store for selected products only
- Results are ranked by total price

---

### US-03 – Cheapest Store for Single Product
**As a** user  
**I want** to see which store sells a product at the lowest price  
**So that** I can quickly decide where to buy it  

**Acceptance Criteria:**
- User enters product name
- System shows list of stores
- Prices sorted ascending by default

---

## Epic 2: Product Search

### US-04 – Product Availability Search
**As a** user  
**I want** to search for a product by name  
**So that** I can see where it is available  

**Acceptance Criteria:**
- Partial name matching supported
- Store list includes:
  - Price
  - Availability status
- Sorting options available (price, store name)

---

## Epic 3: Shopping List Management

### US-05 – Create and Edit Shopping List
**As a** user  
**I want** to create and edit shopping lists  
**So that** I can organize my purchases  

**Acceptance Criteria:**
- Add, remove, update product quantity
- Save multiple named lists
- Persist lists between sessions (for logged-in users)

---

## Epic 4: Route Planning

### US-06 – Generate Shopping Route
**As a** user  
**I want** a route between selected stores  
**So that** I can reduce travel time  

**Acceptance Criteria:**
- User selects stores
- Route is generated
- Distance and estimated time displayed

---

### US-07 – Route Optimization
**As a** user  
**I want** the route optimized by distance and store working hours  
**So that** I avoid closed stores and unnecessary travel  

**Acceptance Criteria:**
- Store working hours considered
- Stores ordered in optimal visit sequence
- System warns if store is closed

---

## Epic 5: Discount Notifications

### US-08 – Mark Favorite Products
**As a** user  
**I want** to mark products as favorite  
**So that** I can track discounts  

**Acceptance Criteria:**
- Favorite toggle available
- Favorites stored per user

---

### US-09 – Discount Alerts
**As a** user  
**I want** to receive notifications when favorite products are discounted  
**So that** I can save money  

**Acceptance Criteria:**
- System detects price drop
- Notification sent (email or in-app)
- Notification includes:
  - Old price
  - New price
  - Store name

---

## Optional Epic: Smart Meal Planning (Experimental)

### US-10 – Recipe Suggestions
**As a** user  
**I want** recipe suggestions based on my products  
**So that** I can plan meals efficiently  

---

### US-11 – Nutrition Calculation
**As a** user  
**I want** to see macronutrient breakdown  
**So that** I can manage my diet  

---

# 2. Functional Requirements

## FR-01 Price Aggregation
The system shall aggregate product prices from multiple stores.

## FR-02 Total Cost Calculation
The system shall calculate total shopping list cost per store.

## FR-03 Cross-Store Optimization
The system shall compute optimal distribution of products across stores to minimize total cost.

## FR-04 Product Search
The system shall support product search with partial name matching.

## FR-05 Price Ranking
The system shall sort results by price (ascending by default).

## FR-06 Shopping List Management
The system shall allow creation, editing, deletion, and persistence of shopping lists.

## FR-07 Discount Tracking
The system shall track price changes for favorite products.

## FR-08 Notification System
The system shall send notifications when price drops occur.

## FR-09 Route Generation
The system shall generate routes between selected stores.

## FR-10 Route Optimization
The system shall optimize route based on distance and store working hours.

## FR-11 User Accounts
The system shall support user registration and authentication.

---

# 3. Non-Functional Requirements (NFR)

## NFR-01 Performance
- Price optimization for a list of up to 100 products across 20 stores must complete in under 2 seconds.
- Product search results must return in under 1 second (95th percentile).

## NFR-02 Scalability
- System must support at least 10,000 concurrent users.
- Architecture must allow horizontal scaling of optimization service.

## NFR-03 Data Freshness
- Price data must not be older than 24 hours.
- Discount detection must run at least once per hour.

## NFR-04 Reliability
- System uptime target: 99.5% monthly availability.
- No data loss for persisted shopping lists.

## NFR-05 Security
- Passwords must be hashed using industry-standard algorithms (e.g., bcrypt).
- All communication must use HTTPS.
- Users can access only their own shopping lists and favorites.

## NFR-06 Privacy
- User data must not be shared with third parties.
- Users must be able to delete their account and associated data.

## NFR-07 Usability
- Core workflow (create list → optimize → view results) must not exceed 5 user interactions.
- Mobile-responsive interface required.

## NFR-08 Maintainability
- Codebase must follow modular architecture.
- Business logic (optimization engine) must be isolated from UI layer.
- Automated test coverage minimum: 70% of business logic.

## NFR-09 Observability
- System must log:
  - Optimization execution time
  - Failed price imports
  - Notification delivery status
- Critical failures must trigger alerts.

## NFR-10 Compatibility
- Must support latest two versions of major browsers (Chrome, Firefox, Edge, Safari).

## NFR-11 Constraints
- Prototype must be deployable in cloud environment.
- External map API usage must remain within free-tier limits (if applicable).

---

# 4. Definition of Done (DoD)

A feature is considered complete when:

- All acceptance criteria are satisfied.
- Unit tests are written and passing.
- No critical or major defects remain.
- Documentation is updated.
- Feature is deployable to staging environment.