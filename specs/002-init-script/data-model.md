# Data Model: RocketMQ Client Initialization Scripts

## Entities

### Initialization Script

Represents the executable scripts that set up the RocketMQ client environment.

**Attributes:**
- scriptName (String): Name of the script file (e.g., "init-windows.bat", "init-linux.sh")
- platform (Enum): Target operating system (Windows, Linux)
- filePath (String): Path to the script file relative to project root
- description (String): Brief description of script functionality

**Behaviors:**
- Executes initialization sequence
- Validates prerequisites
- Creates RocketMQ topic
- Generates environment file
- Provides user feedback

### RocketMQ Topic

Represents the message queue topic that will be created by the initialization scripts.

**Attributes:**
- topicName (String): Name of the topic ("HelloServiceTopic")
- clusterName (String): Name of the RocketMQ cluster ("DefaultCluster")
- status (Enum): Current state (Pending, Created, Error)

**Behaviors:**
- Created via mqadmin command
- Verified for existence

### Environment File (.env)

Represents the configuration file containing environment variables.

**Attributes:**
- fileName (String): Name of the file (".env")
- path (String): Location of the file (project root)
- variables (Map<String, String>): Key-value pairs of environment variables
- isOpenAIKeySet (Boolean): Whether the OPENAI_API_KEY has been configured

**Behaviors:**
- Generated with default values
- Preserved if already exists
- Can be modified by user

## Relationships

1. **Initialization Script** --creates--> **RocketMQ Topic**
2. **Initialization Script** --generates--> **Environment File**
3. **Environment File** --contains--> **OPENAI_API_KEY variable**

## Validation Rules

1. Script files must exist at specified paths
2. RocketMQ topic name must be "HelloServiceTopic"
3. Environment file must contain OPENAI_API_KEY variable
4. Scripts must be executable on their target platforms
5. mqadmin tool must be available in system PATH