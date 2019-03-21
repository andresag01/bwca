/**
 * MIT License
 *
 * Copyright (c) 2019 Andres Amaya Garcia, Kyriakos Georgiou
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.bwca.utils;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

public class PlatformUtils
{
    public static ArrayList<String> runShell(String[] cmd, File outputFile)
        throws InterruptedException, IOException
    {
        ProcessBuilder procBuilder = new ProcessBuilder(cmd);
        procBuilder.redirectErrorStream(true);
        procBuilder.redirectOutput(outputFile);
        Process p = procBuilder.start();
        int exitCode = p.waitFor();

        if (exitCode != 0)
        {
            System.out.println("Subprocess terminated with error " + exitCode);
            System.out.println("Errors at " + outputFile.getAbsolutePath());
            System.exit(1);
        }

        // Read the output
        FileReader freader = new FileReader(outputFile);
        BufferedReader breader = new BufferedReader(freader);
        ArrayList<String> output = new ArrayList<String>();
        String line;
        while ((line = breader.readLine()) != null)
        {
            output.add(line);
        }

        return output;
    }

    public static void createOutputDirectory(String directory)
    {
        File dir = new File(directory);

        if (dir.isDirectory())
        {
            // The directory already exists, nothing to do
            return;
        }

        // Create the directory (and any parent directories that do not exist)
        if (!dir.mkdirs())
        {
            System.out.println("Could not create output directory " +
                               directory);
            System.exit(1);
        }
    }
}
