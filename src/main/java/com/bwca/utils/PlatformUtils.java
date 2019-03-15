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
