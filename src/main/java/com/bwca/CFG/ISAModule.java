package Driver;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class ISAModule
{
	static final Pattern SYM_TABLE_FUNC =
		Pattern.compile("^\\s+\\d+:" +
						"\\s+(?<address>[0-9a-fA-F]+)" +
						"\\s+(?<size>\\d+)" +
						"\\s+FUNC" +
						"\\s+(LOCAL|GLOBAL|WEAK)" +
						"\\s+(DEFAULT|PROTECTED|HIDDEN|INTERNAL)" +
						"\\s+(ABS|\\d+)" +
						"\\s+(?<name>.+)$");

	private Map<String, ISAFunction> funcMap;
    private String outputDir;

    public ISAModule(String outputDir)
    {
        this.funcMap = new HashMap<String, ISAFunction>();
        this.outputDir = outputDir;
    }

    public void parseFunctions(ArrayList<String> readelf,
                               ArrayList<String> objdump)
    {
		for (String line : readelf)
		{
			Matcher match = SYM_TABLE_FUNC.matcher(line);
			if (!match.matches())
			{
				// This is not an entry corresponding to a function symbol
				continue;
			}

			String name = match.group("name");
            long size = Long.parseLong(match.group("size"), 10);
            if (size == 0)
            {
                continue;
            }
			ISAFunction func = new ISAFunction(
				Long.parseLong(match.group("address"), 16),
				size,
				name);
			func.parseInstructions(objdump);

			addFunction(name, func);
		}
    }

    public void analyzeCFG()
    {
        for (Map.Entry<String, ISAFunction> entry : funcMap.entrySet())
        {
            entry.getValue().analyzeCFG();
        }
    }

	public void addFunction(String name, ISAFunction func)
    {
		funcMap.put(name, func);
	}

	public ISAFunction getFunction(String key)
    {
		return funcMap.get(key);
	}

    public void applyModel(Model model)
    {
        for (Map.Entry<String, ISAFunction> entry : funcMap.entrySet())
        {
            ISAFunction func = entry.getValue();
            func.applyModel(model);
        }
    }

    public void writeILP(Model model)
    {
        for (Map.Entry<String, ISAFunction> entry : funcMap.entrySet())
        {
            String name = entry.getKey();
            ISAFunction func = entry.getValue();
            func.writeILP(outputDir + "/" + name + ".ilp", model);
        }
    }

    public void writeDotRepresentation(Model model)
    {
        for (Map.Entry<String, ISAFunction> entry : funcMap.entrySet())
        {
            String name = entry.getKey();
            ISAFunction func = entry.getValue();
            func.writeDotFile(outputDir + "/" + name + ".dot", model);
        }
    }
}
