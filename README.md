# Ludo – Team Sloth

Educational Ludo game, Software Engineering course project (University of Haifa).
Built with Java 21 and JavaFX.

## Requirements
- Java 21 or newer
- Put the JAR in a folder whose path has English letters only (non-English folder names stop the game from starting on Windows)
- Macs: Apple Silicon only (Intel Macs are not supported in this version)

## Download
Get `ludo-0.1.0.jar` from the [Releases](../../releases) page.

## How to start
| System | How |
|---|---|
| Windows | Double-click the JAR |
| macOS | Double-click the JAR. The first time, macOS blocks it: open System Settings → Privacy & Security and click "Open Anyway" |
| Linux | Run `java -jar ludo-0.1.0.jar` in the JAR's folder |

`java -jar ludo-0.1.0.jar` works on every system.

## Game history
Finished games are saved to `Ludo Sloth/history.csv` in your home folder
(e.g. `C:\Users\<name>\Ludo Sloth\history.csv` on Windows, `~/Ludo Sloth/history.csv` on macOS and Linux).
The folder and file are created automatically.

## Building from source
```
./mvnw package       # builds target/ludo-0.1.0.jar
./mvnw test          # runs the unit tests
./mvnw javafx:run    # runs the game without building a JAR
```
