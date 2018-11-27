#! /usr/bin/env python3

import sys
import argparse
import os
import re
import subprocess

class Function:
    FUNC_WEIGHTS_PATTERN = re.compile(r'^(?P<funcName>[a-zA-Z_]\w*)\s+=\s+WEIGHT;$')
    LP_SOLVE_RES_PATTERN = re.compile(r'^Value of objective function:\s+(?P<cost>\d+(\.\d+)?)$')

    def __init__(self, name, in_dir_path, out_dir_path):
        self.name = name
        self.in_dir_path = in_dir_path
        self.out_dir_path = out_dir_path
        self.deps = []
        self.models = {
            "wcma": self.read_file(os.path.join(self.in_dir_path, "wcma.lp")),
            "wcet": self.read_file(os.path.join(self.in_dir_path, "wcet.lp")),
            "wca": self.read_file(os.path.join(self.in_dir_path, "wca.lp")),
        }
        self.results = {
            "wcma": None,
            "wcet": None,
            "wca": None,
        }

        # Check that at least one of the models is available
        for key, model in self.models.items():
            if model is not None:
                return

        print("No static analysis models available to solve!")
        sys.exit(1)

    def __str__(self):
        return "name:{0} deps:[{1}]".format(self.name,
            ", ".join([x.name for x in self.deps]))

    def read_file(self, filepath):
        if not os.path.isfile(filepath):
            # Not all the models might be available
            return None

        with open(filepath, "r") as f:
            return f.read()

    def write_file(self, filepath, data):
        with open(filepath, "w") as f:
            f.write(data)

    def create_dep_list(self, funcs):
        # Get a model that is available
        model = None
        for key, m in self.models.items():
            if m is not None:
                model = m
                break

        # Get a list of the functions that the lp file references
        for line in model.split("\n"):
            match = Function.FUNC_WEIGHTS_PATTERN.match(line)
            if not match:
                continue
            self.deps.append(funcs[match.group("funcName")])

    def check_recursion(self, visited_funcs):
        for func in self.deps:
            if func.name in visited_funcs:
                print("The program has recursion!")
                exit(1)
            visited_funcs.add(func.name)
            func.check_recursion(visited_funcs)
            visited_funcs.remove(func.name)

    def set_func_weight(self, func):
        # Replace the WEIGHT string for the actual value of each function
        pattern = re.compile(r'' + func.name + '\s+=\s+WEIGHT;')
        for model_name in self.models.keys():
            if self.models[model_name] is None:
                continue
            repl = "{0} = {1};".format(func.name, func.results[model_name])
            self.models[model_name] = pattern.sub(repl,
                self.models[model_name])

    def run_lp_solve(self, lp_filename, out_filename):
        # Run the lp_solve application and parse the result
        args = ["lp_solve", lp_filename]

        with open(out_filename, "w") as f:
            proc = subprocess.Popen(args, stdout=f, stderr=f)
            proc.communicate()
            if proc.returncode != 0:
                print("lp_solve call failed. Failure message at " +
                    out_filename)
                sys.exit(1)

        with open(out_filename, "r") as f:
            for line in f.readlines():
                match = Function.LP_SOLVE_RES_PATTERN.match(line)
                if not match:
                    continue
                return float(match.group("cost"))

        print("Could not match lp_solve result in " + out_filename)
        sys.exit(1)

    def solve_ilp(self, visited_funcs):
        if self.name in visited_funcs:
            # We have already processed this function
            return False

        visited_funcs.add(self.name)

        # Solve all the dependencies of this function
        for dep in self.deps:
            if dep.solve_ilp(visited_funcs):
                self.set_func_weight(dep)

        # Solve for this function
        for model_name, model in self.models.items():
            outfilename = os.path.join(self.out_dir_path, model_name + ".ilp")
            outlpsolve = os.path.join(self.out_dir_path, model_name + ".log")
            self.write_file(outfilename, model)
            self.results[model_name] = self.run_lp_solve(outfilename, outlpsolve)

        return True

def main(indir, entry_func, outdir):
    # Sanity check the input arguments
    if not os.path.isdir(indir):
        print("Input path '{0}' is not a valid directory".format(indir))
        sys.exit(1)

    # Each subdirectory in the input path is a different function to analyze.
    # Compile a list of these
    funcs = {}
    for item in os.listdir(indir):
        inpath = os.path.join(indir, item)
        outpath = os.path.join(outdir, item)
        if not os.path.isdir(inpath):
            continue

        # Load the function information
        funcs[item] = Function(item, inpath, outpath)

    # Build a dependency list for each function
    for name, func in funcs.items():
        func.create_dep_list(funcs)

    # Check for recursion
    visited_funcs = set([entry_func])
    funcs[entry_func].check_recursion(visited_funcs)
    if len(visited_funcs) != 1:
        # Should never happen!
        print("Failed to check for recursion!")
        sys.exit(1)

    # Solve ILP for each of the functions
    if os.path.isdir(outdir):
        print("Output directory exists, please delete it")
        sys.exit(1)
    # Create output directories
    os.mkdir(outdir)
    for key, func in funcs.items():
        os.mkdir(os.path.join(outdir, key))
    visited_funcs = set([])
    funcs[entry_func].solve_ilp(visited_funcs)

    # Write global results to a file
    result = ""
    for key, func in funcs.items():
        result +="Function {0}{1}:\n".format(key,
            " (entry)" if key == entry_func else "")
        for model_name, model_result in func.results.items():
            result += "    - {0}: {1}\n".format(model_name,
                "UNAVAILABLE" if model_result is None else model_result)

    print(result, end="")

    with open(os.path.join(outdir, "summary.txt"), "w") as f_summary:
        f_summary.write(result)

def parseCmdLineArguments(args):
    description = "Solve ILP problem for a set of functions."

    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("-d", "--func-directory", required=True, metavar="DIR",
        dest="func_dir", help="Directory that contains the raw .lp files.")
    parser.add_argument("-e", "--entry", required=True, metavar="FUNC",
        dest="entry_func", help="The name of the function that is the entry " +
        "point for the program")
    parser.add_argument("-o", "--out-directory", required=True, metavar="DIR",
        dest="out_dir", help="Directory that will contain the output .lp" +
        "files.")

    return parser.parse_args(args[1:])

if __name__ == "__main__":
    args = parseCmdLineArguments(sys.argv)
    main(args.func_dir, args.entry_func, args.out_dir)
