# Setup

## Requirements

- Java 17+
- Maven 3.8+
- Node.js 20+ (no package installation is required)

## Running the tests

From the project root:

```bash
mvn test
npm run test:web
```

You should see a mix of passing and failing tests. That is the intentional
starting point; the output identifies the behavior to investigate.

## Previewing the dashboard

The dashboard uses Tailwind through its CDN and ES modules. Serve the project
root with any static server, for example:

```bash
python3 -m http.server 8000
```

Then open <http://localhost:8000/web/>. You do not need to install frontend
dependencies or run a CSS build.

## Project structure

```
src/main/java/com/rentrewards/challenge/
  model/
    PaymentEvent.java      - incoming webhook payload
    MemberAccount.java     - in-memory member state (streak, points/month)
    PointsResult.java      - outcome of processing one event
  service/
    PointsCalculator.java  - base points, multiplier, streak bonus rules
    ProcessedEventStore.java - tracks which webhook events were already processed
    RewardsEngine.java     - orchestrates the whole flow

src/test/java/com/rentrewards/challenge/service/
  RewardsEngineTest.java   - the test suite you need to make fully pass

web/
  index.html               - Tailwind dashboard preview
  dashboard.js             - maps processing outcomes to visible UI states
  dashboard.test.js        - frontend behavior tests (Node's built-in runner)
```

No database, application framework, or external service is required. The
Tailwind CDN is only needed for the visual preview, not for tests.
