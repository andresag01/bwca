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
    FUNC_WEIGHTS_PATTERN = re.compile(r'^(?P<funcName>[a-zA-Z_]\w*)\s+=' +
        r'\s+WEIGHT;$')

    LP_MAX_PROBLEM_PATTERN = re.compile(r'max:\s+[a-zA-Z_]\w*\s+[a-zA-Z_]\w*' +
        r'(\s+\+\s+[a-zA-Z_]\w*\s+[a-zA-Z_]\w*|' +
        r'\s+-\s+[0-9]+(\.[0-9]+)?\s+[a-zA-Z_]\w*)*;')
    LP_LOCAL_PATTERN = re.compile(r'/\* Block weights \*/')
    LP_CONSTRAINTS_PATTERN = re.compile(r'/\* Output constraints \*/')
    LP_DECLARATIONS_PATTERN = re.compile(
        r'/\* Block variable declarations \*/')
    LP_BLOCK_WEIGHTS_PATTERN = re.compile(
        r'(?P<weightName>[a-zA-Z_]\w*_wb\d+)\s+=\s+' +
        r'(?P<weightVal>[\+-]?\s*[0-9]+(\.[0-9]+)?(\s+[0-9]+(\.[0-9]+)?)?' +
        r'(\s+[\+-]\s+[0-9]+(\.[0-9]+)?(\s+[0-9]+(\.[0-9]+)?)?)*);')
    LP_MULTIPLICATION_PATTERN = re.compile(r'(?P<lhs>[0-9]+(\.[0-9]+)?)\s+' +
        r'(?P<rhs>[0-9]+(\.[0-9]+)?)')

    LP_SOLVE_RES_PATTERN = re.compile(r'^Value of objective function:\s+' +
        r'(?P<cost>\d+(\.\d+)?)$')
    LP_SOLVE_COUNT_PATTERN = re.compile(
        r'^(?P<name>[be]\d+)\s+(?P<count>\d+)$')

    def __init__(self, name, in_dir_path, out_dir_path):
        self.name = name
        self.in_dir_path = in_dir_path
        self.out_dir_path = out_dir_path
        self.deps = set([])
        self.models = {
            "wcma": self.read_file(os.path.join(self.in_dir_path, "wcma.lp")),
            "wcet": self.read_file(os.path.join(self.in_dir_path, "wcet.lp")),
            "wca": self.read_file(os.path.join(self.in_dir_path, "wca.lp")),
        }
        self.models_combined = {
            "wcet_wcma": "/* ILP for function " + self.name + " */\n\n" +
                "min: {wcet_func}\n" +
                "     - {wcma_func};\n\n" +
                "/**** Constraints ****/\n" +
                "{global_constraints}\n\n" +
                "/**** WCET ****/\n" +
                "{wcet_local}\n\n" +
                "/**** WCMA ****/\n" +
                "{wcma_local}\n\n" +
                "/**** Declarations ****/\n" +
                "{declarations}"
        }
        self.results = {
            "wcma": None,
            "wcet": None,
            "wca": None,
        }
        self.results_combined = {
            "value": None,
            "counts": None,
        }

        # Check that all models are available
        for key, model in self.models.items():
            if model is None:
                print("Model for {0} is not available for function {1}".format(
                    key, self.name))
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
            self.deps.add(funcs[match.group("funcName")])

    def check_recursion(self, visited_funcs):
        for func in self.deps:
            if func.name in visited_funcs:
                print("The program has recursion!")
                exit(1)
            visited_funcs.add(func.name)
            func.check_recursion(visited_funcs)
            visited_funcs.remove(func.name)

    def solve_ilp(self, visited_funcs):
        if self.name in visited_funcs:
            # We have already processed this function
            return

        visited_funcs.add(self.name)

        # Solve all the dependencies of this function
        for dep in self.deps:
            dep.solve_ilp(visited_funcs)

        print("Processing " + self.name)

        ######################################################################

        ##########################
        # Replace function weights
        ##########################
        for dep in self.deps:
            self.models["wcet"] = self.substitute_func_weight(
                "wcet", self.models["wcet"], dep.name, dep.results["wcet"])
            self.models["wcma"] = self.substitute_func_weight(
                "wcma", self.models["wcma"], dep.name, dep.results["wcma"])
            self.models["wca"] = self.substitute_func_weight(
                "wca", self.models["wca"], dep.name, dep.results["wca"])

        ######################################################################

        #########################
        # Solve for WCET and WCMA
        #########################
        wcet_local = self.extract_local_info(self.models["wcet"])
        wcet_weights = self.extract_block_weights(self.models["wcet"])
        wcet_problem = self.extract_problem(
            self.models["wcet"])[4:-1].strip()
        wcet_problem = self.substitute_weights(wcet_problem, wcet_weights)
        wcet_problem = self.strip_prefix(wcet_problem, "wcet_")

        wcma_local = self.extract_local_info(self.models["wcma"])
        wcma_weights = self.extract_block_weights(self.models["wcma"])
        wcma_problem = self.extract_problem(
            self.models["wcma"])[4:-1].strip()
        wcma_problem = self.substitute_weights(wcma_problem, wcma_weights)
        wcma_problem = self.strip_prefix(wcma_problem, "wcma_")
        wcma_problem_cpy = wcma_problem
        wcma_problem = self.reverse_sign(wcma_problem)

        constraints = self.extract_constraints(self.models["wcma"])
        constraints = self.strip_prefix(constraints, "wcma_")

        declarations = self.extract_declarations(self.models["wcma"])
        declarations = self.strip_prefix(declarations, "wcma_")

        self.models_combined["wcet_wcma"] = \
            self.models_combined["wcet_wcma"].format(
                wcet_func=wcet_problem,
                wcma_func=wcma_problem,
                global_constraints=constraints,
                wcet_local=wcet_local,
                wcma_local=wcma_local,
                declarations=declarations)

        outfilename = os.path.join(self.out_dir_path, "wcet_wcma" + ".lp")
        self.write_file(outfilename, self.models_combined["wcet_wcma"])

        # Solve the combined file
        outlpsolve = os.path.join(self.out_dir_path, "wcet_wcma" + ".log")
        self.run_lp_solve(outfilename, outlpsolve)

        # Extract the results
        lpoutput = self.read_file(outlpsolve)
        self.results_combined["value"], self.results_combined["counts"] = \
            self.extract_result(lpoutput)

        ######################################################################

        ################
        # Solve for WCET
        ################
        wcet_problem = wcet_problem.replace("\n", "")
        wcet_problem = self.substitute_block_counts(wcet_problem)
        self.results["wcet"] = eval_expr(wcet_problem)
        outfilename = os.path.join(self.out_dir_path, "wcet" + ".lp")
        self.write_file(outfilename, self.models["wcet"])

        ################
        # Solve for WCMA
        ################
        wcma_problem = wcma_problem_cpy
        wcma_problem = wcma_problem.replace("\n", "")
        wcma_problem = self.substitute_block_counts(wcma_problem)
        self.results["wcma"] = eval_expr(wcma_problem)
        outfilename = os.path.join(self.out_dir_path, "wcma" + ".lp")
        self.write_file(outfilename, self.models["wcma"])

        ################
        # Solve for WCA
        ################
        wca_weights = self.extract_block_weights(self.models["wca"])
        self.models["wca"] = self.substitute_weights(self.models["wca"],
            wca_weights)

        outfilename = os.path.join(self.out_dir_path, "wca" + ".lp")
        self.write_file(outfilename, self.models["wca"])

        outlpsolve = os.path.join(self.out_dir_path, "wca" + ".log")
        self.run_lp_solve(outfilename, outlpsolve)
        lpoutput = self.read_file(outlpsolve)
        self.results["wca"] = self.extract_result_only(lpoutput)

        ######################################################################

    def extract_result_only(self, src):
        for line in src.split("\n"):
            match = Function.LP_SOLVE_RES_PATTERN.match(line)
            if match:
                return float(match.group("cost"))

        print("Result was not matched!")
        sys.exit(1)

    def extract_result(self, src):
        result = None
        counts = {}

        for line in src.split("\n"):
            match = Function.LP_SOLVE_RES_PATTERN.match(line)
            if match:
                if result is not None:
                    print("Result matches multiple times!")
                    sys.exit(1)
                result = float(match.group("cost"))

            match = Function.LP_SOLVE_COUNT_PATTERN.match(line)
            if match:
                name = match.group("name")
                count = match.group("count")
                if name in counts:
                    print("Result name more than once in log output")
                    sys.exit(1)
                counts[name] = int(count)

        if result is None or len(counts) < 1:
            print("Did not find solution in log file")
            sys.exit(1)
        return (result, counts)

    def extract_local_info(self, src):
        match = Function.LP_LOCAL_PATTERN.search(src)
        if not match:
            print("Failed to match local info")
            print(src)
            sys.exit(1)
        start = match.start()

        match = Function.LP_CONSTRAINTS_PATTERN.search(src)
        if not match:
            print("Failed to match start of constraints")
            print(src)
            sys.exit(1)
        end = match.start()

        return src[start:end].strip()

    def extract_problem(self, src):
        match = Function.LP_MAX_PROBLEM_PATTERN.search(src)
        if not match:
            print("Failed to match problem")
            print(src)
            sys.exit(1)
        return src[match.start():match.end()]

    def extract_constraints(self, src):
        match = Function.LP_CONSTRAINTS_PATTERN.search(src)
        if not match:
            print("Failed to match start of constraints")
            print(src)
            sys.exit(1)
        start = match.start()

        match = Function.LP_DECLARATIONS_PATTERN.search(src)
        if not match:
            print("Failed to match start of declarations")
            sys.exit(1)
        end = match.end()

        return src[start:end].strip()

    def extract_declarations(self, src):
        match = Function.LP_DECLARATIONS_PATTERN.search(src)
        if not match:
            print("Failed to match start of declarations")
            sys.exit(1)

        return src[match.start():].strip()

    def strip_prefix(self, src, pfix):
        return src.replace(pfix, "")

    def extract_block_weights(self, src):
        weights = {}
        search_offset = 0

        while search_offset < len(src):
            # Find the start position of the next weight
            match = Function.LP_BLOCK_WEIGHTS_PATTERN.search(
                src[search_offset:])
            if not match:
                # We are done, no more weights
                break

            # Increase the search offset
            search_offset += match.end()

            # Extract the weight we just found
            name = match.group("weightName")
            val = match.group("weightVal")

            # Replace the space operator for multiplication (*)
            while True:
                match = Function.LP_MULTIPLICATION_PATTERN.search(val)
                if not match:
                    break
                val = Function.LP_MULTIPLICATION_PATTERN.sub(match.group("lhs")
                    + " * " + match.group("rhs"), val)

            # Evaluate the expression
            weights[name] = eval_expr(val)

        if len(weights) == 0:
            print("Could not find any weights")
            sys.exit(1)

        return weights

    def substitute_func_weight(self, model_name, model, func_name,
        func_weight):
        # Replace the WEIGHT string for the actual value of each function
        pattern = re.compile(r'' + func_name + '\s+=\s+WEIGHT;')
        repl = "{0}_{1} = {2};".format(model_name, func_name, func_weight)
        model = pattern.sub(repl, model)

        # Substitute the occurrence of the function name in the lp file
        cnt = model.count(func_name) - 1
        return model.replace(func_name, str(func_weight), cnt)

    def substitute_weights(self, src, weights):
        for name in sorted(weights.keys(), key=len, reverse=True):
            val = weights[name]
            src = src.replace(name, str(val), 1)
        return src

    def substitute_block_counts(self, src):
        counts = self.results_combined["counts"]
        for name in sorted(counts.keys(), key=len, reverse=True):
            val = counts[name]
            src = src.replace(name, "* " + str(val), 1)
        return src

    def reverse_sign(self, src):
        src = src.replace("+", "%")
        src = src.replace("-", "+")
        src = src.replace("%", "-")
        return src

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

    if entry_func not in funcs:
        print("Entry point function " + entry_func + " is not available")
        sys.exit(1)

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

        # Write the results for each of the models
        for model_name in sorted(func.results.keys()):
            model_result = func.results[model_name]
            result += "    - {0}: ".format(model_name)
            if model_result is None:
                result += "UNAVAILABLE"
            else:
                result += str(model_result)
            result += "\n"

        # Write the GC interval for every function (even though this is only
        # meaningful for the entry)
        result += "    - I_GC: "
        if func.results_combined["value"] is None:
            result += "UNAVAILABLE"
        else:
            result += "{0}".format(func.results_combined["value"])
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
