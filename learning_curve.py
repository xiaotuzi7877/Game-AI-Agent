#!/usr/bin/env python3
import argparse as ap
import matplotlib.pyplot as plt
import numpy as np
import os
import re
from typing import List, Tuple

LINE_PREAMBLE  = "[INFO] TrainerAgent.onGameEnd: After "
LINE_POSTAMBLE = "cycle(s), avg trajectory utility = "

def load(path: str) -> np.ndarray:
    data: List[Tuple[float, float]] = []
    with open(path, "r") as f:
        for line in f:
            line = line.strip()
            if LINE_PREAMBLE in line and LINE_POSTAMBLE in line:
                # strip off the prefix and suffix, leaving "N UUU.UUU"
                vals = line.replace(LINE_PREAMBLE, "") \
                           .replace(LINE_POSTAMBLE, "") \
                           .split()
                cycle_idx   = float(vals[0])
                avg_utility = float(vals[1])
                data.append((cycle_idx, avg_utility))

    arr = np.array(data, dtype=float)
    if arr.size == 0:
        raise RuntimeError(f"No matching lines in '{path}'. "
                           f"Make sure your log has “{LINE_PREAMBLE}…{LINE_POSTAMBLE}…”")
    # reshape into (n_cycles, 2)
    return arr.reshape(-1, 2)

def main() -> None:
    parser = ap.ArgumentParser(
        description="Plot avg trajectory utility vs cycle from a tetris log.")
    parser.add_argument("logfile", type=str,
                        help="path to logfile containing TrainerAgent.onGameEnd lines")
    args = parser.parse_args()

    if not os.path.exists(args.logfile):
        parser.error(f"logfile '{args.logfile}' not found")

    data = load(args.logfile)
    cycles, utils = data[:,0], data[:,1]

    plt.plot(cycles, utils, marker="o", linestyle="-")
    plt.xlabel("Cycle")
    plt.ylabel("Average Trajectory Utility")
    plt.title("TetrisQAgent Learning Curve")
    plt.grid(True)
    plt.tight_layout()
    plt.show()

if __name__ == "__main__":
    main()
