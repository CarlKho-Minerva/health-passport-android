Here’s a single Markdown doc you can drop into your repo (e.g., ‎⁠docs/java-setup-m2.md⁠).# Java & Gradle Setup on M2 Air (health-passport-android)

This doc captures exactly how to fix the Java / `JAVA_HOME` hell

and get `./gradlew testDebugUnitTest --tests "com.nexa.demo.VaultPipelineTest"` working on an M2 Air.

Tested with:

- macOS: 15.7.3 (aarch64)

- JDK: Homebrew `openjdk@17` 17.0.18

- Gradle: 8.13

- Project: `health-passport-android`

---

## 1. Symptoms

What started all this:

```text

ERROR: JAVA_HOME is set to an invalid directory: /Library/Internet Plug-Ins/JavaAppletPlugin.plugin/Contents/Home

Please set the JAVA_HOME variable in your environment to match the

location of your Java installation.

Also:/usr/libexec/java_home -V

# -> The operation couldn’t be completed. Unable to locate a Java Runtime.

So:

- macOS thought Java was the ancient browser plugin.

- ‎⁠java_home⁠ couldn’t find any real JDK.

- Gradle refused to run tests.

2. Install OpenJDK 17 via Homebrew

We use Homebrew’s ‎⁠openjdk@17⁠ (not the old Java plugin).

From any terminal:brew install openjdk@17

Verify it exists:ls -d /opt/homebrew/Cellar/openjdk@17/*

# Example output:

# /opt/homebrew/Cellar/openjdk@17/17.0.18

Inside that folder:ls /opt/homebrew/Cellar/openjdk@17/17.0.18

# bin  include  libexec  share  ...

# IMPORTANT: java home is under libexec/openjdk.jdk/Contents/Home

So the JDK home is:/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home

(Replace ‎⁠17.0.18⁠ with whatever version you actually get in the future.)

3. Set JAVA_HOME (one‑off in a shell)

To prove the JDK works, set ‎⁠JAVA_HOME⁠ manually and check ‎⁠java -version⁠.export JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home"

echo $JAVA_HOME

"$JAVA_HOME/bin/java" -version

Expected:/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home

openjdk version "17.0.18" 2026-01-20

OpenJDK Runtime Environment Homebrew (build 17.0.18+0)

OpenJDK 64-Bit Server VM Homebrew (build 17.0.18+0, mixed mode, sharing)

If that works, you have a real JDK; ‎⁠java_home⁠ being confused is just cosmetic.

4. Point Gradle at Java 17 (project‑local)

‎⁠health-passport-android⁠ uses Gradle 8.13. We explicitly tell Gradle which JDK to use via ‎⁠gradle.properties⁠.

From the project root:cd /path/to/health-passport-android

ls

# Make sure you see a file named gradlew here

Create or edit ‎⁠gradle.properties⁠ in the project root (same folder as ‎⁠gradlew⁠):nano gradle.properties

Add this line (adjust path if your version differs):org.gradle.java.home=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home

Save in nano:

- ‎⁠Ctrl+O⁠, ‎⁠Enter⁠ (write file)

- ‎⁠Ctrl+X⁠ (exit)

Now verify Gradle:./gradlew --version

You want to see something like:Gradle 8.13

Launcher JVM:  17.0.18 (Homebrew 17.0.18+0)

Daemon JVM:    /opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home (from org.gradle.java.home)

OS:            Mac OS X 15.7.3 aarch64

If that matches, Gradle is officially on Java 17.

5. Run the Health Passport tests

From the same project root (‎⁠health-passport-android⁠):./gradlew testDebugUnitTest --tests "com.nexa.demo.VaultPipelineTest"

What this test suite covers:

- ‎⁠"Amoxicillin prescription"⁠ → routes to ‎⁠Active_Medications.md⁠ + timeline

- ‎⁠"lab results"⁠ → routes to ‎⁠00_Lab_Baselines.md⁠

- Multi‑category ‎⁠"medication,visit"⁠ → routes to both files

- ‎⁠"grocery list"⁠ → classified ‎⁠not_medical⁠, nothing saved

- Stage 4 append behavior: appends without overwriting existing content

- Full pipeline integration without LLM (uses entities as content)

On success you’ll see something like:BUILD SUCCESSFUL in 11s

41 actionable tasks: 2 executed, 39 up-to-date

6. Make JAVA_HOME universal on an M2 Air

To avoid re‑exporting ‎⁠JAVA_HOME⁠ every time you open a new terminal, set it in your shell configuration.

On modern macOS (default shell is ‎⁠zsh⁠):nano ~/.zshrc

Add this line at the bottom:export JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home"

Save and reload:source ~/.zshrc

echo $JAVA_HOME

"$JAVA_HOME/bin/java" -version

Now:

- Any new terminal will have ‎⁠JAVA_HOME⁠ set correctly.

- ‎⁠./gradlew --version⁠ and your tests should work without extra setup.

If you ever upgrade Java (e.g., ‎⁠openjdk@17/17.0.20⁠):

1. Update the path in ‎⁠~/.zshrc⁠.

2. Update the path in ‎⁠gradle.properties⁠.

3. Run ‎⁠source ~/.zshrc⁠ or open a new terminal.

7. Quick “am I in the right folder?” checklist

Most confusion came from being in the wrong directory (like the JDK folder) and running Gradle there.

Before running any Gradle commands, confirm:pwd

# ends with /health-passport-android

ls

# shows gradlew, app/, settings.gradle, etc.

./gradlew --version

# uses Java 17 as configured above

If the prompt shows something like:➜  17.0.18 git:(stable)

you’re in the JDK folder, not in your project. First:cd /path/to/health-passport-android

then run Gradle.

This should capture the whole saga and give you a repeatable “Java path ritual” for future you on the M2 Air.

