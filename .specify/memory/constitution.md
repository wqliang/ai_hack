<!--
SYNC IMPACT REPORT
==================
Version change: N/A (initial version) → 1.0.0
Modified principles: N/A (initial creation)
Added sections:
  - Core Principles (5 principles)
  - Quality Standards
  - Development Workflow
  - Governance
Removed sections: N/A
Templates requiring updates:
  ✅ plan-template.md - Constitution Check section compatible
  ✅ spec-template.md - Requirements align with principles
  ✅ tasks-template.md - Test-driven approach compatible
  ✅ agent-file-template.md - No specific updates needed
  ✅ checklist-template.md - No specific updates needed
Follow-up TODOs: None
-->

# AI Hack Spring AI Demo Constitution

## Core Principles

### I. Code Documentation (NON-NEGOTIABLE)

Every class and method MUST have JavaDoc comments explaining its purpose and behavior. Key implementation steps within methods MUST have inline comments describing what the code is doing. This principle ensures code maintainability and knowledge transfer.

**Rationale**: In a Java application that integrates AI capabilities, clear documentation is essential for understanding both business logic and AI model interactions. Well-documented code reduces onboarding time and prevents technical debt.

**Requirements**:
- All public classes MUST have JavaDoc with @author, class description, and @since tags
- All public and protected methods MUST have JavaDoc with @param, @return, and @throws tags
- Complex algorithms or business logic MUST have inline comments explaining the approach
- Magic numbers MUST be replaced with named constants with explanatory comments

### II. Java Development Standards

All code MUST follow standard Java development conventions including proper naming, package organization, and design patterns. Code MUST adhere to Java 21 language features and best practices.

**Rationale**: Consistent coding standards improve code readability, reduce bugs, and enable effective team collaboration. Following industry-standard practices ensures the codebase remains accessible to any Java developer.

**Requirements**:
- Follow Java naming conventions: camelCase for methods/variables, PascalCase for classes
- Package names follow reverse domain notation (ai.hack.*)
- Use appropriate access modifiers (prefer private/protected, expose public only when necessary)
- Leverage Java 21 features: records for DTOs, sealed classes where appropriate, pattern matching
- Apply SOLID principles and appropriate design patterns
- Avoid raw types, prefer generics for type safety

### III. Spring Boot Best Practices

Application architecture MUST follow Spring Boot conventions and best practices for dependency injection, configuration management, and layered architecture.

**Rationale**: Spring Boot provides proven patterns for building production-ready applications. Following these patterns ensures scalability, testability, and maintainability.

**Requirements**:
- Use constructor-based dependency injection (avoid field injection)
- Follow layered architecture: Controller → Service → Repository
- Use Spring configuration properties for externalized configuration
- Leverage Spring Boot auto-configuration when possible
- Use appropriate Spring annotations (@Service, @Controller, @Repository, @Configuration)
- Implement proper exception handling with @ControllerAdvice
- Use Spring's validation framework for input validation

### IV. Testing Discipline

All significant features MUST have corresponding tests. Unit tests for services, integration tests for API endpoints, and contract tests for external dependencies (AI models) are required.

**Rationale**: Tests serve as executable documentation and prevent regressions. Given the project's AI integration, testing ensures both business logic correctness and proper AI model interaction.

**Requirements**:
- Unit tests for all service classes using JUnit 5 and Mockito
- Integration tests for REST endpoints using MockMvc
- Test coverage focus on business logic and edge cases
- Use test profiles (application-test.yml) to isolate test configuration
- Mock external AI dependencies (OpenAI, Ollama) in unit tests
- Use descriptive test method names following Given-When-Then pattern
- Tests MUST be written before or alongside implementation (Test-Driven Development encouraged)

### V. Git Flow and Version Control

The project MUST follow Git Flow branching strategy with feature branches, proper commit messages, and protected main branch.

**Rationale**: Git Flow provides a structured approach to version control that supports parallel development, release management, and hotfix deployment.

**Requirements**:
- Use conventional commits: feat:, fix:, docs:, refactor:, test:, chore:
- Feature branches MUST branch from develop and merge back to develop
- Main branch is production-ready code only
- All merges MUST use --no-ff to preserve branch history
- Pull requests require code review before merge
- Commit messages MUST be descriptive and reference related issues

## Quality Standards

### Code Review Requirements
- All code changes require peer review via pull request
- Reviewers MUST verify:
  - JavaDoc and inline comments are present and accurate
  - Code follows Java and Spring Boot best practices
  - Tests exist and provide adequate coverage
  - No security vulnerabilities (SQL injection, XSS, command injection, etc.)
  - Proper error handling and logging

### Security Standards
- Never commit secrets (API keys, passwords) to version control
- Use environment variables for sensitive configuration (OPENAI_API_KEY)
- Validate and sanitize all user inputs
- Use parameterized queries to prevent SQL injection
- Implement proper authentication and authorization for APIs
- Log security events for audit trails

### Performance Standards
- RESTful APIs SHOULD respond within 200ms for simple operations (excluding AI processing time)
- AI chat operations timeout configured appropriately for model response times
- Use connection pooling for database and HTTP clients
- Implement caching where appropriate (Spring Cache abstraction)

## Development Workflow

### Build and Test Process
1. Local development: `./gradlew build` MUST pass before commit
2. Run tests: `./gradlew test` MUST show all tests passing
3. Code review: Pull request created, reviewed, and approved
4. Integration: Merge to develop with --no-ff
5. Release preparation: Create release/* branch from develop when ready
6. Production deployment: Merge release to main after validation

### Configuration Management
- Default configuration in application.yml
- Environment-specific overrides in application-{profile}.yml
- Use Spring profiles for different environments (test, dev, prod)
- Document all configuration properties in CLAUDE.md or README.md

### Dependency Management
- Keep dependencies up to date with security patches
- Document rationale for major dependency versions in build.gradle.kts
- Review Spring Boot and Spring AI release notes before upgrading
- Test thoroughly after dependency updates

## Governance

### Amendment Process
This constitution can be amended through the following process:
1. Proposal: Create issue describing proposed amendment and rationale
2. Discussion: Team discusses impact, benefits, and migration plan
3. Approval: Team lead or majority approval required
4. Documentation: Update constitution with new version number
5. Migration: Update existing code to comply with new principles (if applicable)
6. Communication: Announce changes to all team members

### Version Semantics
- MAJOR: Backward-incompatible changes requiring significant code refactoring
- MINOR: New principles or sections added, expanded guidance
- PATCH: Clarifications, typos, non-semantic refinements

### Compliance Review
- All pull requests MUST verify compliance with this constitution
- Code reviewers are responsible for enforcing these principles
- Deviations MUST be justified in writing and approved by team lead
- Quarterly review of constitution relevance and effectiveness

### Exception Process
When principles cannot be followed:
1. Document the specific principle being violated
2. Explain why it cannot be followed (technical limitation, external dependency, etc.)
3. Describe simpler alternatives considered and why they were rejected
4. Get explicit approval from team lead
5. Document the exception in code comments or design documents

**Version**: 1.0.0 | **Ratified**: 2025-11-11 | **Last Amended**: 2025-11-11
