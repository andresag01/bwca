package Driver;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

public class Controller
{
    static final String READELF = "arm-none-eabi-readelf";
    static final String OBJDUMP = "arm-none-eabi-objdump";

    static final String[] READELF_CMD = { READELF, "-s", };
    static final String[] OBJDUMP_CMD = { OBJDUMP, "-C", "-d", };

    public static void main(String[] args)
    {
        if (args.length != 1)
        {
            System.out.println("This program takes exactly one argument!");
            System.exit(0);
        }

        Controller controller = new Controller();
        controller.analyze(args[0]);
    }

    private ArrayList<String> runShell(String[] cmd)
		throws InterruptedException, IOException
    {
        Runtime rt = Runtime.getRuntime();
        Process proc = rt.exec(cmd);

        InputStream stdoutStream = proc.getInputStream();
        InputStreamReader stdoutReader = new InputStreamReader(stdoutStream);
        BufferedReader stdout = new BufferedReader(stdoutReader);

        InputStream stderrStream = proc.getErrorStream();
        InputStreamReader stderrReader = new InputStreamReader(stderrStream);
        BufferedReader stderr = new BufferedReader(stderrReader);

        // Wait for the subprocess to terminate
        int retStatus = proc.waitFor();

        // Read the errors
        String line = null;
        boolean failed = false;
        while ((line = stderr.readLine()) != null)
        {
            System.out.println(line);
            failed = true;
        }

        if (failed)
        {
            System.out.println("Subprocess terminated with errors");
            System.exit(1);
        }

        if (retStatus != 0)
        {
            System.out.println("Subprocess terminated with error " +
                retStatus);
            System.exit(1);
        }

        // Read the output
        ArrayList<String> output = new ArrayList<String>();
        while ((line = stdout.readLine()) != null)
        {
            output.add(line);
        }

        return output;
    }

	private ISAModule parseFunctions(ArrayList<String> readelf,
									 ArrayList<String> objdump)
	{
		ISAModule module = new ISAModule(".");

		module.parseFunctions(readelf, objdump);

		return module;
	}

    private void analyze(String filename)
    {
		ArrayList<String> objdump = null;
		ArrayList<String> readelf = null;

		try
		{
			System.out.println("Running objdump");
			String[] cmd = Arrays.copyOf(OBJDUMP_CMD, OBJDUMP_CMD.length + 1);
			cmd[cmd.length - 1] = filename;
        	objdump = runShell(cmd);

        	System.out.println("Running readelf");
			cmd = Arrays.copyOf(READELF_CMD, READELF_CMD.length + 1);
			cmd[cmd.length - 1] = filename;
        	readelf = runShell(cmd);
		}
		catch (IOException ioe)
		{
			ioe.printStackTrace();
			System.out.println(ioe);
			System.exit(1);
		}
		catch (InterruptedException ie)
		{
			ie.printStackTrace();
			System.out.println(ie);
			System.exit(1);
		}

        System.out.println("Generating CFG");
		ISAModule module = new ISAModule(".");
		module.parseFunctions(readelf, objdump);

		System.out.println("Analyzing CFG");
		module.analyzeCFG();

		System.out.println("Applying model");
		Model model = new WCETModel();
		module.applyModel(model);

		System.out.println("Writing Dot representation");
		module.writeDotRepresentation(model);

		System.out.println("Write ILP file");
		module.writeILP(model);
    }
}
