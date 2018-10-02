# Bristol Worst-Case Analysis Tool

This is a simple tool written in Java to statically analyze programs. It takes a binary file as an input, constructs a Control Flow Graph (CFG) and the applies one or more analysis models. Examples include:

* Worst-Case Execution Time (WCET)
* Worst-Case Allocations (WCA)
* Worst-Case Memory Access (WCMA)

The tool can also be easily expanded to apply other analysis models.

# Dependencies

The tool has the following dependencies:

* Java
* arm-none-eabi-gcc binutils (readelf and objdump)
* Gradle build system

# Usage

Run the following command to compile the tool:

```
./gradlew build
```

This will create a build directory in the project's root folder. The application can the be run by using:

```
java -cp <PROJECT_ROOT>/build/classes/java/main com.bwca.driver.Controller <OPTS>
```

For more information on the available command line arguments and options run the tool with -h.
