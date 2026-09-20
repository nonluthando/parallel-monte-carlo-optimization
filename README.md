# Parallel Monte Carlo Optimisation (Java)

## Overview

This project parallelises a Monte Carlo optimisation algorithm in Java using the Fork/Join framework. The goal was to evaluate how multi-core execution impacts performance, and to understand the real-world limits of parallel scalability.

The project focuses on parallel algorithm design, correctness validation, and performance benchmarking, with an emphasis on when parallelism meaningfully improves runtime and when it does not.


## Why This Project
	•	Demonstrates practical use of Java concurrency and parallelism
	•	Explores systems-level performance trade-offs
	•	Uses controlled benchmarks to analyse scalability and overhead
	•	Shows disciplined engineering practices: validation, repeatability, and measurement


## What I Built

### Parallel Implementation
	•	Converted a serial Monte Carlo optimisation algorithm into a Fork/Join-based parallel solution
	•	Applied divide-and-conquer recursion with a configurable sequential cutoff to balance task overhead and throughput
	•	Executed independent searches concurrently using shared-memory parallelism

### Orchestration & Timing
	•	Managed parallel execution using a ForkJoinPool
	•	Measured execution time consistently for fair serial vs parallel comparisons

### Validation & Testing
	•	Verified correctness by comparing serial and parallel outputs under identical inputs
	•	Repeated runs to confirm result consistency
	•	Validated optimisation behaviour using known test functions

Performance Benchmarking

## Benchmark Design
	•	Compared serial vs parallel runtime
	•	Varied grid size, search density, and CPU core count
	•	Used median execution time to reduce noise from JVM scheduling and background processes

## Key Findings
	•	Parallel execution provides clear speedup for large workloads
	•	Ideal speedup is constrained by sequential components and scheduling overhead
	•	Highlights real-world limits of parallel scalability

## Tech Stack
	•	Java
	•	Fork/Join Framework
	•	Shared-memory parallelism
	•	CLI-based execution
	•	Performance benchmarking

## Project Structure
	•	TerrainArea.java – Terrain representation with lazy evaluation
	•	Search.java – Serial searcher (used by the serial baseline)
	•	MonteCarloMinimization.java – Serial baseline implementation
	•	SearchParallel.java – Fork/Join parallel task
	•	MonteCarloMinimizationParallel.java – Parallel execution driver
	•	WebServer.java – Dependency-free HTTP server powering the web UI (see below)

## How to Run

Compile

make

Run (Parallel)

java -cp bin MonteCarloMini.MonteCarloMinimizationParallel <rows> <cols> <xmin> <xmax> <ymin> <ymax> <search_density>

Example

java -cp bin MonteCarloMini.MonteCarloMinimizationParallel 5000 5000 -2 2 -2 2 0.1

Run (Serial)

java -cp bin MonteCarloMini.MonteCarloMinimization <rows> <cols> <xmin> <xmax> <ymin> <ymax> <search_density>

## Web UI

A small browser UI lets you configure a run, execute the serial and/or parallel
algorithm, and see the terrain, search coverage and the resulting speedup
without touching the command line.

Start it with:

make web

Then open http://127.0.0.1:8080/ in a browser. It:
	•	Runs the serial baseline and the Fork/Join parallel version on the same random seed and lets you compare their timings and reported speedup
	•	Renders the underlying function as a heatmap and overlays which grid points the searches actually visited
	•	Marks the location of the global minimum that was found
	•	Lets you tune rows/columns, the (x, y) search area, search density and the parallel sequential-cutoff threshold

The server is built on the JDK's built-in `com.sun.net.httpserver` (no external
dependencies) and serves the static frontend from `web/`. It binds to
`127.0.0.1` by default; set `BIND_HOST` to change that, and pass a port as the
first argument (`java -cp bin MonteCarloMini.WebServer 9090`) or via `make web PORT=9090`.

## Author

Luthando Mbuyane
University of Cape Town

