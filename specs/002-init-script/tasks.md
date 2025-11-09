# Tasks: RocketMQ Client Initialization Scripts

**Input**: Design documents from `/specs/001-init-script/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: The examples below include test tasks. Tests are OPTIONAL - only include them if explicitly requested in the feature specification.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/`, `tests/` at repository root
- **Utility scripts**: `scripts/` at repository root
- Paths shown below follow the implementation plan structure

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Create scripts directory structure per implementation plan
- [x] T002 [P] Set up documentation directory structure in specs/001-init-script/
- [x] T003 [P] Configure version control ignores for generated files (.env)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T004 Define script execution interface contract in contracts/api-contracts.md
- [x] T005 [P] Create base script template with standardized logging format
- [x] T006 [P] Implement cross-platform file system utility functions
- [x] T007 Setup RocketMQ connection validation framework
- [x] T008 Configure environment file generation mechanism

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Initialize RocketMQ Client Environment (Priority: P1) 🎯 MVP

**Goal**: Enable developers to easily set up their local RocketMQ client environment with automated topic creation and environment file generation

**Independent Test**: Can be fully tested by running the initialization script on a clean environment and verifying that the RocketMQ topic is created and the .env file is generated.

### Tests for User Story 1 (OPTIONAL - only if tests requested) ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T009 [P] [US1] Create test environment for Windows script execution
- [x] T010 [P] [US1] Create test environment for Linux script execution

### Implementation for User Story 1

- [x] T011 [P] [US1] Create Windows initialization script with topic creation in scripts/init-windows.bat
- [x] T012 [P] [US1] Create Linux initialization script with topic creation in scripts/init-linux.sh
- [x] T013 [US1] Implement RocketMQ validation in Windows script (depends on T011)
- [x] T014 [US1] Implement RocketMQ validation in Linux script (depends on T012)
- [x] T015 [US1] Add .env file generation to Windows script (depends on T011)
- [x] T016 [US1] Add .env file generation to Linux script (depends on T012)
- [x] T017 [US1] Add detailed logging and error handling to both scripts
- [x] T018 [US1] Add success/failure reporting to both scripts

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Cross-platform Compatibility (Priority: P2)

**Goal**: Ensure initialization scripts work consistently across Windows and Linux platforms

**Independent Test**: Can be tested by running both the Windows and Linux scripts on their respective platforms and comparing the outcomes.

### Tests for User Story 2 (OPTIONAL - only if tests requested) ⚠️

- [x] T019 [P] [US2] Create cross-platform comparison test for script outputs
- [x] T020 [P] [US2] Create validation test for equivalent functionality

### Implementation for User Story 2

- [x] T021 [P] [US2] Enhance Windows script with improved error messages
- [x] T022 [P] [US2] Enhance Linux script with improved error messages
- [x] T023 [US2] Align logging format between Windows and Linux scripts
- [x] T024 [US2] Ensure equivalent exit codes for similar conditions
- [x] T025 [US2] Add platform-specific troubleshooting guidance
- [x] T026 [US2] Verify consistent .env file format across platforms

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

 - [x] T027 [P] Create README.md with usage instructions in scripts/
- [x] T028 Add documentation references to quickstart.md
- [x] T029 [P] Update contracts/api-contracts.md with final interfaces
- [x] T030 Run quickstart.md validation on both platforms
- [x] T031 [P] Add example usage to documentation
- [x] T032 Security review of script execution permissions

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable

### Within Each User Story

- Tests (if included) MUST be written and FAIL before implementation
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- All tests for a user story marked [P] can run in parallel
- Different user stories can be worked on in parallel by different team members

---

## Parallel Example: User Story 1

```bash
# Launch all models for User Story 1 together:
Task: "Create Windows initialization script with topic creation in scripts/init-windows.bat"
Task: "Create Linux initialization script with topic creation in scripts/init-linux.sh"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1
   - Developer B: User Story 2
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence