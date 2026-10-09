# Quality Gates & DevSecOps Strategy

This document outlines the local and automated Quality Gates configured for the **RentRewards** project, enabling developers and automated CI systems (via GitHub Actions and local `act`) to enforce code reliability, security, and test coverage standards.

---

## 1. Overview of Quality Gates

| Gate ID | Quality Gate | Tool / Mechanism | Failure Threshold / Rule | Execution Context |
| :---: | :--- | :--- | :--- | :--- |
| **QG-01** | **Strict Compilation & Typing** | `javac (Java 17)`, `node --check` | Zero compilation warnings or syntax errors | Local (`mvn`, `node`) & CI |
| **QG-02** | **Code Coverage Gate** | **JaCoCo (`jacoco-maven-plugin`)** | **Line coverage < 80%** fails the build | Local (`mvn test`) & `act` / CI |
| **QG-03** | **Unit & Concurrency Verification** | JUnit 5 + Node Test Runner | 100% test pass rate (34 total runs, 0 failures) | Local & CI |
| **QG-04** | **Static Analysis & SAST Linting** | Python SAST scanner + Node AST | Prohibits unmanaged stdout, sleep in production, eval, or raw var | Local & `act` / CI |
| **QG-05** | **Automated CI/CD Workflows** | GitHub Actions / `act` | All matrix jobs green (`push`, `pull_request`) | Local (`act`) & GitHub |

---

## 2. JaCoCo Code Coverage Quality Gate

JaCoCo is configured directly inside `pom.xml` (`org.jacoco:jacoco-maven-plugin:0.8.12`) to enforce quality checks during the Maven lifecycle:

### Configuration:
- **Phase:** `test`
- **Rule:**
  ```xml
  <limit>
      <counter>LINE</counter>
      <value>COVEREDRATIO</value>
      <minimum>0.80</minimum>
  </limit>
  ```
- **Current Verification Metrics:**
  - **Instructions Covered:** **100.00%** (293 / 293)
  - **Branches Covered:** **100.00%** (10 / 10)
  - **Lines Covered:** **100.00%** (71 / 71)
  - **Complexity Covered:** **100.00%** (33 / 33)
  - **Methods Covered:** **100.00%** (28 / 28)
  - **Classes Covered:** **100.00%** (7 / 7)

Interactive HTML reports are automatically generated at `target/site/jacoco/index.html`.

---

## 3. Local Execution with `act`

Since `act` is installed locally, you can test and reproduce the complete GitHub Actions pipeline on your local machine using Docker:

### List available workflows:
```bash
act -l -W .github/workflows/quality-gates.yml
```

### Run the coverage and test Quality Gate job:
```bash
act -j code-quality-and-coverage -W .github/workflows/quality-gates.yml
```

### Run the SAST static analysis job:
```bash
act -j static-analysis-and-sast -W .github/workflows/quality-gates.yml
```

### Run the GitHub CodeQL SAST analysis job:
```bash
act -j security-codeql-sast -W .github/workflows/quality-gates.yml
```

---

## 4. GitHub CodeQL SAST Integration

GitHub CodeQL is configured as a dedicated job (`security-codeql-sast`) running on both Java and JavaScript matrices with `security-extended` query suites:
- **Languages scanned:** `java-kotlin`, `javascript-typescript`.
- **Query suite:** `security-extended` (includes OWASP Top 10, CWE vulnerabilities, memory leaks, and concurrency race hazards).
- **Automation:** Integrated into pull requests and pushes across all branches.

---

## 5. Enterprise Alignment: SonarQube & Checkmarx

In an enterprise environment:
1. **SonarQube / SonarCloud:**
   - Evaluates code smells, technical debt ratio (< 5%), and new code coverage (> 80%).
   - Consumes the JaCoCo XML report generated at `target/site/jacoco/jacoco.xml`.
2. **Checkmarx SAST & GitHub CodeQL:**
   - Scans against OWASP Top 10 vulnerabilities, CWE injection vectors, and data sanitization paths.
   - Enforces a zero High/Critical vulnerability gate prior to branch merge.

