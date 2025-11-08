# Quick Start: RocketMQ Client Initialization

## Prerequisites

1. RocketMQ installed and running on your system
2. `mqadmin` tool available in your system PATH
3. Appropriate permissions to create topics in RocketMQ

## Installation

The initialization scripts are located in the `scripts` directory at the project root:
- `init-windows.bat` for Windows environments
- `init-linux.sh` for Linux environments

No additional installation is required.

## Usage

### Windows

1. Open Command Prompt or PowerShell
2. Navigate to your project directory
3. Run the initialization script:
   ```cmd
   scripts\init-windows.bat
   ```

### Linux

1. Open a terminal
2. Navigate to your project directory
3. Make the script executable (if not already):
   ```bash
   chmod +x scripts/init-linux.sh
   ```
4. Run the initialization script:
   ```bash
   ./scripts/init-linux.sh
   ```

## What Happens During Initialization

1. **Validation**: The script checks if RocketMQ and mqadmin are available
2. **Topic Creation**: Creates a topic named "HelloServiceTopic" in the "DefaultCluster"
3. **Environment Setup**: Generates a `.env` file in the project root with `OPENAI_API_KEY=`
4. **Completion**: Reports success or failure of each step

## Post-Initialization Steps

1. **Configure API Key**: Edit the generated `.env` file to add your actual OpenAI API key:
   ```env
   OPENAI_API_KEY=your_actual_api_key_here
   ```

2. **Verify Topic Creation**: You can verify the topic was created by using RocketMQ's admin tools:
   ```bash
   mqadmin topicList
   ```

## Troubleshooting

### "mqadmin tool not found" Error

Ensure RocketMQ is properly installed and the `mqadmin` tool is in your system PATH.

### "Failed to create HelloServiceTopic" Warning

This may occur if:
- RocketMQ is not running
- You don't have permissions to create topics
- The topic already exists (harmless warning)

### Script Execution Policy Errors (Windows)

If you encounter execution policy errors on Windows, you may need to adjust your PowerShell execution policy:
```cmd
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

## Customization

The scripts can be customized by modifying:
- Topic name in the script files
- Environment variables in the .env generation section
- Logging and error handling behavior

For major changes, consider creating a new script rather than modifying the existing ones.