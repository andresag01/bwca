#! /usr/bin/env python3

import sys
import argparse
import os
import re
import subprocess
import ast
import operator as op
import math

# Supported operators
operators = {
    ast.Add: op.add,
    ast.Sub: op.sub,
    ast.Mult: op.mul,
    ast.USub: op.neg,
}

def eval_expr(expr):
    return eval_(ast.parse(expr, mode='eval').body)

def eval_(node):
    if isinstance(node, ast.Num): # <number>
        return node.n
    elif isinstance(node, ast.BinOp): # <left> <operator> <right>
        return operators[type(node.op)](eval_(node.left), eval_(node.right))
    elif isinstance(node, ast.UnaryOp): # <operator> <operand> e.g., -1
        return operators[type(node.op)](eval_(node.operand))
    else:
        raise TypeError(node)

class Function:
    FUNC_WEIGHTS_PATTERN = re.compile(r'^(?P<funcName>[a-zA-Z_]\w*)\s+=\s+WEIGHT;$')
    LP_SOLVE_RES_PATTERN = re.compile(r'^Value of objective function:\s+(?P<cost>\d+(\.\d+)?)$')
    LP_MAX_PROBLEM_PATTERN = re.compile(r'max:\s+[a-zA-Z_]\w*\s+[a-zA-Z_]\w*' +
        r'(\s+\+\s+[a-zA-Z_]\w*\s+[a-zA-Z_]\w*|' +
        r'\s+-\s+[0-9]+(\.[0-9]+)?\s+[a-zA-Z_]\w*)*;')

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
            # Substitute the keyword WEIGHT in the lp file
            self.models[model_name] = pattern.sub(repl,
                self.models[model_name])
            # Substitute the occurrence of the function name in the lp file
            repl = str(func.results[model_name])
            cnt = self.models[model_name].count(func.name) - 1
            self.models[model_name] = self.models[model_name].replace(func.name,
                repl, cnt)

    def set_block_weights(self):
        for model_name in self.models.keys():
            # Match the max problem
            match = Function.LP_MAX_PROBLEM_PATTERN.search(
                self.models[model_name])
            if not match:
                print("Failed to match problem for " + self.name + " and " +
                    model_name)
                sys.exit(1)
            problem = self.models[model_name][match.start():match.end()]

            # Look up the constant names for every block weight
            index = 0
            while True:
                pattern = re.compile(model_name + r'_wb' + str(index))
                if pattern.search(problem):
                    index += 1
                    continue
                break

            for i in range(0, index):
                # Look Up the expression for each constant block weight
                pattern = re.compile(model_name + r'_wb' + str(i) +
                    r'\s+=\s+[0-9]+(\.[0-9]+)?(\s+[0-9]+(\.[0-9]+)?)?' +
                    r'(\s+[\+-]\s+[0-9]+(\.[0-9]+)?(\s+[0-9]+(\.[0-9]+)?)?)*;')
                match = pattern.search(self.models[model_name])
                if not match:
                    print("Failed to match block weight for " + self.name +
                        " and " + model_name)
                    sys.exit(1)
                weight = self.models[model_name][match.start():match.end()]
                weight = weight.replace("\n", " ")

                # Replace the space operator for multiplication (*)
                pattern = re.compile(r'(?P<lhs>[0-9]+(\.[0-9]+)?)\s+' +
                    r'(?P<rhs>[0-9]+(\.[0-9]+)?)')
                while True:
                    match = pattern.search(weight)
                    if not match:
                        break
                    weight = pattern.sub(match.group("lhs") + " * " +
                        match.group("rhs"), weight)

                # Remove the assignment operator and semicolon
                weight = weight.strip()
                weight = weight[weight.find("=") + 1:-1]
                weight = weight.strip()

                # Evaluate the expression
                const = eval_expr(weight)

                # Replace the block weight with the evaluated constant
                self.models[model_name] = self.models[model_name].replace(
                    model_name + "_wb" + str(i), str(const), 1)

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

        # Replace the block weight variables by constants
        self.set_block_weights()

        # Solve for this function
        for model_name, model in self.models.items():
            outfilename = os.path.join(self.out_dir_path, model_name + ".lp")
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

    # Create output directories
    if not os.path.isdir(outdir):
        os.mkdir(outdir)
    for key, func in funcs.items():
        path = os.path.join(outdir, key)
        if not os.path.isdir(path):
            os.mkdir(path)

    # Solve ILP for each of the functions
    visited_funcs = set([])
    funcs[entry_func].solve_ilp(visited_funcs)

    # Write global results to a file
    result = ""
    for key, func in sorted(funcs.items()):
        result += "Function {0}".format(key)
        if key == entry_func:
            result += " (entry)"
        result += ":\n"

        wcma = None
        wcet = None

        # Write the results for each of the models
        for model_name in sorted(func.results.keys()):
            model_result = func.results[model_name]
            result += "    - {0}: ".format(model_name)
            if model_result is None:
                result += "UNAVAILABLE"
            else:
                result += str(model_result)
            result += "\n"

            if model_name == "wcma" and model_result is not None:
                wcma = math.ceil(model_result)
            elif model_name == "wcet" and model_result is not None:
                wcet = math.ceil(model_result)

        # Write the GC interval for every function (even though this is only
        # meaningful for the entry)
        result += "    - I_GC: "
        if wcet is None or wcma is None:
            result += "UNAVAILABLE"
        else:
            if wcet < wcma:
                print("Inconsistency for {0}: wcet ({1}) < wcma ({2})".format(
                    key, wcet, wcma))
                sys.exit(1)
            result += "{0}".format(wcet - wcma)
        result += "\n"

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
