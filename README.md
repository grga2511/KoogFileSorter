# KoogFileSorter

A Kotlin application that uses a Koog AI agent to classify files
and move them into category folders.

The application processes existing files in the inbox and uses
Java WatchService to detect newly created files.

## Requirements

- JDK 21
- IntelliJ IDEA, recommended
- Internet access for downloading dependencies and calling the LLM
- A valid Google Gemini API key with access to the configured model

The Gradle Wrapper is included. A separate Gradle installation
is not required.

## Configuration

Review `AppConfig` and the project's configuration files before
starting the application.

Check the following settings:

- Workspace location
- Gemini model
- Proxy settings, if required by your network

The application is intended to operate inside its configured workspace.
Use disposable test files when trying the sorter.

### API key

In IntelliJ IDEA, open:

**Run → Edit Configurations → the configuration used to launch MainKt
→ Environment variables**

Add:

```text
GOOGLE_API_KEY=your-api-key
```

Set the variable in the configuration you actually run.

Never commit a real API key or a run configuration containing it.

### Network and proxy

The project was developed in an environment that required an HTTP proxy.

Review the application's HTTP client configuration and disable or
replace environment-specific proxy settings for your network.

Gradle dependency downloads use separate proxy and certificate settings.
Review those settings if Gradle synchronization fails.

## Running the application

1. Clone the repository:

   ```powershell
   git clone https://github.com/grga2511/KoogFileSorter.git
   cd KoogFileSorter
   ```

2. Open the project in IntelliJ IDEA.
3. Select JDK 21 and synchronize Gradle.
4. Review the workspace, model and proxy configuration.
5. Set `GOOGLE_API_KEY` in the application run configuration.
6. Run the `main()` function in `Main.kt`.

Check the console for the resolved workspace and inbox paths.
Relative workspace paths depend on the application's working directory.

Keep the application running to process newly arriving files.
Stop the application using IntelliJ IDEA's Stop button.

## Workspace

The application initializes an inbox and category directories:

```text
workspace/
├── inbox/
├── documents/
├── code/
├── configurations/
├── other/
└── review/
```

Place files to classify in `workspace/inbox`.

The application moves files, so they will no longer remain in
the inbox after successful processing.

## How it works

1. The application initializes the workspace.
2. The folder watcher registers for new file events.
3. Existing inbox files are processed during the initial scan.
4. The watcher waits for file creation events instead of repeatedly
   scanning the entire inbox.
5. Before processing a candidate, the application checks that its
   size and modification time have stabilized.
6. The Koog agent uses tools to read the file and move it to a category.
7. Processing activity and errors are logged in the console.

If WatchService reports an event overflow, the application rescans
the inbox to recover files whose individual events may have been lost.

## Categories

| Category | Intended content |
|---|---|
| DOCUMENTS | Notes and other textual documents |
| CODE | Source code |
| CONFIGURATIONS | Application configuration files |
| OTHER | Files that do not fit the other categories |
| REVIEW | Uncertain classifications or files that cannot be read |

Classification uses an LLM, so results can vary.

## File tools

### readFile

Reads a UTF-8 text file from the inbox.

The tool checks the file name and rejects paths outside the inbox.
It reports a read failure for files exceeding the 64 KiB limit,
invalid UTF-8 content or detected binary content.

This sorter does not provide general PDF text extraction.

### moveFile

Moves an inbox file into an allowed category directory.

Existing destination files are not overwritten. If a destination
already exists, the incoming file is moved to the review directory
with a unique name.

Files with the same extension are not duplicates by themselves.
The destination-name conflict occurs when the target file name
already exists.

## Example

Add disposable files to the inbox, such as:

- `notes.txt` containing meeting notes
- `Hello.kt` containing Kotlin source code
- `settings.properties` containing application settings
- An empty or unreadable file for testing review handling

Check the category directories and console logs after processing.

To test a naming conflict, add another file whose name already exists
in its destination category. The existing file should remain unchanged,
and the incoming file should be moved to review.

## Tests

Run the tests from the project root:

```powershell
.\gradlew.bat test
```

The file-tool tests use temporary directories and do not need
to call a real LLM.

Tests can also be launched from IntelliJ IDEA.

## Model and provider notes

Supply your own API key. Model availability, quotas and possible
charges depend on your provider account.

The configured model must be supported by the agent creation code.

To use another provider, update the LLM client, model configuration
and dependencies as needed. A key from another provider cannot be
used with the Google client.

## Troubleshooting

- **Missing API key:** check the environment variables in the active
  run configuration.
- **Connection timeout:** check network access and proxy settings.
- **Gradle dependency errors:** check Gradle's network and certificate
  configuration.
- **Files are not processed:** check the inbox path printed in the
  console and confirm that the application is still running.
- **The application remains running:** this is expected while it
  waits for new file events.
- **A file remains in the inbox:** check the console for processing,
  permission or file-lock errors. An unchanged file may be skipped
  after an attempted processing failure.
