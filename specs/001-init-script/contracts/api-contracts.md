# API Contracts: RocketMQ Client Initialization Scripts

## Script Interface Contract

### Command Line Interface

The initialization scripts provide a simple command-line interface with no required parameters.

**Endpoint**: Script execution
**Method**: CLI execution
**Path**: `scripts/init-{platform}.{extension}`

#### Parameters

None required.

#### Response

**Success Response**:
- Exit code: 0
- Stdout: Progress messages and success confirmation
- Stderr: Empty or informational messages only

**Error Response**:
- Exit code: Non-zero
- Stdout: Progress messages and error context
- Stderr: Error details

#### Example Success Output

```text
[INFO] Starting RocketMQ client initialization for Windows...
[INFO] Found mqadmin tool. Proceeding with initialization...
[INFO] Creating HelloServiceTopic...
[SUCCESS] HelloServiceTopic created successfully.
[INFO] Generating .env file...
[SUCCESS] .env file created successfully.
[SUCCESS] RocketMQ client initialization completed successfully!
[INFO] Please update the OPENAI_API_KEY in the .env file with your actual API key.
```

#### Example Error Output

```text
[INFO] Starting RocketMQ client initialization for Windows...
[ERROR] mqadmin tool not found. Please ensure RocketMQ is installed and accessible via PATH.
```

## File Generation Contract

### .env File

**File Name**: `.env`
**Location**: Project root directory
**Format**: KEY=VALUE pairs, one per line
**Encoding**: UTF-8

#### Generated Content

```
OPENAI_API_KEY=
```

#### Validation Rules

1. File must be created in project root
2. File must contain exactly one line with "OPENAI_API_KEY="
3. File must be readable by standard text editors
4. File must use LF line endings on Linux, CRLF on Windows

## Topic Creation Contract

### RocketMQ Topic

**Topic Name**: HelloServiceTopic
**Cluster**: DefaultCluster
**Creation Method**: mqadmin updateTopic command

#### Validation Rules

1. Topic must be accessible via standard RocketMQ tools after creation
2. Topic must be associated with the DefaultCluster
3. Creation command must return success or acceptable warning
4. Existing topics with the same name are acceptable

## Cross-Platform Consistency Contract

### Equivalent Behavior

Both Windows and Linux scripts must provide equivalent functionality:

1. Same topic creation behavior
2. Same .env file generation
3. Similar progress reporting
4. Equivalent error handling
5. Same exit codes for similar conditions