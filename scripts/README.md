# RocketMQ Client Initialization Scripts

This directory contains cross-platform scripts to initialize your RocketMQ client environment for development.

## Scripts

- `init-windows.bat` - Windows initialization script
- `init-linux.sh` - Linux initialization script

## What the Scripts Do

1. **Validate RocketMQ Installation**: Check that RocketMQ and the `mqadmin` tool are properly installed and accessible
2. **Create HelloServiceTopic**: Create a RocketMQ topic named "HelloServiceTopic" in the "DefaultCluster"
3. **Generate .env File**: Create a local `.env` file with `OPENAI_API_KEY=` placeholder in the project root

## Prerequisites

- RocketMQ installed and running on your system
- `mqadmin` tool available in your system PATH
- Appropriate permissions to create topics in RocketMQ

## Usage

### Windows

1. Open Command Prompt or PowerShell
2. Navigate to your project directory
3. Run the initialization script:
   ```cmd
   scripts\init-windows.bat
   ```

Example output:
```
[INFO] Starting RocketMQ client initialization for Windows...
[INFO] Found mqadmin tool. Proceeding with initialization...
[INFO] Creating HelloServiceTopic...
[SUCCESS] HelloServiceTopic created successfully.
[INFO] Generating .env file...
[SUCCESS] .env file created successfully.
[SUCCESS] RocketMQ client initialization completed successfully!
[INFO] Please update the OPENAI_API_KEY in the .env file with your actual API key.
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

Example output:
```
[INFO] Starting RocketMQ client initialization for Linux...
[INFO] Found mqadmin tool. Proceeding with initialization...
[INFO] Creating HelloServiceTopic...
[SUCCESS] HelloServiceTopic created successfully.
[INFO] Generating .env file...
[SUCCESS] .env file created successfully.
[SUCCESS] RocketMQ client initialization completed successfully!
[INFO] Please update the OPENAI_API_KEY in the .env file with your actual API key.
```

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

**Windows:**
- Verify RocketMQ is installed and `ROCKETMQ_HOME` environment variable is set
- Check that `%ROCKETMQ_HOME%\bin` is in your PATH
- Run `where mqadmin` to verify the tool is found

**Linux:**
- Verify RocketMQ is installed and `ROCKETMQ_HOME` environment variable is set
- Check that `$ROCKETMQ_HOME/bin` is in your PATH
- Run `which mqadmin` to verify the tool is found

### "Failed to create HelloServiceTopic" Warning

This may occur if:
- RocketMQ is not running (ensure NameServer and Broker are started)
- You don't have permissions to create topics
- The topic already exists (harmless warning)
- Network connectivity issues with RocketMQ server

### Script Execution Policy Errors (Windows)

If you encounter execution policy errors on Windows, you may need to adjust your PowerShell execution policy:
```cmd
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### "Access Denied" or Permission Errors

**Windows:**
- Run Command Prompt or PowerShell as Administrator
- Ensure you have write permissions in the current directory
- Check antivirus/firewall settings that might block file creation

**Linux:**
- Run with appropriate permissions or use `sudo` if needed
- Ensure you have write permissions in the current directory
- Check SELinux or AppArmor policies that might restrict file operations

### .env File Not Created

If the .env file is not created:
- Check available disk space
- Verify write permissions in the current directory
- Ensure no security software is blocking file creation

## Customization

The scripts can be customized by modifying:
- Topic name in the script files
- Environment variables in the .env generation section
- Logging and error handling behavior

For major changes, consider creating a new script rather than modifying the existing ones.