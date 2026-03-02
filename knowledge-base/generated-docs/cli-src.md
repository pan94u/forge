# cli-src

Generated: 2026-03-02T23:21:18Z

## Module Structure

```
  cli/src/main/kotlin/com/forge/cli/ForgeCliMain.kt
  cli/src/main/kotlin/com/forge/cli/commands/DoctorCommand.kt
  cli/src/main/kotlin/com/forge/cli/commands/InitCommand.kt
  cli/src/main/kotlin/com/forge/cli/commands/McpCommand.kt
  cli/src/main/kotlin/com/forge/cli/commands/SkillCommand.kt
  cli/src/main/kotlin/com/forge/cli/commands/UpdateCommand.kt
```

## Public Interfaces and Classes

### main/kotlin/com/forge/cli/ForgeCliMain.kt

- `28: class ForgeCli : Runnable`

### main/kotlin/com/forge/cli/commands/DoctorCommand.kt

- `15: class DoctorCommand : Callable<Int>`

### main/kotlin/com/forge/cli/commands/InitCommand.kt

- `16: class InitCommand : Callable<Int>`

### main/kotlin/com/forge/cli/commands/McpCommand.kt

- `19: class McpCommand : Runnable`
- `30: class McpStatusCommand : Callable<Int>`
- `124: class McpTestCommand : Callable<Int>`

### main/kotlin/com/forge/cli/commands/SkillCommand.kt

- `19: class SkillCommand : Runnable`
- `30: class SkillListCommand : Callable<Int>`
- `165: class SkillShowCommand : Callable<Int>`
- `208: class SkillValidateCommand : Callable<Int>`

### main/kotlin/com/forge/cli/commands/UpdateCommand.kt

- `15: class UpdateCommand : Callable<Int>`

